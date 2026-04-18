package com.example.welfare.collect.service;

import com.example.welfare.collect.gateway.BokjiroDetailClient;
import com.example.welfare.collect.validation.RawFieldValidator;
import com.example.welfare.collect.validation.TextConstraintExtractor;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.entity.WelfareServiceDetail;
import com.example.welfare.policy.repository.WelfareServiceDetailRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
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
    private final BokjiroDetailClient detailClient;
    private final RawApiPayloadService rawApiPayloadService;

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
        return collectBokjiroDetails(maxCallsPerRun);
    }

    @Transactional
    public int collectBokjiroDetails(int maxCalls) {
        int centralBudget = Math.min(maxCallsPerApiPerRun, maxCalls);
        int localBudget = Math.min(maxCallsPerApiPerRun, Math.max(0, maxCalls - centralBudget));

        CollectStats centralStats = collectBySource(WelfareService.SourceType.BOKJIRO_CENTRAL, centralBudget);
        CollectStats localStats = collectBySource(WelfareService.SourceType.BOKJIRO_LOCAL, localBudget);

        int calls = centralStats.calls() + localStats.calls();
        int saved = centralStats.saved() + localStats.saved();
        int skipped = centralStats.skipped() + localStats.skipped();
        int failed = centralStats.failed() + localStats.failed();

        log.info("[BokjiroDetailCollectService] 상세 수집 완료 calls={} saved={} skipped={} failed={} maxCalls={} centralCalls={} localCalls={}",
                calls, saved, skipped, failed, maxCalls, centralStats.calls(), localStats.calls());
        return saved;
    }

    private CollectStats collectBySource(WelfareService.SourceType sourceType, int callBudget) {
        if (callBudget <= 0) {
            return new CollectStats(0, 0, 0, 0);
        }

        List<WelfareService> targets = new ArrayList<>(welfareServiceRepository.findBySourceType(sourceType));

        int calls = 0;
        int saved = 0;
        int skipped = 0;
        int failed = 0;
        int rateLimitHits = 0;

        for (WelfareService service : targets) {
            if (calls >= callBudget) break;
            if (detailRepository.existsByServiceId(service.getId())) {
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
                    log.warn("[BokjiroDetailCollectService] 연속 429 발생으로 sourceType={} 수집 중단 calls={} rateLimitHits={}",
                            sourceType, calls, rateLimitHits);
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
                Optional<WelfareServiceDetail> existing = detailRepository.findByServiceId(service.getId());
                WelfareServiceDetail entity = existing.orElse(
                        WelfareServiceDetail.builder().service(service).build()
                );

                WelfareServiceDetail merged = WelfareServiceDetail.builder()
                        .id(entity.getId())
                        .service(service)
                        .targetDetail(payload.getTargetDetail())
                        .supportDetail(payload.getSupportDetail())
                        .applyMethodDetail(payload.getApplyMethodDetail())
                        .selectionCriteria(payload.getSelectionCriteria())
                        .contactList(toJsonArray(payload.getContactList()))
                        .supportCycle(payload.getSupportCycle())
                        .provisionType(payload.getProvisionType())
                        .build();

                detailRepository.save(merged);
                applyFallbacksToService(service, payload);
                saved++;
            } catch (Exception e) {
                log.warn("[BokjiroDetailCollectService] 상세 저장 실패 serviceId={} sourceType={} err={}",
                        service.getId(), service.getSourceType(), e.getMessage());
                failed++;
            }
        }

        log.info("[BokjiroDetailCollectService] sourceType={} 상세 수집 완료 calls={} saved={} skipped={} failed={} budget={}",
                sourceType, calls, saved, skipped, failed, callBudget);
        return new CollectStats(calls, saved, skipped, failed);
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

    private void applyFallbacksToService(WelfareService service, BokjiroDetailClient.DetailPayload payload) {
        TextConstraintExtractor.ConstraintSummary constraints = TextConstraintExtractor.summarize(
                payload.getTargetDetail(),
                payload.getSupportDetail(),
                payload.getApplyMethodDetail(),
                payload.getSelectionCriteria()
        );

        service.applyDetailFallbacks(
                RawFieldValidator.normalize(payload.getSupportDetail()),
                RawFieldValidator.normalize(payload.getApplyMethodDetail()),
                constraints.minAge(),
                constraints.maxAge(),
                constraints.applyEndDate(),
                inferOnlineApply(service.getDetailUrl(), payload.getApplyMethodDetail(), payload.getSupportDetail())
        );
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
}
