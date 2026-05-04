package com.example.welfare.collect.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class CollectSourceExecutionService {

    private final ApiSyncLogService apiSyncLogService;
    private final BokjiroDetailCollectService bokjiroDetailCollectService;
    private final Map<CollectSource, CollectSourceAdapter> adapters;

    public CollectSourceExecutionService(List<CollectSourceAdapter> adapters,
                                         ApiSyncLogService apiSyncLogService,
                                         BokjiroDetailCollectService bokjiroDetailCollectService) {
        this.apiSyncLogService = apiSyncLogService;
        this.bokjiroDetailCollectService = bokjiroDetailCollectService;
        this.adapters = buildAdapterMap(adapters);
    }

    public CollectResult collectSource(CollectSource source) {
        return apiSyncLogService.runWithLog(source.jobName(), adapter(source)::collect);
    }

    public BokjiroDetailCollectService.GapFillResult collectBokjiroDetailGapFill(int rounds, int maxCallsPerRound) {
        AtomicReference<BokjiroDetailCollectService.GapFillResult> resultRef = new AtomicReference<>();
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
        });
        return resultRef.get();
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
