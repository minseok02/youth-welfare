package com.example.welfare.collect.service;

import com.example.welfare.collect.gateway.BokjiroDetailClient;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.collect.repository.BokjiroDetailCommandRepository;
import com.example.welfare.collect.repository.BokjiroDetailReadRepository;
import com.example.welfare.collect.support.CollectSourceRegistry;
import com.example.welfare.collect.validation.RawFieldValidator;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.entity.WelfareService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final BokjiroDetailCommandRepository bokjiroDetailCommandRepository;
    private final BokjiroDetailClient detailClient;
    private final RawApiPayloadService rawApiPayloadService;
    private final WelfareServiceMapper welfareServiceMapper;
    private final CollectPolicyAggregateApplyService collectPolicyAggregateApplyService;
    private final Map<WelfareService.SourceType, DetailCollectCapability> detailCapabilities;
    private final BokjiroDetailBudgetAllocator budgetAllocator;

    public BokjiroDetailCollectService(BokjiroDetailReadRepository bokjiroDetailReadRepository,
                                       BokjiroDetailCommandRepository bokjiroDetailCommandRepository,
                                       BokjiroDetailClient detailClient,
                                       RawApiPayloadService rawApiPayloadService,
                                       WelfareServiceMapper welfareServiceMapper,
                                       CollectPolicyAggregateApplyService collectPolicyAggregateApplyService) {
        this.bokjiroDetailReadRepository = bokjiroDetailReadRepository;
        this.bokjiroDetailCommandRepository = bokjiroDetailCommandRepository;
        this.detailClient = detailClient;
        this.rawApiPayloadService = rawApiPayloadService;
        this.welfareServiceMapper = welfareServiceMapper;
        this.collectPolicyAggregateApplyService = collectPolicyAggregateApplyService;
        this.detailCapabilities = buildDetailCapabilities(detailClient, welfareServiceMapper);
        this.budgetAllocator = new BokjiroDetailBudgetAllocator();
    }

    @Value("${collect.detail.max-calls-per-run:900}")
    private int maxCallsPerRun;
    @Value("${collect.detail.max-calls-per-api-per-run:95}")
    private int maxCallsPerApiPerRun;
    @Value("${collect.detail.request-interval-ms:1000}")
    private long requestIntervalMs;
    @Value("${collect.detail.retry.max-attempts:3}")
    private int retryMaxAttempts;
    @Value("${collect.detail.retry.base-backoff-ms:1500}")
    private long retryBaseBackoffMs;
    @Value("${collect.detail.max-consecutive-rate-limit-hits:5}")
    private int maxConsecutiveRateLimitHits;

    @Transactional
    public int collectBokjiroDetails() {
        return collectBokjiroDetailsResult().savedCount();
    }

    @Transactional
    public int collectBokjiroDetails(int maxCalls) {
        return collectBokjiroDetailsResult(maxCalls).savedCount();
    }

    @Transactional
    public CollectResult collectBokjiroDetailsResult() {
        return collectBokjiroDetailsRun(maxCallsPerRun, false).collectResult();
    }

    @Transactional
    public CollectResult collectBokjiroDetailsResult(int maxCalls) {
        return collectBokjiroDetailsRun(maxCalls, false).collectResult();
    }

    @Transactional
    public int collectBokjiroDetailsRefresh() {
        return collectBokjiroDetailsRefreshResult().savedCount();
    }

    @Transactional
    public int collectBokjiroDetailsRefresh(int maxCalls) {
        return collectBokjiroDetailsRefreshResult(maxCalls).savedCount();
    }

    @Transactional
    public CollectResult collectBokjiroDetailsRefreshResult() {
        return collectBokjiroDetailsRun(maxCallsPerRun, true).collectResult();
    }

    @Transactional
    public CollectResult collectBokjiroDetailsRefreshResult(int maxCalls) {
        return collectBokjiroDetailsRun(maxCalls, true).collectResult();
    }

    @Transactional
    public GapFillResult collectBokjiroDetailGapFillResult(int rounds, int maxCallsPerRound) {
        int requested = 0;
        int saved = 0;
        int skipped = 0;
        int failed = 0;
        int roundsExecuted = 0;
        boolean stoppedAfterNoSaves = false;

        for (int round = 1; round <= rounds; round++) {
            DetailCollectRunResult roundRun = collectBokjiroDetailsRun(maxCallsPerRound, false);
            CollectResult roundResult = roundRun.collectResult();
            roundsExecuted++;
            requested += roundResult.requestedCount();
            saved += roundResult.savedCount();
            skipped += roundResult.skippedCount();
            failed += roundResult.failedCount();

            if (roundRun.rateLimitedAbort() && roundResult.savedCount() <= 0) {
                log.warn("[BokjiroDetailCollectService] gap fill 수집 실패 roundsExecuted={} requested={} saved={} skipped={} failed={} rateLimitedAbort=true",
                        roundsExecuted, requested, saved, skipped, failed);
                throw new CustomException(ErrorCode.COLLECT_API_FAILED);
            }

            if (roundResult.savedCount() <= 0) {
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

    @Transactional
    private DetailCollectRunResult collectBokjiroDetailsRun(int maxCalls, boolean refreshExisting) {
        Map<WelfareService.SourceType, List<WelfareService>> targetsBySource = loadTargetsBySource();
        BokjiroDetailBudgetAllocator.BudgetAllocation budgetAllocation = budgetAllocator.allocate(
                maxCalls,
                maxCallsPerApiPerRun,
                List.copyOf(detailCapabilities.keySet()),
                targetsBySource
        );
        Map<WelfareService.SourceType, CollectStats> statsBySource = new LinkedHashMap<>();

        for (Map.Entry<WelfareService.SourceType, DetailCollectCapability> entry : detailCapabilities.entrySet()) {
            WelfareService.SourceType sourceType = entry.getKey();
            CollectStats stats = collectBySource(
                    entry.getValue(),
                    targetsBySource.getOrDefault(sourceType, List.of()),
                    budgetAllocation.budgetFor(sourceType),
                    refreshExisting
            );
            statsBySource.put(sourceType, stats);
        }

        int calls = statsBySource.values().stream().mapToInt(CollectStats::calls).sum();
        int saved = statsBySource.values().stream().mapToInt(CollectStats::saved).sum();
        int skipped = statsBySource.values().stream().mapToInt(CollectStats::skipped).sum();
        int failed = statsBySource.values().stream().mapToInt(CollectStats::failed).sum();
        boolean rateLimitedAbort = statsBySource.values().stream().anyMatch(CollectStats::rateLimitedAbort);

        log.info("[BokjiroDetailCollectService] 상세 수집 완료 refreshExisting={} calls={} saved={} skipped={} failed={} maxCalls={} sourceCalls={}",
                refreshExisting, calls, saved, skipped, failed, maxCalls, summarizeStats(statsBySource));
        String metadataJson = buildMetadataJson(maxCalls, refreshExisting, budgetAllocation, statsBySource, rateLimitedAbort);
        return new DetailCollectRunResult(
                CollectResult.withMetadata(calls, saved, skipped, 0, failed, metadataJson),
                rateLimitedAbort
        );
    }

    private CollectStats collectBySource(DetailCollectCapability capability,
                                         List<WelfareService> targets,
                                         int callBudget,
                                         boolean refreshExisting) {
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

        for (WelfareService service : targets) {
            if (calls >= callBudget) break;
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

            rawApiPayloadService.saveBokjiroDetail(service.getSourceType(), service.getSourceId(), payload);

            try {
                NormalizedPolicyAggregate aggregate = capability.toAggregate(service, payload);
                collectPolicyAggregateApplyService.applyCollectedDetail(
                        service,
                        bokjiroDetailReadRepository.findDetailByServiceId(service.getId()).orElse(null),
                        aggregate
                );
                saved++;
            } catch (Exception e) {
                log.warn("[BokjiroDetailCollectService] 상세 저장 실패 serviceId={} sourceType={} refreshExisting={} err={}",
                        service.getId(), service.getSourceType(), refreshExisting, e.getMessage());
                failed++;
            }
        }

        log.info("[BokjiroDetailCollectService] sourceType={} refreshExisting={} 상세 수집 완료 calls={} saved={} skipped={} failed={} budget={}",
                sourceType, refreshExisting, calls, saved, skipped, failed, callBudget);
        return new CollectStats(calls, saved, skipped, failed, rateLimitedAbort);
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

    private String buildMetadataJson(int maxCalls,
                                     boolean refreshExisting,
                                     BokjiroDetailBudgetAllocator.BudgetAllocation budgetAllocation,
                                     Map<WelfareService.SourceType, CollectStats> statsBySource,
                                     boolean rateLimitedAbort) {
        return """
                {"maxCalls":%d,"refreshExisting":%s,"rateLimitedAbort":%s,"sourceBudgets":{%s},"sourceCalls":{%s}}
                """.formatted(
                maxCalls,
                refreshExisting,
                rateLimitedAbort,
                budgetAllocation.toJsonObject(),
                statsBySource.entrySet().stream()
                        .map(entry -> "\"%s\":%d".formatted(entry.getKey().name(), entry.getValue().calls()))
                        .collect(Collectors.joining(","))
        ).trim();
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

    private record CollectStats(int calls, int saved, int skipped, int failed, boolean rateLimitedAbort) {
        private static CollectStats empty() {
            return new CollectStats(0, 0, 0, 0, false);
        }
    }

    private record DetailCollectRunResult(CollectResult collectResult, boolean rateLimitedAbort) {
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
