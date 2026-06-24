package com.example.welfare.policy.service;

import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.entity.RawApiPayload;
import com.example.welfare.collect.gateway.BokjiroDetailClient;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.collect.repository.NormalizedPolicySidecarBackfillReadRepository;
import com.example.welfare.collect.repository.NormalizedPolicySidecarBackfillTarget;
import com.example.welfare.collect.service.CollectPolicyAggregateApplyService;
import com.example.welfare.collect.support.CollectSourceRegistry;
import com.example.welfare.policy.dto.PolicyReferenceUrlBackfillResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceDetailRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyReferenceUrlAdminService {

    private final NormalizedPolicySidecarBackfillReadRepository normalizedPolicySidecarBackfillReadRepository;
    private final WelfareServiceDetailRepository welfareServiceDetailRepository;
    private final WelfareServiceMapper welfareServiceMapper;
    private final CollectPolicyAggregateApplyService collectPolicyAggregateApplyService;
    private final ObjectMapper objectMapper;

    @Transactional
    public PolicyReferenceUrlBackfillResponse rebuildReferenceUrls(List<WelfareService.SourceType> sourceTypes,
                                                                   int limitPerSource,
                                                                   boolean missingOnly) {
        List<WelfareService.SourceType> effectiveSourceTypes = normalizeSourceTypes(sourceTypes);

        int scanned = 0;
        int skipped = 0;
        int updated = 0;
        int missingService = 0;
        int failed = 0;

        for (WelfareService.SourceType sourceType : effectiveSourceTypes) {
            List<NormalizedPolicySidecarBackfillTarget> targets =
                    normalizedPolicySidecarBackfillReadRepository.findTargetsBySourceTypeAndApiCategoryOrderByFetchedAtAsc(
                            sourceType,
                            RawApiPayload.ApiCategory.DETAIL,
                            limitPerSource
                    );

            for (NormalizedPolicySidecarBackfillTarget target : targets) {
                scanned++;
                WelfareService service = target.matchedService();
                if (service == null) {
                    missingService++;
                    continue;
                }

                try {
                    var existingDetail = welfareServiceDetailRepository.findByServiceId(service.getId()).orElse(null);
                    if (missingOnly
                            && existingDetail != null
                            && existingDetail.getReferenceUrlsJson() != null
                            && !existingDetail.getReferenceUrlsJson().isBlank()) {
                        skipped++;
                        continue;
                    }
                    NormalizedPolicyAggregate aggregate = toDetailAggregate(service, target.rawApiPayload());
                    collectPolicyAggregateApplyService.applyCollectedDetail(
                            service,
                            existingDetail,
                            aggregate
                    );
                    updated++;
                } catch (Exception e) {
                    failed++;
                    log.warn("[PolicyReferenceUrlAdminService] reference url backfill 실패 sourceType={} sourceId={} errorType={}",
                            sourceType, target.rawApiPayload().getSourceId(), e.getClass().getSimpleName());
                }
            }
        }

        String scope = effectiveSourceTypes.size() == WelfareService.SourceType.values().length ? "all-detail-sources" : "selected-detail-sources";
        log.info("[PolicyReferenceUrlAdminService] reference url backfill 완료 scope={} sourceTypes={} limitPerSource={} missingOnly={} scanned={} skipped={} updated={} missing={} failed={}",
                scope, effectiveSourceTypes, limitPerSource, missingOnly, scanned, skipped, updated, missingService, failed);
        return new PolicyReferenceUrlBackfillResponse(
                scope,
                effectiveSourceTypes,
                limitPerSource,
                missingOnly,
                scanned,
                skipped,
                updated,
                missingService,
                failed
        );
    }

    private List<WelfareService.SourceType> normalizeSourceTypes(List<WelfareService.SourceType> sourceTypes) {
        if (sourceTypes == null || sourceTypes.isEmpty()) {
            return List.of(WelfareService.SourceType.values());
        }
        return sourceTypes.stream().distinct().toList();
    }

    private NormalizedPolicyAggregate toDetailAggregate(WelfareService service, RawApiPayload rawApiPayload) throws Exception {
        if (service.getSourceType() == WelfareService.SourceType.YOUTH) {
            YouthApiDto.Item detail = objectMapper.readValue(rawApiPayload.getPayloadJson(), YouthApiDto.Item.class);
            return welfareServiceMapper.toYouthDetailAggregate(service, detail);
        }

        BokjiroDetailClient.DetailPayload detailPayload = objectMapper.readValue(
                rawApiPayload.getPayloadJson(),
                BokjiroDetailClient.DetailPayload.class
        );
        return CollectSourceRegistry.of(service.getSourceType()).toDetailAggregate(welfareServiceMapper, service, detailPayload);
    }
}
