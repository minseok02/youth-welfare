package com.example.welfare.collect.service;

import com.example.welfare.policy.service.PolicyEmbeddingRefreshRequestService;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class CollectSourceExecutionService {

    private final ApiSyncLogService apiSyncLogService;
    private final BokjiroDetailCollectService bokjiroDetailCollectService;
    private final Gov24DetailCollectService gov24DetailCollectService;
    private final Gov24SupportConditionsCollectService gov24SupportConditionsCollectService;
    private final PolicyEmbeddingRefreshRequestService policyEmbeddingRefreshRequestService;
    private final Map<CollectSource, CollectSourceAdapter> adapters;

    public CollectSourceExecutionService(List<CollectSourceAdapter> adapters,
                                         ApiSyncLogService apiSyncLogService,
                                         BokjiroDetailCollectService bokjiroDetailCollectService,
                                         Gov24DetailCollectService gov24DetailCollectService,
                                         Gov24SupportConditionsCollectService gov24SupportConditionsCollectService,
                                         PolicyEmbeddingRefreshRequestService policyEmbeddingRefreshRequestService) {
        this.apiSyncLogService = apiSyncLogService;
        this.bokjiroDetailCollectService = bokjiroDetailCollectService;
        this.gov24DetailCollectService = gov24DetailCollectService;
        this.gov24SupportConditionsCollectService = gov24SupportConditionsCollectService;
        this.policyEmbeddingRefreshRequestService = policyEmbeddingRefreshRequestService;
        this.adapters = buildAdapterMap(adapters);
    }

    public CollectResult collectSource(CollectSource source) {
        return apiSyncLogService.runWithLog(
                source.jobName(),
                () -> policyEmbeddingRefreshRequestService.runInBatch(adapter(source)::collect)
        );
    }

    public BokjiroDetailCollectService.GapFillResult collectBokjiroDetailGapFill(int rounds, int maxCallsPerRound) {
        AtomicReference<BokjiroDetailCollectService.GapFillResult> resultRef = new AtomicReference<>();
        apiSyncLogService.runWithLog(CollectSource.BOKJIRO_DETAIL_GAP_FILL.jobName(), () -> {
            BokjiroDetailCollectService.GapFillResult result = policyEmbeddingRefreshRequestService.runInBatch(
                    () -> bokjiroDetailCollectService.collectBokjiroDetailGapFillResult(rounds, maxCallsPerRound)
            );
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

    public CollectResult collectGov24Details(int maxCallsPerRun) {
        AtomicReference<CollectResult> resultRef = new AtomicReference<>();
        apiSyncLogService.runWithLog(CollectSource.GOV24_DETAIL.jobName(), () -> {
            CollectResult result = policyEmbeddingRefreshRequestService.runInBatch(
                    () -> gov24DetailCollectService.collectGov24Details(maxCallsPerRun)
            );
            resultRef.set(result);
            return result;
        });
        return resultRef.get();
    }

    public CollectResult collectGov24DetailsForSourceId(String sourceId) {
        AtomicReference<CollectResult> resultRef = new AtomicReference<>();
        apiSyncLogService.runWithLog(CollectSource.GOV24_DETAIL.jobName(), () -> {
            CollectResult result = policyEmbeddingRefreshRequestService.runInBatch(
                    () -> gov24DetailCollectService.collectGov24DetailsForSourceId(sourceId)
            );
            resultRef.set(result);
            return result;
        });
        return resultRef.get();
    }

    public CollectResult collectGov24SupportConditions(int maxCallsPerRun) {
        AtomicReference<CollectResult> resultRef = new AtomicReference<>();
        apiSyncLogService.runWithLog(CollectSource.GOV24_SUPPORT_CONDITIONS.jobName(), () -> {
            CollectResult result = policyEmbeddingRefreshRequestService.runInBatch(
                    () -> gov24SupportConditionsCollectService.collectGov24SupportConditions(maxCallsPerRun)
            );
            resultRef.set(result);
            return result;
        });
        return resultRef.get();
    }

    public CollectResult collectGov24SupportConditionsForSourceId(String sourceId) {
        AtomicReference<CollectResult> resultRef = new AtomicReference<>();
        apiSyncLogService.runWithLog(CollectSource.GOV24_SUPPORT_CONDITIONS.jobName(), () -> {
            CollectResult result = policyEmbeddingRefreshRequestService.runInBatch(
                    () -> gov24SupportConditionsCollectService.collectGov24SupportConditionsForSourceId(sourceId)
            );
            resultRef.set(result);
            return result;
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
