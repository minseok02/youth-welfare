package com.example.welfare.collect.service;

import com.example.welfare.collect.gateway.BokjiroDetailClient;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.collect.normalization.NormalizedPolicySidecarWriter;
import com.example.welfare.collect.validation.RawFieldValidator;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.entity.WelfareServiceDetail;
import com.example.welfare.policy.repository.ServiceTagRepository;
import com.example.welfare.policy.repository.WelfareServiceDetailRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.policy.service.SearchYouthRelevanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 복지로 상세 정보 수집.
 * 트래픽 제한을 고려해 1회 실행당 호출 상한을 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BokjiroDetailCollectService {

    private final WelfareServiceRepository welfareServiceRepository;
    private final WelfareServiceDetailRepository detailRepository;
    private final ServiceTagRepository serviceTagRepository;
    private final BokjiroDetailClient detailClient;
    private final RawApiPayloadService rawApiPayloadService;
    private final SearchYouthRelevanceService searchYouthRelevanceService;
    private final WelfareServiceMapper welfareServiceMapper;
    private final NormalizedPolicySidecarWriter normalizedPolicySidecarWriter;

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
        return collectBokjiroDetailsResult(maxCallsPerRun, false);
    }

    @Transactional
    public CollectResult collectBokjiroDetailsResult(int maxCalls) {
        return collectBokjiroDetailsResult(maxCalls, false);
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
        return collectBokjiroDetailsResult(maxCallsPerRun, true);
    }

    @Transactional
    public CollectResult collectBokjiroDetailsRefreshResult(int maxCalls) {
        return collectBokjiroDetailsResult(maxCalls, true);
    }

    @Transactional
    public CollectResult collectBokjiroDetailsResult(int maxCalls, boolean refreshExisting) {
        List<WelfareService> centralTargets = new ArrayList<>(welfareServiceRepository.findBySourceType(WelfareService.SourceType.BOKJIRO_CENTRAL));
        List<WelfareService> localTargets = new ArrayList<>(welfareServiceRepository.findBySourceType(WelfareService.SourceType.BOKJIRO_LOCAL));
        BudgetAllocation budgetAllocation = allocateBudgets(maxCalls, centralTargets.size(), localTargets.size());

        CollectStats centralStats = collectBySource(
                WelfareService.SourceType.BOKJIRO_CENTRAL,
                centralTargets,
                budgetAllocation.centralBudget(),
                refreshExisting
        );
        CollectStats localStats = collectBySource(
                WelfareService.SourceType.BOKJIRO_LOCAL,
                localTargets,
                budgetAllocation.localBudget(),
                refreshExisting
        );

        int calls = centralStats.calls() + localStats.calls();
        int saved = centralStats.saved() + localStats.saved();
        int skipped = centralStats.skipped() + localStats.skipped();
        int failed = centralStats.failed() + localStats.failed();

        log.info("[BokjiroDetailCollectService] 상세 수집 완료 refreshExisting={} calls={} saved={} skipped={} failed={} maxCalls={} centralCalls={} localCalls={}",
                refreshExisting, calls, saved, skipped, failed, maxCalls, centralStats.calls(), localStats.calls());
        String metadataJson = """
                {"maxCalls":%d,"centralBudget":%d,"localBudget":%d,"centralCalls":%d,"localCalls":%d,"refreshExisting":%s}
                """.formatted(
                maxCalls,
                budgetAllocation.centralBudget(),
                budgetAllocation.localBudget(),
                centralStats.calls(),
                localStats.calls(),
                refreshExisting
        ).trim();
        return CollectResult.withMetadata(calls, saved, skipped, 0, failed, metadataJson);
    }

    private CollectStats collectBySource(WelfareService.SourceType sourceType,
                                         List<WelfareService> targets,
                                         int callBudget,
                                         boolean refreshExisting) {
        if (callBudget <= 0) {
            return new CollectStats(0, 0, 0, 0);
        }

        int calls = 0;
        int saved = 0;
        int skipped = 0;
        int failed = 0;
        int rateLimitHits = 0;

        for (WelfareService service : targets) {
            if (calls >= callBudget) break;
            if (!refreshExisting && detailRepository.existsByServiceId(service.getId())) {
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
                NormalizedPolicyAggregate aggregate = welfareServiceMapper.toNormalizedBokjiroDetail(service, payload);
                Optional<WelfareServiceDetail> existing = detailRepository.findByServiceId(service.getId());
                WelfareServiceDetail entity = existing.orElse(
                        WelfareServiceDetail.builder().service(service).build()
                );

                NormalizedPolicyAggregate.Detail detail = aggregate.detail();
                WelfareServiceDetail merged = WelfareServiceDetail.builder()
                        .id(entity.getId())
                        .service(service)
                        .targetDetail(detail.targetDetail())
                        .supportDetail(detail.supportDetail())
                        .applyMethodDetail(detail.applyMethodDetail())
                        .selectionCriteria(detail.selectionCriteria())
                        .contactList(toJsonArray(detail.contactText()))
                        .supportCycle(detail.supportCycle())
                        .provisionType(detail.provisionType())
                        .build();

                detailRepository.save(merged);
                applyFallbacksToService(service, aggregate);
                normalizedPolicySidecarWriter.upsert(service, aggregate);
                searchYouthRelevanceService.refreshForService(service, serviceTagRepository.findByServiceId(service.getId()));
                saved++;
            } catch (Exception e) {
                log.warn("[BokjiroDetailCollectService] 상세 저장 실패 serviceId={} sourceType={} refreshExisting={} err={}",
                        service.getId(), service.getSourceType(), refreshExisting, e.getMessage());
                failed++;
            }
        }

        log.info("[BokjiroDetailCollectService] sourceType={} refreshExisting={} 상세 수집 완료 calls={} saved={} skipped={} failed={} budget={}",
                sourceType, refreshExisting, calls, saved, skipped, failed, callBudget);
        return new CollectStats(calls, saved, skipped, failed);
    }

    private BudgetAllocation allocateBudgets(int maxCalls, int centralTargetCount, int localTargetCount) {
        if (maxCalls <= 0) {
            return new BudgetAllocation(0, 0);
        }

        boolean hasCentralTargets = centralTargetCount > 0;
        boolean hasLocalTargets = localTargetCount > 0;

        if (!hasCentralTargets && !hasLocalTargets) {
            return new BudgetAllocation(0, 0);
        }
        if (!hasCentralTargets) {
            return new BudgetAllocation(0, Math.min(maxCallsPerApiPerRun, maxCalls));
        }
        if (!hasLocalTargets) {
            return new BudgetAllocation(Math.min(maxCallsPerApiPerRun, maxCalls), 0);
        }

        int totalTargets = centralTargetCount + localTargetCount;
        double centralShare = (double) centralTargetCount / totalTargets;
        double localShare = (double) localTargetCount / totalTargets;

        int centralBudget = Math.min(maxCallsPerApiPerRun, (int) Math.floor(maxCalls * centralShare));
        int localBudget = Math.min(maxCallsPerApiPerRun, (int) Math.floor(maxCalls * localShare));
        int remaining = maxCalls - centralBudget - localBudget;

        double centralRemainder = maxCalls * centralShare - Math.floor(maxCalls * centralShare);
        double localRemainder = maxCalls * localShare - Math.floor(maxCalls * localShare);

        while (remaining > 0) {
            boolean canGiveCentral = centralBudget < maxCallsPerApiPerRun;
            boolean canGiveLocal = localBudget < maxCallsPerApiPerRun;

            if (!canGiveCentral && !canGiveLocal) {
                break;
            }
            if (!canGiveLocal || (canGiveCentral && centralRemainder >= localRemainder)) {
                centralBudget++;
            } else {
                localBudget++;
            }
            remaining--;
        }

        return new BudgetAllocation(centralBudget, localBudget);
    }

    private String toJsonArray(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String escaped = raw
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return "[\"" + escaped + "\"]";
    }

    private FetchOutcome fetchWithRetry(WelfareService service) {
        BokjiroDetailClient.FetchResult result = null;
        int requestCount = 0;
        for (int attempt = 1; attempt <= retryMaxAttempts; attempt++) {
            requestCount++;
            result = switch (service.getSourceType()) {
                case BOKJIRO_CENTRAL -> detailClient.fetchCentralWithStatus(service.getSourceId());
                case BOKJIRO_LOCAL -> detailClient.fetchLocalWithStatus(service.getSourceId());
                default -> BokjiroDetailClient.FetchResult.failure(false, false, null);
            };

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

    private void applyFallbacksToService(WelfareService service, NormalizedPolicyAggregate aggregate) {
        Integer minAge = findAgeMin(aggregate);
        Integer maxAge = findAgeMax(aggregate);
        java.time.LocalDate applyEndDate = findApplyEndDate(aggregate);
        NormalizedPolicyAggregate.Detail detail = aggregate.detail();
        service.applyDetailFallbacks(
                RawFieldValidator.normalize(detail.supportDetail()),
                RawFieldValidator.normalize(detail.applyMethodDetail()),
                minAge,
                maxAge,
                applyEndDate,
                inferOnlineApply(service.getDetailUrl(), detail.applyMethodDetail(), detail.supportDetail())
        );
    }

    private Integer findAgeMin(NormalizedPolicyAggregate aggregate) {
        return aggregate.facts().stream()
                .filter(fact -> "AGE".equals(fact.factGroup()))
                .map(NormalizedPolicyAggregate.Fact::rangeMinInt)
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }

    private Integer findAgeMax(NormalizedPolicyAggregate aggregate) {
        return aggregate.facts().stream()
                .filter(fact -> "AGE".equals(fact.factGroup()))
                .map(NormalizedPolicyAggregate.Fact::rangeMaxInt)
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }

    private java.time.LocalDate findApplyEndDate(NormalizedPolicyAggregate aggregate) {
        return aggregate.facts().stream()
                .filter(fact -> "APPLY_END_DATE".equals(fact.factGroup()))
                .map(NormalizedPolicyAggregate.Fact::dateValue)
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }

    private Boolean inferOnlineApply(String detailUrl, String... texts) {
        if (RawFieldValidator.normalize(detailUrl) != null) {
            return true;
        }
        if (texts == null) {
            return null;
        }
        for (String text : texts) {
            String normalized = RawFieldValidator.normalize(text);
            if (normalized == null) {
                continue;
            }
            if (normalized.contains("온라인")
                    || normalized.contains("인터넷")
                    || normalized.contains("홈페이지")
                    || normalized.contains("모바일")
                    || normalized.contains("누리집")) {
                return true;
            }
        }
        return null;
    }

    private void sleepQuietly(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record CollectStats(int calls, int saved, int skipped, int failed) {
    }

    private record FetchOutcome(BokjiroDetailClient.FetchResult result, int requestCount) {
    }

    private record BudgetAllocation(int centralBudget, int localBudget) {
    }
}
