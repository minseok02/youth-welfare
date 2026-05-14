package com.example.welfare.collect.service;

import com.example.welfare.collect.dto.Gov24SupportConditionsDto;
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
public class Gov24SupportConditionsCollectService {

    private final Gov24Client gov24Client;
    private final WelfareServiceMapper welfareServiceMapper;
    private final RawApiPayloadService rawApiPayloadService;
    private final CollectPolicyAggregateApplyService collectPolicyAggregateApplyService;
    private final BokjiroDetailReadRepository detailReadRepository;
    private final WelfareServiceRepository welfareServiceRepository;

    @Value("${collect.support-conditions.gov24.max-calls-per-run:50}")
    private int maxCallsPerRun;

    @Value("${collect.detail.request-interval-ms:300}")
    private long requestIntervalMs;

    @Value("${collect.detail.retry.max-attempts:3}")
    private int retryMaxAttempts;

    @Value("${collect.detail.retry.base-backoff-ms:1500}")
    private long retryBaseBackoffMs;

    public CollectResult collectGov24SupportConditions() {
        return collectGov24SupportConditions(maxCallsPerRun);
    }

    public CollectResult collectGov24SupportConditions(int maxCallsPerRunOverride) {
        List<WelfareService> targets = detailReadRepository.findTargetsBySourceType(WelfareService.SourceType.GOV24);
        int requested = 0, saved = 0, skipped = 0, failed = 0;

        for (WelfareService service : targets) {
            if (requested >= maxCallsPerRunOverride) {
                break;
            }
            if (rawApiPayloadService.existsGov24SupportConditions(service.getSourceId())) {
                skipped++;
                continue;
            }

            sleepQuietly(requestIntervalMs);
            requested++;

            Gov24SupportConditionsDto.Item payload = fetchSupportConditionsWithRetry(service);
            if (payload == null) {
                failed++;
                continue;
            }

            boolean rawSaved = rawApiPayloadService.saveGov24SupportConditions(service.getSourceId(), payload);
            if (!rawSaved) {
                failed++;
                log.warn("[Gov24SupportConditionsCollectService] raw 저장 실패 sourceId={}", service.getSourceId());
                continue;
            }

            try {
                NormalizedPolicyAggregate aggregate = welfareServiceMapper.toGov24SupportConditionsAggregate(service, payload);
                collectPolicyAggregateApplyService.replaceFactCodeSet(service, "GOV24_SUPPORT_CONDITION", aggregate);
                saved++;
            } catch (Exception e) {
                failed++;
                log.warn("[Gov24SupportConditionsCollectService] 저장 실패 sourceId={} err={}", service.getSourceId(), e.getMessage());
            }
        }

        log.info("[Gov24SupportConditionsCollectService] 완료 requested={} saved={} skipped={} failed={} maxCallsPerRun={}",
                requested, saved, skipped, failed, maxCallsPerRunOverride);
        return new CollectResult(requested, saved, skipped, 0, failed, null);
    }

    public CollectResult collectGov24SupportConditionsForSourceId(String sourceId) {
        WelfareService service = welfareServiceRepository.findBySourceTypeAndSourceId(WelfareService.SourceType.GOV24, sourceId)
                .orElse(null);
        if (service == null) {
            return CollectResult.of(0, 0, 1, 0, 0);
        }

        sleepQuietly(requestIntervalMs);

        Gov24SupportConditionsDto.Item payload = fetchSupportConditionsWithRetry(service);
        if (payload == null) {
            return CollectResult.of(1, 0, 0, 0, 1);
        }

        boolean rawSaved = rawApiPayloadService.saveGov24SupportConditions(service.getSourceId(), payload);
        if (!rawSaved) {
            return CollectResult.of(1, 0, 0, 0, 1);
        }

        try {
            NormalizedPolicyAggregate aggregate = welfareServiceMapper.toGov24SupportConditionsAggregate(service, payload);
            collectPolicyAggregateApplyService.replaceFactCodeSet(service, "GOV24_SUPPORT_CONDITION", aggregate);
            return CollectResult.of(1, 1, 0, 0, 0);
        } catch (Exception e) {
            log.warn("[Gov24SupportConditionsCollectService] 단건 저장 실패 sourceId={} err={}", service.getSourceId(), e.getMessage());
            return CollectResult.of(1, 0, 0, 0, 1);
        }
    }

    private Gov24SupportConditionsDto.Item fetchSupportConditionsWithRetry(WelfareService service) {
        for (int attempt = 1; attempt <= retryMaxAttempts; attempt++) {
            try {
                Gov24SupportConditionsDto.Item payload = gov24Client.fetchSupportConditions(service.getSourceId());
                if (payload != null) {
                    return payload;
                }
                log.warn("[Gov24SupportConditionsCollectService] 응답 비어있음 sourceId={} attempt={}/{}",
                        service.getSourceId(), attempt, retryMaxAttempts);
            } catch (Exception e) {
                log.warn("[Gov24SupportConditionsCollectService] API 오류 sourceId={} attempt={}/{} err={}",
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
