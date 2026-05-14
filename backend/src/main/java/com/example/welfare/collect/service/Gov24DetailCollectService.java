package com.example.welfare.collect.service;

import com.example.welfare.collect.dto.Gov24ServiceDetailDto;
import com.example.welfare.collect.gateway.Gov24Client;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.collect.repository.BokjiroDetailReadRepository;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class Gov24DetailCollectService {

    private final Gov24Client gov24Client;
    private final WelfareServiceMapper welfareServiceMapper;
    private final RawApiPayloadService rawApiPayloadService;
    private final CollectPolicyAggregateApplyService collectPolicyAggregateApplyService;
    private final BokjiroDetailReadRepository detailReadRepository;
    private final WelfareServiceRepository welfareServiceRepository;

    @Value("${collect.detail.gov24.max-calls-per-run:50}")
    private int maxCallsPerRun;

    @Value("${collect.detail.request-interval-ms:300}")
    private long requestIntervalMs;

    @Value("${collect.detail.retry.max-attempts:3}")
    private int retryMaxAttempts;

    @Value("${collect.detail.retry.base-backoff-ms:1500}")
    private long retryBaseBackoffMs;

    public CollectResult collectGov24Details() {
        return collectGov24Details(maxCallsPerRun);
    }

    public CollectResult collectGov24Details(int maxCallsPerRunOverride) {
        List<WelfareService> targets = detailReadRepository.findTargetsBySourceType(WelfareService.SourceType.GOV24);
        int requested = 0, saved = 0, skipped = 0, failed = 0;

        for (WelfareService service : targets) {
            if (requested >= maxCallsPerRunOverride) {
                break;
            }
            if (detailReadRepository.existsDetailByServiceId(service.getId())) {
                skipped++;
                continue;
            }

            sleepQuietly(requestIntervalMs);
            requested++;

            Gov24ServiceDetailDto.Item detail = fetchDetailWithRetry(service);
            if (detail == null) {
                failed++;
                log.warn("[Gov24DetailCollectService] DETAIL 수집 실패 sourceId={}", service.getSourceId());
                continue;
            }

            boolean rawSaved = rawApiPayloadService.saveGov24Detail(service.getSourceId(), detail);
            if (!rawSaved) {
                failed++;
                log.warn("[Gov24DetailCollectService] raw 저장 실패 sourceId={}", service.getSourceId());
                continue;
            }

            try {
                NormalizedPolicyAggregate aggregate = welfareServiceMapper.toGov24DetailAggregate(service, detail);
                collectPolicyAggregateApplyService.applyCollectedDetail(
                        service,
                        detailReadRepository.findDetailByServiceId(service.getId()).orElse(null),
                        aggregate
                );
                saved++;
            } catch (Exception e) {
                failed++;
                log.warn("[Gov24DetailCollectService] 저장 실패 sourceId={} err={}", service.getSourceId(), e.getMessage());
            }
        }

        log.info("[Gov24DetailCollectService] 완료 requested={} saved={} skipped={} failed={} maxCallsPerRun={}",
                requested, saved, skipped, failed, maxCallsPerRunOverride);
        return new CollectResult(requested, saved, skipped, 0, failed, null);
    }

    public CollectResult collectGov24DetailsForSourceId(String sourceId) {
        WelfareService service = welfareServiceRepository.findBySourceTypeAndSourceId(WelfareService.SourceType.GOV24, sourceId)
                .orElse(null);
        if (service == null) {
            return CollectResult.of(0, 0, 1, 0, 0);
        }

        sleepQuietly(requestIntervalMs);

        Gov24ServiceDetailDto.Item detail = fetchDetailWithRetry(service);
        if (detail == null) {
            return CollectResult.of(1, 0, 0, 0, 1);
        }

        boolean rawSaved = rawApiPayloadService.saveGov24Detail(service.getSourceId(), detail);
        if (!rawSaved) {
            return CollectResult.of(1, 0, 0, 0, 1);
        }

        try {
            NormalizedPolicyAggregate aggregate = welfareServiceMapper.toGov24DetailAggregate(service, detail);
            collectPolicyAggregateApplyService.applyCollectedDetail(
                    service,
                    detailReadRepository.findDetailByServiceId(service.getId()).orElse(null),
                    aggregate
            );
            return CollectResult.of(1, 1, 0, 0, 0);
        } catch (Exception e) {
            log.warn("[Gov24DetailCollectService] 단건 저장 실패 sourceId={} err={}", service.getSourceId(), e.getMessage());
            return CollectResult.of(1, 0, 0, 0, 1);
        }
    }

    private Gov24ServiceDetailDto.Item fetchDetailWithRetry(WelfareService service) {
        for (int attempt = 1; attempt <= retryMaxAttempts; attempt++) {
            try {
                Gov24ServiceDetailDto.Item detail = gov24Client.fetchDetail(service.getSourceId());
                if (detail != null) {
                    return detail;
                }
                log.warn("[Gov24DetailCollectService] DETAIL 응답 비어있음 sourceId={} attempt={}/{}",
                        service.getSourceId(), attempt, retryMaxAttempts);
            } catch (Exception e) {
                log.warn("[Gov24DetailCollectService] DETAIL API 오류 sourceId={} attempt={}/{} err={}",
                        service.getSourceId(), attempt, retryMaxAttempts, e.getMessage());
            }

            if (attempt < retryMaxAttempts) {
                long waitMs = retryBaseBackoffMs * attempt + ThreadLocalRandom.current().nextLong(100, 500);
                sleepQuietly(waitMs);
            }
        }
        return null;
    }

    private void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
