package com.example.welfare.collect.service;

import com.example.welfare.collect.gateway.BokjiroDetailClient;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.collect.repository.BokjiroDetailReadRepository;
import com.example.welfare.collect.support.CollectSourceRegistry;
import com.example.welfare.collect.validation.RawFieldValidator;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 복지로 상세 정보 수집.
 * 트래픽 제한을 고려해 1회 실행당 호출 상한을 둔다.
 */
@Slf4j
@Service
public class BokjiroDetailCollectService {

    private final BokjiroDetailReadRepository bokjiroDetailReadRepository;
    private final BokjiroDetailClient detailClient;
    private final RawApiPayloadService rawApiPayloadService;
    private final WelfareServiceMapper welfareServiceMapper;
    private final CollectPolicyAggregateApplyService collectPolicyAggregateApplyService;
    private final WelfareServiceRepository welfareServiceRepository;
    private final Map<WelfareService.SourceType, DetailCollectCapability> detailCapabilities;

    public BokjiroDetailCollectService(BokjiroDetailReadRepository bokjiroDetailReadRepository,
                                       BokjiroDetailClient detailClient,
                                       RawApiPayloadService rawApiPayloadService,
                                       WelfareServiceMapper welfareServiceMapper,
                                       CollectPolicyAggregateApplyService collectPolicyAggregateApplyService,
                                       WelfareServiceRepository welfareServiceRepository) {
        this.bokjiroDetailReadRepository = bokjiroDetailReadRepository;
        this.detailClient = detailClient;
        this.rawApiPayloadService = rawApiPayloadService;
        this.welfareServiceMapper = welfareServiceMapper;
        this.collectPolicyAggregateApplyService = collectPolicyAggregateApplyService;
        this.welfareServiceRepository = welfareServiceRepository;
        this.detailCapabilities = buildDetailCapabilities(detailClient, welfareServiceMapper);
    }

    @Value("${collect.detail.central.max-calls-per-run:10000}")
    private int centralMaxCallsPerRun;
    @Value("${collect.detail.local.max-calls-per-run:10000}")
    private int localMaxCallsPerRun;
    @Value("${collect.detail.request-interval-ms:1000}")
    private long requestIntervalMs;
    @Value("${collect.detail.retry.max-attempts:3}")
    private int retryMaxAttempts;
    @Value("${collect.detail.retry.base-backoff-ms:1500}")
    private long retryBaseBackoffMs;
    @Value("${collect.detail.max-consecutive-rate-limit-hits:5}")
    private int maxConsecutiveRateLimitHits;

    public int collectBokjiroDetails() {
        return collectBokjiroDetailsResult().savedCount();
    }

    public int collectBokjiroDetails(int maxCalls) {
        return collectBokjiroDetailsResult(maxCalls).savedCount();
    }

    public CollectResult collectBokjiroDetailsResult() {
        return collectBokjiroDetailsRun(configuredSourceBudgets(), false).collectResult();
    }

    public CollectResult collectBokjiroDetailsResult(int maxCalls) {
        return collectBokjiroDetailsRun(uniformSourceBudgets(maxCalls), false).collectResult();
    }

    public int collectBokjiroDetailsRefresh() {
        return collectBokjiroDetailsRefreshResult().savedCount();
    }

    public int collectBokjiroDetailsRefresh(int maxCalls) {
        return collectBokjiroDetailsRefreshResult(maxCalls).savedCount();
    }

    public CollectResult collectBokjiroDetailsRefreshResult() {
        return collectBokjiroDetailsRun(configuredSourceBudgets(), true).collectResult();
    }

    public CollectResult collectBokjiroDetailsRefreshResult(int maxCalls) {
        return collectBokjiroDetailsRun(uniformSourceBudgets(maxCalls), true).collectResult();
    }

    public CollectResult collectBokjiroDetailsForSourceId(String sourceId) {
        return collectBokjiroDetailsForSourceId(sourceId, false);
    }

    public CollectResult collectBokjiroDetailsRefreshForSourceId(String sourceId) {
        return collectBokjiroDetailsForSourceId(sourceId, true);
    }

    public GapFillResult collectBokjiroDetailGapFillResult(int rounds, int maxCallsPerRound) {
        int requested = 0;
        int saved = 0;
        int skipped = 0;
        int failed = 0;
        int roundsExecuted = 0;
        boolean stoppedAfterNoSaves = false;
        java.util.Set<Long> excludedFailedServiceIds = new java.util.HashSet<>();

        for (int round = 1; round <= rounds; round++) {
            DetailCollectRunResult roundRun = collectBokjiroDetailsRun(
                    uniformSourceBudgets(maxCallsPerRound),
                    false,
                    excludedFailedServiceIds
            );
            CollectResult roundResult = roundRun.collectResult();
            roundsExecuted++;
            requested += roundResult.requestedCount();
            saved += roundResult.savedCount();
            skipped += roundResult.skippedCount();
            failed += roundResult.failedCount();
            excludedFailedServiceIds.addAll(roundRun.failedServiceIds());

            if (roundRun.rateLimitedAbort() && roundResult.savedCount() <= 0) {
                log.warn("[BokjiroDetailCollectService] gap fill 수집 실패 roundsExecuted={} requested={} saved={} skipped={} failed={} rateLimitedAbort=true",
                        roundsExecuted, requested, saved, skipped, failed);
                throw new CustomException(ErrorCode.COLLECT_API_FAILED);
            }

            if (roundResult.savedCount() <= 0 && roundResult.failedCount() <= 0) {
                stoppedAfterNoSaves = true;
                break;
            }
        }

        return new GapFillResult(
                rounds,
                roundsExecuted,
                maxCallsPerRound,
                requested,
                saved,
                skipped,
                failed,
                stoppedAfterNoSaves
        );
    }

    private DetailCollectRunResult collectBokjiroDetailsRun(Map<WelfareService.SourceType, Integer> sourceBudgets,
                                                            boolean refreshExisting) {
        return collectBokjiroDetailsRun(sourceBudgets, refreshExisting, java.util.Set.of());
    }

    private DetailCollectRunResult collectBokjiroDetailsRun(Map<WelfareService.SourceType, Integer> sourceBudgets,
                                                            boolean refreshExisting,
                                                            java.util.Set<Long> excludedServiceIds) {
        Map<WelfareService.SourceType, List<WelfareService>> targetsBySource = loadTargetsBySource();
        Map<WelfareService.SourceType, CollectStats> statsBySource = new LinkedHashMap<>();

        for (Map.Entry<WelfareService.SourceType, DetailCollectCapability> entry : detailCapabilities.entrySet()) {
            WelfareService.SourceType sourceType = entry.getKey();
            CollectStats stats = collectBySource(
                    entry.getValue(),
                    targetsBySource.getOrDefault(sourceType, List.of()),
                    sourceBudgets.getOrDefault(sourceType, 0),
                    refreshExisting,
                    excludedServiceIds
            );
            statsBySource.put(sourceType, stats);
        }

        int calls = statsBySource.values().stream().mapToInt(CollectStats::calls).sum();
        int saved = statsBySource.values().stream().mapToInt(CollectStats::saved).sum();
        int skipped = statsBySource.values().stream().mapToInt(CollectStats::skipped).sum();
        int failed = statsBySource.values().stream().mapToInt(CollectStats::failed).sum();
        boolean rateLimitedAbort = statsBySource.values().stream().anyMatch(CollectStats::rateLimitedAbort);
        List<Long> failedServiceIds = statsBySource.values().stream()
                .flatMap(stats -> stats.failedServiceIds().stream())
                .toList();

        log.info("[BokjiroDetailCollectService] 상세 수집 완료 refreshExisting={} calls={} saved={} skipped={} failed={} sourceBudgets={} sourceCalls={}",
                refreshExisting, calls, saved, skipped, failed, summarizeBudgets(sourceBudgets), summarizeStats(statsBySource));
        String metadataJson = buildMetadataJson(sourceBudgets, refreshExisting, statsBySource, rateLimitedAbort);
        return new DetailCollectRunResult(
                CollectResult.withMetadata(calls, saved, skipped, 0, failed, metadataJson),
                rateLimitedAbort,
                failedServiceIds
        );
    }

    private CollectResult collectBokjiroDetailsForSourceId(String sourceId, boolean refreshExisting) {
        WelfareService service = findBokjiroServiceBySourceId(sourceId);
        if (service == null) {
            return CollectResult.of(0, 0, 1, 0, 0);
        }

        CollectStats stats = collectBySource(
                detailCapabilities.get(service.getSourceType()),
                List.of(service),
                1,
                refreshExisting,
                java.util.Set.of()
        );
        return CollectResult.of(stats.calls(), stats.saved(), stats.skipped(), 0, stats.failed());
    }

    private CollectStats collectBySource(DetailCollectCapability capability,
                                         List<WelfareService> targets,
                                         int callBudget,
                                         boolean refreshExisting,
                                         java.util.Set<Long> excludedServiceIds) {
        if (callBudget <= 0) {
            return CollectStats.empty();
        }

        WelfareService.SourceType sourceType = capability.sourceType();
        int calls = 0;
        int saved = 0;
        int skipped = 0;
        int failed = 0;
        int rateLimitHits = 0;
        boolean rateLimitedAbort = false;
        List<Long> failedServiceIds = new ArrayList<>();

        for (WelfareService service : targets) {
            if (calls >= callBudget) break;
            if (excludedServiceIds.contains(service.getId())) {
                continue;
            }
            if (!refreshExisting && bokjiroDetailReadRepository.existsDetailByServiceId(service.getId())) {
                skipped++;
                continue;
            }

            sleepQuietly(requestIntervalMs);

            FetchOutcome outcome = fetchWithRetry(service);
            calls += outcome.requestCount();
            BokjiroDetailClient.FetchResult fetchResult = outcome.result();

            if (fetchResult == null) {
                failed++;
                failedServiceIds.add(service.getId());
                continue;
            }
            if (fetchResult.isRateLimited()) {
                rateLimitHits++;
                if (rateLimitHits >= maxConsecutiveRateLimitHits) {
                    log.warn("[BokjiroDetailCollectService] 연속 429 발생으로 sourceType={} refreshExisting={} 수집 중단 calls={} rateLimitHits={}",
                            sourceType, refreshExisting, calls, rateLimitHits);
                    rateLimitedAbort = true;
                    break;
                }
                continue;
            }
            rateLimitHits = 0;

            BokjiroDetailClient.DetailPayload payload = fetchResult.getPayload();
            if (payload == null || payload.isEmpty()) {
                continue;
            }

            boolean rawSaved = rawApiPayloadService.saveBokjiroDetail(service.getSourceType(), service.getSourceId(), payload);
            if (!rawSaved) {
                failed++;
                failedServiceIds.add(service.getId());
                log.warn("[BokjiroDetailCollectService] raw detail 저장 실패 serviceId={} sourceType={} refreshExisting={}",
                        service.getId(), service.getSourceType(), refreshExisting);
                continue;
            }

            try {
                NormalizedPolicyAggregate aggregate = capability.toAggregate(service, payload);
                collectPolicyAggregateApplyService.applyCollectedDetail(
                        service,
                        bokjiroDetailReadRepository.findDetailByServiceId(service.getId()).orElse(null),
                        aggregate
                );
                saved++;
            } catch (Exception e) {
                log.warn("[BokjiroDetailCollectService] 상세 저장 실패 serviceId={} sourceType={} refreshExisting={} errorType={}",
                        service.getId(), service.getSourceType(), refreshExisting, e.getClass().getSimpleName());
                failed++;
                failedServiceIds.add(service.getId());
            }
        }

        log.info("[BokjiroDetailCollectService] sourceType={} refreshExisting={} 상세 수집 완료 calls={} saved={} skipped={} failed={} budget={}",
                sourceType, refreshExisting, calls, saved, skipped, failed, callBudget);
        return new CollectStats(calls, saved, skipped, failed, rateLimitedAbort, failedServiceIds);
    }

    private Map<WelfareService.SourceType, List<WelfareService>> loadTargetsBySource() {
        Map<WelfareService.SourceType, List<WelfareService>> targetsBySource = new LinkedHashMap<>();
        for (WelfareService.SourceType sourceType : detailCapabilities.keySet()) {
            targetsBySource.put(
                    sourceType,
                    new ArrayList<>(bokjiroDetailReadRepository.findTargetsBySourceType(sourceType))
            );
        }
        return targetsBySource;
    }

    private WelfareService findBokjiroServiceBySourceId(String sourceId) {
        WelfareService local = welfareServiceRepository
                .findBySourceTypeAndSourceId(WelfareService.SourceType.BOKJIRO_LOCAL, sourceId)
                .orElse(null);
        if (local != null) {
            return local;
        }
        return welfareServiceRepository
                .findBySourceTypeAndSourceId(WelfareService.SourceType.BOKJIRO_CENTRAL, sourceId)
                .orElse(null);
    }

    private Map<WelfareService.SourceType, Integer> configuredSourceBudgets() {
        EnumMap<WelfareService.SourceType, Integer> budgets = new EnumMap<>(WelfareService.SourceType.class);
        budgets.put(WelfareService.SourceType.BOKJIRO_CENTRAL, centralMaxCallsPerRun);
        budgets.put(WelfareService.SourceType.BOKJIRO_LOCAL, localMaxCallsPerRun);
        return budgets;
    }

    private Map<WelfareService.SourceType, Integer> uniformSourceBudgets(int maxCallsPerSource) {
        EnumMap<WelfareService.SourceType, Integer> budgets = new EnumMap<>(WelfareService.SourceType.class);
        for (WelfareService.SourceType sourceType : detailCapabilities.keySet()) {
            budgets.put(sourceType, maxCallsPerSource);
        }
        return budgets;
    }

    private FetchOutcome fetchWithRetry(WelfareService service) {
        DetailCollectCapability capability = detailCapabilities.get(service.getSourceType());
        if (capability == null) {
            return new FetchOutcome(BokjiroDetailClient.FetchResult.failure(false, false, null), 0);
        }

        BokjiroDetailClient.FetchResult result = null;
        int requestCount = 0;
        for (int attempt = 1; attempt <= retryMaxAttempts; attempt++) {
            requestCount++;
            result = capability.fetchWithStatus(service.getSourceId());

            if (!result.isRetryable()) {
                return new FetchOutcome(result, requestCount);
            }
            if (attempt >= retryMaxAttempts) {
                return new FetchOutcome(result, requestCount);
            }

            long waitMs = retryBaseBackoffMs * attempt + ThreadLocalRandom.current().nextLong(100, 500);
            log.info("[BokjiroDetailCollectService] 재시도 대기 serviceId={} sourceType={} attempt={}/{} waitMs={} status={}",
                    service.getId(), service.getSourceType(), attempt, retryMaxAttempts, waitMs, result.getStatusCode());
            sleepQuietly(waitMs);
        }
        return new FetchOutcome(result, requestCount);
    }

    private void sleepQuietly(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String buildMetadataJson(Map<WelfareService.SourceType, Integer> sourceBudgets,
                                     boolean refreshExisting,
                                     Map<WelfareService.SourceType, CollectStats> statsBySource,
                                     boolean rateLimitedAbort) {
        return """
                {"refreshExisting":%s,"rateLimitedAbort":%s,"sourceBudgets":{%s},"sourceCalls":{%s}}
                """.formatted(
                refreshExisting,
                rateLimitedAbort,
                sourceBudgets.entrySet().stream()
                        .map(entry -> "\"%s\":%d".formatted(entry.getKey().name(), entry.getValue()))
                        .collect(Collectors.joining(",")),
                statsBySource.entrySet().stream()
                        .map(entry -> "\"%s\":%d".formatted(entry.getKey().name(), entry.getValue().calls()))
                        .collect(Collectors.joining(","))
        ).trim();
    }

    private String summarizeBudgets(Map<WelfareService.SourceType, Integer> sourceBudgets) {
        return sourceBudgets.entrySet().stream()
                .map(entry -> "%s=%d".formatted(entry.getKey().name(), entry.getValue()))
                .collect(Collectors.joining(","));
    }

    private String summarizeStats(Map<WelfareService.SourceType, CollectStats> statsBySource) {
        return statsBySource.entrySet().stream()
                .map(entry -> "%s=%d".formatted(entry.getKey().name(), entry.getValue().calls()))
                .collect(Collectors.joining(","));
    }

    private Map<WelfareService.SourceType, DetailCollectCapability> buildDetailCapabilities(BokjiroDetailClient detailClient,
                                                                                            WelfareServiceMapper welfareServiceMapper) {
        EnumMap<WelfareService.SourceType, DetailCollectCapability> capabilities = new EnumMap<>(WelfareService.SourceType.class);
        for (CollectSourceRegistry binding : CollectSourceRegistry.detailSources()) {
            capabilities.put(
                    binding.sourceType(),
                    new DetailCollectCapability(
                            binding.sourceType(),
                            sourceId -> binding.fetchDetail(detailClient, sourceId),
                            (service, payload) -> binding.toDetailAggregate(welfareServiceMapper, service, payload)
                    )
            );
        }
        return Map.copyOf(capabilities);
    }

    private record CollectStats(int calls,
                                int saved,
                                int skipped,
                                int failed,
                                boolean rateLimitedAbort,
                                List<Long> failedServiceIds) {
        private static CollectStats empty() {
            return new CollectStats(0, 0, 0, 0, false, List.of());
        }
    }

    private record DetailCollectRunResult(CollectResult collectResult,
                                          boolean rateLimitedAbort,
                                          List<Long> failedServiceIds) {
    }

    private record FetchOutcome(BokjiroDetailClient.FetchResult result, int requestCount) {
    }

    private record DetailCollectCapability(
            WelfareService.SourceType sourceType,
            DetailFetchFunction fetchFunction,
            DetailAggregateFunction aggregateFunction
    ) {
        private BokjiroDetailClient.FetchResult fetchWithStatus(String sourceId) {
            return fetchFunction.fetch(sourceId);
        }

        private NormalizedPolicyAggregate toAggregate(WelfareService service,
                                                      BokjiroDetailClient.DetailPayload payload) {
            return aggregateFunction.toAggregate(service, payload);
        }
    }

    @FunctionalInterface
    private interface DetailFetchFunction {
        BokjiroDetailClient.FetchResult fetch(String sourceId);
    }

    @FunctionalInterface
    private interface DetailAggregateFunction {
        NormalizedPolicyAggregate toAggregate(WelfareService service, BokjiroDetailClient.DetailPayload payload);
    }

    public record GapFillResult(
            int roundsRequested,
            int roundsExecuted,
            int maxCallsPerRound,
            int requestedCount,
            int savedCount,
            int skippedCount,
            int failedCount,
            boolean stoppedAfterNoSaves
    ) {
    }
}
