package com.example.welfare.collect.normalization;

import com.example.welfare.collect.entity.RawApiPayload;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.collect.repository.NormalizedPolicySidecarBackfillReadRepository;
import com.example.welfare.collect.repository.RawApiPayloadReadRepository;
import com.example.welfare.collect.support.CollectSourceRegistry;
import com.example.welfare.policy.entity.WelfareService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class NormalizedPolicySidecarBackfillService {

    private final RawApiPayloadReadRepository rawApiPayloadReadRepository;
    private final NormalizedPolicySidecarBackfillReadRepository normalizedPolicySidecarBackfillReadRepository;
    private final WelfareServiceMapper welfareServiceMapper;
    private final NormalizedPolicySidecarWriter normalizedPolicySidecarWriter;
    private final ObjectMapper objectMapper;
    private final Map<WelfareService.SourceType, SidecarBackfillCapability> backfillCapabilities;

    public NormalizedPolicySidecarBackfillService(RawApiPayloadReadRepository rawApiPayloadReadRepository,
                                                  NormalizedPolicySidecarBackfillReadRepository normalizedPolicySidecarBackfillReadRepository,
                                                  WelfareServiceMapper welfareServiceMapper,
                                                  NormalizedPolicySidecarWriter normalizedPolicySidecarWriter,
                                                  ObjectMapper objectMapper) {
        this.rawApiPayloadReadRepository = rawApiPayloadReadRepository;
        this.normalizedPolicySidecarBackfillReadRepository = normalizedPolicySidecarBackfillReadRepository;
        this.welfareServiceMapper = welfareServiceMapper;
        this.normalizedPolicySidecarWriter = normalizedPolicySidecarWriter;
        this.objectMapper = objectMapper;
        this.backfillCapabilities = buildBackfillCapabilities(welfareServiceMapper, objectMapper);
    }

    @Transactional
    public BackfillResult backfillBokjiroListSidecars(int limitPerSource) {
        return backfillListSidecars(configuredSourceTypes(SidecarBackfillCapability::supportsList), limitPerSource);
    }

    @Transactional
    public BackfillResult backfillBokjiroDetailSidecars(int limitPerSource) {
        return backfillDetailSidecars(configuredSourceTypes(SidecarBackfillCapability::supportsDetail), limitPerSource);
    }

    @Transactional
    public BackfillResult backfillListSidecars(List<WelfareService.SourceType> sourceTypes, int limitPerSource) {
        BackfillResult result = BackfillResult.empty();
        for (WelfareService.SourceType sourceType : sourceTypes) {
            result = result.plus(backfillListSource(sourceType, limitPerSource));
        }
        return result;
    }

    @Transactional
    public BackfillResult backfillDetailSidecars(List<WelfareService.SourceType> sourceTypes, int limitPerSource) {
        BackfillResult result = BackfillResult.empty();
        for (WelfareService.SourceType sourceType : sourceTypes) {
            result = result.plus(backfillDetailSource(sourceType, limitPerSource));
        }
        return result;
    }

    private BackfillResult backfillListSource(WelfareService.SourceType sourceType, int limitPerSource) {
        SidecarBackfillCapability capability = backfillCapabilities.get(sourceType);
        if (capability == null || !capability.supportsList()) {
            return BackfillResult.empty();
        }

        List<RawApiPayload> payloads = rawApiPayloadReadRepository
                .findAllBySourceTypeAndApiCategoryOrderByFetchedAtAsc(sourceType, RawApiPayload.ApiCategory.LIST);

        int scanned = 0;
        int upserted = 0;
        int missingService = 0;
        int failed = 0;

        for (RawApiPayload raw : limit(payloads, limitPerSource)) {
            scanned++;
            WelfareService service = normalizedPolicySidecarBackfillReadRepository
                    .findServiceBySourceTypeAndSourceId(raw.getSourceType(), raw.getSourceId())
                    .orElse(null);
            if (service == null) {
                missingService++;
                continue;
            }

            try {
                NormalizedPolicyAggregate aggregate = capability.toListAggregate(raw);
                normalizedPolicySidecarWriter.upsert(service, aggregate);
                upserted++;
            } catch (Exception e) {
                failed++;
                log.warn("[NormalizedPolicySidecarBackfillService] list backfill 실패 sourceType={} sourceId={} err={}",
                        raw.getSourceType(), raw.getSourceId(), e.getMessage());
            }
        }

        return new BackfillResult(scanned, upserted, missingService, failed);
    }

    private BackfillResult backfillDetailSource(WelfareService.SourceType sourceType, int limitPerSource) {
        SidecarBackfillCapability capability = backfillCapabilities.get(sourceType);
        if (capability == null || !capability.supportsDetail()) {
            return BackfillResult.empty();
        }

        List<RawApiPayload> payloads = rawApiPayloadReadRepository
                .findAllBySourceTypeAndApiCategoryOrderByFetchedAtAsc(sourceType, RawApiPayload.ApiCategory.DETAIL);

        int scanned = 0;
        int upserted = 0;
        int missingService = 0;
        int failed = 0;

        for (RawApiPayload raw : limit(payloads, limitPerSource)) {
            scanned++;
            WelfareService service = normalizedPolicySidecarBackfillReadRepository
                    .findServiceBySourceTypeAndSourceId(raw.getSourceType(), raw.getSourceId())
                    .orElse(null);
            if (service == null) {
                missingService++;
                continue;
            }

            try {
                NormalizedPolicyAggregate aggregate = capability.toDetailAggregate(service, raw);
                normalizedPolicySidecarWriter.upsert(service, aggregate);
                upserted++;
            } catch (Exception e) {
                failed++;
                log.warn("[NormalizedPolicySidecarBackfillService] detail backfill 실패 sourceType={} sourceId={} err={}",
                        raw.getSourceType(), raw.getSourceId(), e.getMessage());
            }
        }

        return new BackfillResult(scanned, upserted, missingService, failed);
    }

    private List<RawApiPayload> limit(List<RawApiPayload> payloads, int limitPerSource) {
        if (limitPerSource <= 0 || payloads.size() <= limitPerSource) {
            return payloads;
        }
        return payloads.subList(0, limitPerSource);
    }

    private List<WelfareService.SourceType> configuredSourceTypes(java.util.function.Predicate<SidecarBackfillCapability> predicate) {
        return backfillCapabilities.values().stream()
                .filter(predicate)
                .map(SidecarBackfillCapability::sourceType)
                .toList();
    }

    private Map<WelfareService.SourceType, SidecarBackfillCapability> buildBackfillCapabilities(WelfareServiceMapper welfareServiceMapper,
                                                                                                 ObjectMapper objectMapper) {
        EnumMap<WelfareService.SourceType, SidecarBackfillCapability> capabilities =
                new EnumMap<>(WelfareService.SourceType.class);
        for (CollectSourceRegistry binding : CollectSourceRegistry.detailSources()) {
            capabilities.put(
                    binding.sourceType(),
                    new SidecarBackfillCapability(
                            binding.sourceType(),
                            raw -> binding.toListAggregate(welfareServiceMapper, objectMapper, raw),
                            (service, raw) -> binding.toDetailAggregate(welfareServiceMapper, service, raw, objectMapper)
                    )
            );
        }

        return Map.copyOf(capabilities);
    }

    public record BackfillResult(int scannedCount, int upsertedCount, int missingServiceCount, int failedCount) {
        private static BackfillResult empty() {
            return new BackfillResult(0, 0, 0, 0);
        }

        public BackfillResult plus(BackfillResult other) {
            return new BackfillResult(
                    scannedCount + other.scannedCount,
                    upsertedCount + other.upsertedCount,
                    missingServiceCount + other.missingServiceCount,
                    failedCount + other.failedCount
            );
        }
    }

    private record SidecarBackfillCapability(
            WelfareService.SourceType sourceType,
            ListAggregateLoader listLoader,
            DetailAggregateLoader detailLoader
    ) {
        private boolean supportsList() {
            return listLoader != null;
        }

        private boolean supportsDetail() {
            return detailLoader != null;
        }

        private NormalizedPolicyAggregate toListAggregate(RawApiPayload raw) throws Exception {
            return listLoader.load(raw);
        }

        private NormalizedPolicyAggregate toDetailAggregate(WelfareService service, RawApiPayload raw) throws Exception {
            return detailLoader.load(service, raw);
        }
    }

    @FunctionalInterface
    private interface ListAggregateLoader {
        NormalizedPolicyAggregate load(RawApiPayload raw) throws Exception;
    }

    @FunctionalInterface
    private interface DetailAggregateLoader {
        NormalizedPolicyAggregate load(WelfareService service, RawApiPayload raw) throws Exception;
    }
}
