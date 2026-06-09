package com.example.welfare.collect.service;

import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.gateway.YouthApiClient;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class YouthDetailCollectService {

    private final ApiSyncLogService apiSyncLogService;
    private final YouthApiClient youthApiClient;
    private final WelfareServiceMapper welfareServiceMapper;
    private final RawApiPayloadService rawApiPayloadService;
    private final CollectPolicyAggregateApplyService collectPolicyAggregateApplyService;
    private final BokjiroDetailReadRepository detailReadRepository;
    private final WelfareServiceRepository welfareServiceRepository;

    @Value("${collect.youth.detail.request-interval-ms:500}")
    private long requestIntervalMs;

    @Value("${collect.youth.detail.max-calls-per-run:50}")
    private int maxCallsPerRun;

    public CollectResult collectYouthDetails() {
        return apiSyncLogService.runWithLog(
                CollectRuntimeLaneCatalog.YOUTH_DETAILS_LANE_KEY,
                () -> collectYouthDetailsInternal(maxCallsPerRun)
        );
    }

    private CollectResult collectYouthDetailsInternal(int maxCalls) {
        List<WelfareService> targets = detailReadRepository.findTargetsBySourceType(WelfareService.SourceType.YOUTH);
        int requested = 0, saved = 0, skipped = 0, failed = 0;

        for (WelfareService service : targets) {
            if (requested >= maxCalls) {
                break;
            }
            if (detailReadRepository.existsDetailByServiceId(service.getId())) {
                skipped++;
                continue;
            }

            CollectResult result = collectOne(service);
            requested += result.requestedCount();
            saved += result.savedCount();
            skipped += result.skippedCount();
            failed += result.failedCount();
        }

        log.info("[YouthDetailCollectService] 완료 requested={} saved={} skipped={} failed={}",
                requested, saved, skipped, failed);
        return new CollectResult(requested, saved, skipped, 0, failed, null);
    }

    public CollectResult collectYouthDetailsForSourceId(String sourceId) {
        return apiSyncLogService.runWithLog(
                CollectRuntimeLaneCatalog.YOUTH_DETAILS_LANE_KEY,
                () -> {
                    WelfareService service = welfareServiceRepository
                            .findBySourceTypeAndSourceId(WelfareService.SourceType.YOUTH, sourceId)
                            .orElse(null);
                    if (service == null) {
                        return CollectResult.of(0, 0, 1, 0, 0);
                    }
                    return collectOne(service);
                }
        );
    }

    private CollectResult collectOne(WelfareService service) {
        sleepQuietly(requestIntervalMs);

        YouthApiDto.Item detail;
        try {
            detail = youthApiClient.fetchDetail(service.getSourceId());
        } catch (Exception e) {
            log.warn("[YouthDetailCollectService] DETAIL API 오류 sourceId={} errorType={}",
                    service.getSourceId(), e.getClass().getSimpleName());
            return CollectResult.of(1, 0, 0, 0, 1);
        }
        if (detail == null) {
            log.warn("[YouthDetailCollectService] DETAIL 수집 실패 sourceId={}", service.getSourceId());
            return CollectResult.of(1, 0, 0, 0, 1);
        }

        boolean rawSaved = rawApiPayloadService.saveYouthDetail(service.getSourceId(), detail);
        if (!rawSaved) {
            log.warn("[YouthDetailCollectService] raw 저장 실패 sourceId={}", service.getSourceId());
            return CollectResult.of(1, 0, 0, 0, 1);
        }

        try {
            NormalizedPolicyAggregate aggregate = welfareServiceMapper.toYouthDetailAggregate(service, detail);
            collectPolicyAggregateApplyService.applyCollectedDetail(
                    service,
                    detailReadRepository.findDetailByServiceId(service.getId()).orElse(null),
                    aggregate
            );
            return CollectResult.of(1, 1, 0, 0, 0);
        } catch (Exception e) {
            log.warn("[YouthDetailCollectService] 저장 실패 sourceId={} errorType={}",
                    service.getSourceId(), e.getClass().getSimpleName());
            return CollectResult.of(1, 0, 0, 0, 1);
        }
    }

    private void sleepQuietly(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
