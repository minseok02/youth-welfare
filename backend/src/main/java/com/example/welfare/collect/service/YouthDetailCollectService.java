package com.example.welfare.collect.service;

import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.gateway.YouthApiClient;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.collect.repository.BokjiroDetailReadRepository;
import com.example.welfare.policy.entity.WelfareService;
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

    @Value("${collect.youth.detail.request-interval-ms:500}")
    private long requestIntervalMs;

    public CollectResult collectYouthDetails() {
        return apiSyncLogService.runWithLog(
                CollectRuntimeLaneCatalog.YOUTH_DETAILS_LANE_KEY,
                this::collectYouthDetailsInternal
        );
    }

    private CollectResult collectYouthDetailsInternal() {
        List<WelfareService> targets = detailReadRepository.findTargetsBySourceType(WelfareService.SourceType.YOUTH);
        int requested = 0, saved = 0, skipped = 0, failed = 0;

        for (WelfareService service : targets) {
            if (detailReadRepository.existsDetailByServiceId(service.getId())) {
                skipped++;
                continue;
            }

            sleepQuietly(requestIntervalMs);
            requested++;

            YouthApiDto.Item detail;
            try {
                detail = youthApiClient.fetchDetail(service.getSourceId());
            } catch (Exception e) {
                failed++;
                log.warn("[YouthDetailCollectService] DETAIL API 오류 sourceId={} err={}", service.getSourceId(), e.getMessage());
                continue;
            }
            if (detail == null) {
                failed++;
                log.warn("[YouthDetailCollectService] DETAIL 수집 실패 sourceId={}", service.getSourceId());
                continue;
            }

            boolean rawSaved = rawApiPayloadService.saveYouthDetail(service.getSourceId(), detail);
            if (!rawSaved) {
                failed++;
                log.warn("[YouthDetailCollectService] raw 저장 실패 sourceId={}", service.getSourceId());
                continue;
            }

            try {
                NormalizedPolicyAggregate aggregate = welfareServiceMapper.toYouthDetailAggregate(service, detail);
                collectPolicyAggregateApplyService.applyCollectedDetail(
                        service,
                        detailReadRepository.findDetailByServiceId(service.getId()).orElse(null),
                        aggregate
                );
                saved++;
            } catch (Exception e) {
                failed++;
                log.warn("[YouthDetailCollectService] 저장 실패 sourceId={} err={}", service.getSourceId(), e.getMessage());
            }
        }

        log.info("[YouthDetailCollectService] 완료 requested={} saved={} skipped={} failed={}",
                requested, saved, skipped, failed);
        return new CollectResult(requested, saved, skipped, 0, failed, null);
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
