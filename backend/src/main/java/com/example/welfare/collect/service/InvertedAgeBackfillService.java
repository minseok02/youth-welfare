package com.example.welfare.collect.service;

import com.example.welfare.collect.dto.Gov24ServiceDetailDto;
import com.example.welfare.collect.dto.InvertedAgeBackfillResponse;
import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.entity.RawApiPayload;
import com.example.welfare.collect.gateway.BokjiroDetailClient;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.collect.repository.RawApiPayloadReadRepository;
import com.example.welfare.collect.support.CollectSourceRegistry;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceDetailRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvertedAgeBackfillService {

    private final WelfareServiceRepository welfareServiceRepository;
    private final WelfareServiceDetailRepository welfareServiceDetailRepository;
    private final RawApiPayloadReadRepository rawApiPayloadReadRepository;
    private final WelfareServiceMapper welfareServiceMapper;
    private final CollectPolicyAggregateApplyService collectPolicyAggregateApplyService;
    private final ObjectMapper objectMapper;

    public InvertedAgeBackfillResponse backfill(List<WelfareService.SourceType> sourceTypes, int limitPerSource) {
        List<WelfareService.SourceType> effectiveSourceTypes = normalizeSourceTypes(sourceTypes);

        int scanned = 0;
        int repaired = 0;
        int missingRawPayload = 0;
        int unrepaired = 0;
        int failed = 0;

        for (WelfareService.SourceType sourceType : effectiveSourceTypes) {
            List<WelfareService> targets = welfareServiceRepository.findInvalidAgeRangeTargetsBySourceType(
                    sourceType,
                    PageRequest.of(0, limitPerSource)
            );

            for (WelfareService service : targets) {
                scanned++;
                var rawPayload = rawApiPayloadReadRepository.findBySourceTypeAndSourceIdAndApiCategory(
                        service.getSourceType(),
                        service.getSourceId(),
                        RawApiPayload.ApiCategory.DETAIL
                );
                if (rawPayload.isEmpty()) {
                    missingRawPayload++;
                    continue;
                }

                try {
                    NormalizedPolicyAggregate aggregate = toDetailAggregate(service, rawPayload.get());
                    collectPolicyAggregateApplyService.applyCollectedDetail(
                            service,
                            welfareServiceDetailRepository.findByServiceId(service.getId()).orElse(null),
                            aggregate
                    );

                    WelfareService refreshed = welfareServiceRepository.findById(service.getId())
                            .orElseThrow(() -> new IllegalStateException("정책을 찾을 수 없습니다. serviceId=" + service.getId()));
                    if (hasInvalidAgeRange(refreshed)) {
                        unrepaired++;
                        log.warn("[InvertedAgeBackfillService] replay 후에도 age range가 비정상입니다. sourceType={} sourceId={} minAge={} maxAge={}",
                                refreshed.getSourceType(), refreshed.getSourceId(), refreshed.getMinAge(), refreshed.getMaxAge());
                    } else {
                        repaired++;
                    }
                } catch (Exception e) {
                    failed++;
                    log.warn("[InvertedAgeBackfillService] inverted age backfill 실패 sourceType={} sourceId={} err={}",
                            service.getSourceType(), service.getSourceId(), e.getMessage());
                }
            }
        }

        String scope = effectiveSourceTypes.size() == WelfareService.SourceType.values().length
                ? "all-source-types"
                : "selected-source-types";
        log.info("[InvertedAgeBackfillService] inverted age backfill 완료 scope={} sourceTypes={} limitPerSource={} scanned={} repaired={} missingRawPayload={} unrepaired={} failed={}",
                scope, effectiveSourceTypes, limitPerSource, scanned, repaired, missingRawPayload, unrepaired, failed);
        return new InvertedAgeBackfillResponse(
                scope,
                effectiveSourceTypes,
                limitPerSource,
                scanned,
                repaired,
                missingRawPayload,
                unrepaired,
                failed
        );
    }

    private List<WelfareService.SourceType> normalizeSourceTypes(List<WelfareService.SourceType> sourceTypes) {
        if (sourceTypes == null || sourceTypes.isEmpty()) {
            return List.of(WelfareService.SourceType.values());
        }
        return sourceTypes.stream().distinct().toList();
    }

    private boolean hasInvalidAgeRange(WelfareService service) {
        return service.getMinAge() != null
                && service.getMaxAge() != null
                && service.getMinAge() > service.getMaxAge();
    }

    private NormalizedPolicyAggregate toDetailAggregate(WelfareService service, RawApiPayload rawApiPayload) throws Exception {
        return switch (service.getSourceType()) {
            case YOUTH -> welfareServiceMapper.toYouthDetailAggregate(
                    service,
                    objectMapper.readValue(rawApiPayload.getPayloadJson(), YouthApiDto.Item.class)
            );
            case GOV24 -> welfareServiceMapper.toGov24DetailAggregate(
                    service,
                    objectMapper.readValue(rawApiPayload.getPayloadJson(), Gov24ServiceDetailDto.Item.class)
            );
            case BOKJIRO_CENTRAL, BOKJIRO_LOCAL -> CollectSourceRegistry.of(service.getSourceType()).toDetailAggregate(
                    welfareServiceMapper,
                    service,
                    objectMapper.readValue(rawApiPayload.getPayloadJson(), BokjiroDetailClient.DetailPayload.class)
            );
        };
    }
}
