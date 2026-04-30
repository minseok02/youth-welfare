package com.example.welfare.collect.normalization;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.collect.dto.BokjiroLocalDto;
import com.example.welfare.collect.entity.RawApiPayload;
import com.example.welfare.collect.gateway.BokjiroDetailClient;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.collect.repository.RawApiPayloadRepository;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NormalizedPolicySidecarBackfillService {

    private final RawApiPayloadRepository rawApiPayloadRepository;
    private final WelfareServiceRepository welfareServiceRepository;
    private final WelfareServiceMapper welfareServiceMapper;
    private final NormalizedPolicySidecarWriter normalizedPolicySidecarWriter;
    private final ObjectMapper objectMapper;

    @Transactional
    public BackfillResult backfillBokjiroListSidecars(int limitPerSource) {
        BackfillResult central = backfillListSource(WelfareService.SourceType.BOKJIRO_CENTRAL, limitPerSource);
        BackfillResult local = backfillListSource(WelfareService.SourceType.BOKJIRO_LOCAL, limitPerSource);
        return central.plus(local);
    }

    @Transactional
    public BackfillResult backfillBokjiroDetailSidecars(int limitPerSource) {
        BackfillResult central = backfillDetailSource(WelfareService.SourceType.BOKJIRO_CENTRAL, limitPerSource);
        BackfillResult local = backfillDetailSource(WelfareService.SourceType.BOKJIRO_LOCAL, limitPerSource);
        return central.plus(local);
    }

    private BackfillResult backfillListSource(WelfareService.SourceType sourceType, int limitPerSource) {
        List<RawApiPayload> payloads = rawApiPayloadRepository
                .findAllBySourceTypeAndApiCategoryOrderByFetchedAtAsc(sourceType, RawApiPayload.ApiCategory.LIST);

        int scanned = 0;
        int upserted = 0;
        int missingService = 0;
        int failed = 0;

        for (RawApiPayload raw : limit(payloads, limitPerSource)) {
            scanned++;
            WelfareService service = welfareServiceRepository
                    .findBySourceTypeAndSourceId(raw.getSourceType(), raw.getSourceId())
                    .orElse(null);
            if (service == null) {
                missingService++;
                continue;
            }

            try {
                NormalizedPolicyAggregate aggregate = switch (sourceType) {
                    case BOKJIRO_CENTRAL -> welfareServiceMapper.toNormalizedBokjiroCentral(
                            objectMapper.readValue(raw.getPayloadJson(), BokjiroCentralDto.Item.class),
                            null
                    );
                    case BOKJIRO_LOCAL -> welfareServiceMapper.toNormalizedBokjiroLocal(
                            objectMapper.readValue(raw.getPayloadJson(), BokjiroLocalDto.Item.class),
                            null
                    );
                    default -> throw new IllegalArgumentException("지원하지 않는 sourceType: " + sourceType);
                };
                normalizedPolicySidecarWriter.upsert(service, aggregate);
                upserted++;
            } catch (Exception e) {
                failed++;
                log.warn("[NormalizedPolicySidecarBackfillService] list backfill 실패 sourceType={} sourceId={} err={}",
                        raw.getSourceType(), raw.getSourceId(), e.getMessage());
            }
        }

        return new BackfillResult(scanned, upserted, missingService, failed);
    }

    private BackfillResult backfillDetailSource(WelfareService.SourceType sourceType, int limitPerSource) {
        List<RawApiPayload> payloads = rawApiPayloadRepository
                .findAllBySourceTypeAndApiCategoryOrderByFetchedAtAsc(sourceType, RawApiPayload.ApiCategory.DETAIL);

        int scanned = 0;
        int upserted = 0;
        int missingService = 0;
        int failed = 0;

        for (RawApiPayload raw : limit(payloads, limitPerSource)) {
            scanned++;
            WelfareService service = welfareServiceRepository
                    .findBySourceTypeAndSourceId(raw.getSourceType(), raw.getSourceId())
                    .orElse(null);
            if (service == null) {
                missingService++;
                continue;
            }

            try {
                BokjiroDetailClient.DetailPayload payload =
                        objectMapper.readValue(raw.getPayloadJson(), BokjiroDetailClient.DetailPayload.class);
                NormalizedPolicyAggregate aggregate = welfareServiceMapper.toNormalizedBokjiroDetail(service, payload);
                normalizedPolicySidecarWriter.upsert(service, aggregate);
                upserted++;
            } catch (Exception e) {
                failed++;
                log.warn("[NormalizedPolicySidecarBackfillService] detail backfill 실패 sourceType={} sourceId={} err={}",
                        raw.getSourceType(), raw.getSourceId(), e.getMessage());
            }
        }

        return new BackfillResult(scanned, upserted, missingService, failed);
    }

    private List<RawApiPayload> limit(List<RawApiPayload> payloads, int limitPerSource) {
        if (limitPerSource <= 0 || payloads.size() <= limitPerSource) {
            return payloads;
        }
        return payloads.subList(0, limitPerSource);
    }

    public record BackfillResult(int scannedCount, int upsertedCount, int missingServiceCount, int failedCount) {
        public BackfillResult plus(BackfillResult other) {
            return new BackfillResult(
                    scannedCount + other.scannedCount,
                    upsertedCount + other.upsertedCount,
                    missingServiceCount + other.missingServiceCount,
                    failedCount + other.failedCount
            );
        }
    }
}
