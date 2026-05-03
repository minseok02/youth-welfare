package com.example.welfare.collect.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CollectService {

    private final CollectExecutionGuard collectExecutionGuard;
    private final ApiSyncLogService apiSyncLogService;
    private final BokjiroDetailCollectService bokjiroDetailCollectService;
    private final Map<CollectSource, CollectSourceAdapter> adapters;

    public CollectService(List<CollectSourceAdapter> adapters,
                          CollectExecutionGuard collectExecutionGuard,
                          ApiSyncLogService apiSyncLogService,
                          BokjiroDetailCollectService bokjiroDetailCollectService) {
        this.collectExecutionGuard = collectExecutionGuard;
        this.apiSyncLogService = apiSyncLogService;
        this.bokjiroDetailCollectService = bokjiroDetailCollectService;
        this.adapters = buildAdapterMap(adapters);
    }

    /**
     * 매일 새벽 2시 수집 배치
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    public void collectAll() {
        collectExecutionGuard.runExclusive("collect-all", () -> {
            log.info("[CollectService] 공공API 수집 시작");
            CollectSource.executionOrder().forEach(this::runSourceSafely);
            log.info("[CollectService] 공공API 수집 완료");
        });
    }

    public void collect(CollectSource source) {
        collectExecutionGuard.runExclusive(source.lockName(), () -> runSource(source));
    }

    public BokjiroDetailCollectService.GapFillResult collectBokjiroDetailGapFill(int rounds, int maxCallsPerRound) {
        AtomicReference<BokjiroDetailCollectService.GapFillResult> resultRef = new AtomicReference<>();
        collectExecutionGuard.runExclusive(CollectSource.BOKJIRO_DETAIL_GAP_FILL.lockName(), () ->
                apiSyncLogService.runWithLog(CollectSource.BOKJIRO_DETAIL_GAP_FILL.jobName(), () -> {
                    BokjiroDetailCollectService.GapFillResult result =
                            bokjiroDetailCollectService.collectBokjiroDetailGapFillResult(rounds, maxCallsPerRound);
                    resultRef.set(result);
                    return CollectResult.of(
                            result.requestedCount(),
                            result.savedCount(),
                            result.skippedCount(),
                            0,
                            result.failedCount()
                    );
                }));
        return resultRef.get();
    }

    private void runSourceSafely(CollectSource source) {
        try {
            runSource(source);
        } catch (Exception e) {
            log.warn("[CollectService][{}] 수집 실패 - 다음 source 계속 진행: {}", source.jobName(), e.getMessage(), e);
        }
    }

    private CollectResult runSource(CollectSource source) {
        return apiSyncLogService.runWithLog(source.jobName(), adapter(source)::collect);
    }

    private CollectSourceAdapter adapter(CollectSource source) {
        CollectSourceAdapter adapter = adapters.get(source);
        if (adapter == null) {
            throw new IllegalStateException("수집 adapter가 없습니다. source=" + source);
        }
        return adapter;
    }

    private Map<CollectSource, CollectSourceAdapter> buildAdapterMap(List<CollectSourceAdapter> adapterList) {
        EnumMap<CollectSource, CollectSourceAdapter> adapterMap = new EnumMap<>(CollectSource.class);
        for (CollectSourceAdapter adapter : adapterList) {
            CollectSource source = adapter.source();
            CollectSourceAdapter existing = adapterMap.putIfAbsent(source, adapter);
            if (existing != null) {
                throw new IllegalStateException("중복 수집 adapter가 등록되었습니다. source=" + source);
            }
        }
        for (CollectSource source : CollectSource.values()) {
            if (!source.requiresAdapter()) {
                continue;
            }
            if (!adapterMap.containsKey(source)) {
                throw new IllegalStateException("필수 수집 adapter가 없습니다. source=" + source);
            }
        }
        return Map.copyOf(adapterMap);
    }
}
