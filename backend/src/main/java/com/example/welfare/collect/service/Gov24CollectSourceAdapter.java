package com.example.welfare.collect.service;

import com.example.welfare.collect.dto.Gov24ServiceListDto;
import com.example.welfare.collect.gateway.Gov24Client;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.collect.support.CollectSourceRegistry;
import com.example.welfare.collect.support.ListCollectSourceBinding;
import com.example.welfare.collect.validation.FieldQualityStats;
import com.example.welfare.collect.validation.RawFieldValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class Gov24CollectSourceAdapter extends AbstractListCollectSourceAdapter<Gov24ServiceListDto.Item> {

    private final Gov24Client gov24Client;
    private final WelfareServiceMapper welfareServiceMapper;
    private final CollectItemSaver saver;
    private final RawApiPayloadService rawApiPayloadService;
    private final CollectListResponsePolicy collectListResponsePolicy;
    private final Gov24StaleServiceCleanupService gov24StaleServiceCleanupService;
    private final ObjectMapper objectMapper;

    @Value("${gov24.collect.chunk-size:500}")
    private int chunkSize;

    @Value("${gov24.collect.chunk-pause-ms:100}")
    private long chunkPauseMs;

    private ListCollectSourceBinding<Gov24ServiceListDto.Item> binding() {
        return CollectSourceRegistry.GOV24.listBinding(welfareServiceMapper);
    }

    @Override
    public CollectSource source() {
        return CollectSource.GOV24;
    }

    @Override
    public CollectResult collect() {
        int effectiveChunkSize = Math.max(1, chunkSize);
        int effectiveMaxItemsPerRun = gov24Client.maxItemsPerRun() > 0
                ? gov24Client.maxItemsPerRun()
                : Integer.MAX_VALUE;
        int requested = 0;
        int saved = 0;
        int skipped = 0;
        int filteredOut = 0;
        int failed = 0;
        int chunkIndex = 0;
        Integer totalCount = null;
        long startedAt = System.currentTimeMillis();
        Set<String> liveSourceIds = new LinkedHashSet<>();
        Gov24StaleServiceCleanupService.CleanupResult cleanupResult = null;

        try {
            for (int page = 1; ; page++) {
                Gov24Client.PageChunk chunk = gov24Client.fetchChunk(page, effectiveChunkSize);
                List<Gov24ServiceListDto.Item> items = chunk.items();
                if (chunkIndex == 0) {
                    collectListResponsePolicy.ensureNonEmpty(source(), items);
                }
                if (items == null || items.isEmpty()) {
                    break;
                }

                chunkIndex++;
                if (totalCount == null) {
                    totalCount = chunk.totalCount();
                }

                FieldQualityStats stats = new FieldQualityStats(source().jobName(), items.size());
                binding().recordStats(items, stats);
                log.info("[Gov24CollectSourceAdapter] chunk={} page={} perPage={} currentCount={} totalCount={} stats={}",
                        chunkIndex, chunk.page(), chunk.perPage(), chunk.currentCount(), totalCount, stats.summary());

                for (Gov24ServiceListDto.Item item : items) {
                    requested++;
                    String serviceId = itemId(item);
                    if (serviceId != null && !serviceId.isBlank()) {
                        liveSourceIds.add(serviceId.trim());
                    }
                    try {
                        if (!rawApiPayloadService.saveList(binding(), item)) {
                            failed++;
                            log.warn("[CollectSourceAdapter][{}] raw 저장 실패 {}={}",
                                    source().jobName(), failureIdLabel(), serviceId);
                            continue;
                        }
                        if (!RawFieldValidator.isValidGov24(item)) {
                            skipped++;
                            continue;
                        }
                        saver.save(binding(), item);
                        saved++;
                    } catch (Exception e) {
                        failed++;
                        log.warn("[CollectSourceAdapter][{}] 저장 실패 {}={} chunk={} errorType={}",
                                source().jobName(), failureIdLabel(), serviceId, chunkIndex, e.getClass().getSimpleName());
                    }
                }

                long elapsedMs = System.currentTimeMillis() - startedAt;
                log.info("[Gov24CollectSourceAdapter] chunk={} processed={} total={} saved={} skipped={} filtered={} failed={} elapsedMs={}",
                        chunkIndex, requested, totalCount, saved, skipped, filteredOut, failed, elapsedMs);

                boolean reachedApiEnd = chunk.currentCount() < effectiveChunkSize;
                boolean reachedTotalCount = totalCount != null && requested >= totalCount;
                boolean reachedMaxItemsPerRun = requested >= effectiveMaxItemsPerRun;
                if (reachedApiEnd || reachedTotalCount || reachedMaxItemsPerRun) {
                    if (reachedMaxItemsPerRun) {
                        log.warn("[Gov24CollectSourceAdapter] max-items-per-run reached processed={} maxItemsPerRun={}",
                                requested, effectiveMaxItemsPerRun);
                    }
                    break;
                }

                pauseBetweenChunksIfNeeded();
            }
        } catch (Exception e) {
            log.error("[Gov24CollectSourceAdapter] chunk collect failed chunk={} processed={} saved={} skipped={} filtered={} failed={} errorType={}",
                    chunkIndex + 1, requested, saved, skipped, filteredOut, failed, e.getClass().getSimpleName());
            throw e;
        }

        try {
            cleanupResult = gov24StaleServiceCleanupService.cleanupAgainstLiveInventory(List.copyOf(liveSourceIds));
            if (cleanupResult.skipped()) {
                log.warn("[Gov24CollectSourceAdapter] stale cleanup skipped reason={} existing={} live={} stale={}",
                        cleanupResult.reason(),
                        cleanupResult.existingCount(),
                        cleanupResult.liveCount(),
                        cleanupResult.staleCount());
            } else {
                log.info("[Gov24CollectSourceAdapter] stale cleanup deleted={} stale={} existing={} live={} sample={}",
                        cleanupResult.deletedCount(),
                        cleanupResult.staleCount(),
                        cleanupResult.existingCount(),
                        cleanupResult.liveCount(),
                        cleanupResult.staleSourceIds().stream().limit(10).toList());
            }
        } catch (Exception e) {
            log.warn("[Gov24CollectSourceAdapter] stale cleanup failed errorType={}", e.getClass().getSimpleName());
        }

        long elapsedMs = System.currentTimeMillis() - startedAt;
        log.info("[CollectSourceAdapter][{}] 저장 완료 requested={} saved={} skipped={} filtered={} failed={} chunkCount={} elapsedMs={}",
                source().jobName(), requested, saved, skipped, filteredOut, failed, chunkIndex, elapsedMs);
        return CollectResult.withMetadata(
                requested,
                saved,
                skipped,
                filteredOut,
                failed,
                buildMetadataJson(effectiveChunkSize, chunkIndex, elapsedMs, totalCount, cleanupResult)
        );
    }

    @Override
    protected List<Gov24ServiceListDto.Item> fetchItems() {
        throw new UnsupportedOperationException("Gov24 collect uses chunked collect()");
    }

    @Override
    protected void recordStats(List<Gov24ServiceListDto.Item> items, FieldQualityStats stats) {
        throw new UnsupportedOperationException("Gov24 collect uses chunked collect()");
    }

    @Override
    protected boolean saveRawPayload(Gov24ServiceListDto.Item item) {
        throw new UnsupportedOperationException("Gov24 collect uses chunked collect()");
    }

    @Override
    protected boolean isValid(Gov24ServiceListDto.Item item) {
        throw new UnsupportedOperationException("Gov24 collect uses chunked collect()");
    }

    @Override
    protected void saveItem(Gov24ServiceListDto.Item item) {
        throw new UnsupportedOperationException("Gov24 collect uses chunked collect()");
    }

    @Override
    protected String itemId(Gov24ServiceListDto.Item item) {
        return item.getServiceId();
    }

    @Override
    protected String failureIdLabel() {
        return "serviceId";
    }

    private String buildMetadataJson(int effectiveChunkSize,
                                     int chunkCount,
                                     long elapsedMs,
                                     Integer totalCount,
                                     Gov24StaleServiceCleanupService.CleanupResult cleanupResult) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("chunkSize", effectiveChunkSize);
        metadata.put("chunkPauseMs", chunkPauseMs);
        metadata.put("chunkCount", chunkCount);
        metadata.put("elapsedMs", elapsedMs);
        metadata.put("totalCount", totalCount);
        if (cleanupResult != null) {
            metadata.put("staleCleanupStatus", cleanupResult.skipped() ? cleanupResult.reason() : cleanupResult.reason());
            metadata.put("staleDeletedCount", cleanupResult.deletedCount());
            metadata.put("staleCandidateCount", cleanupResult.staleCount());
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            log.warn("[Gov24CollectSourceAdapter] metadata json build failed errorType={}", e.getClass().getSimpleName());
            return null;
        }
    }

    private void pauseBetweenChunksIfNeeded() {
        if (chunkPauseMs <= 0) {
            return;
        }
        try {
            Thread.sleep(chunkPauseMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gov24 chunk pause interrupted", e);
        }
    }
}
