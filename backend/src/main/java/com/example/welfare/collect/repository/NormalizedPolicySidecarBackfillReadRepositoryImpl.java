package com.example.welfare.collect.repository;

import com.example.welfare.collect.entity.RawApiPayload;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.collect.repository.RawApiPayloadRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class NormalizedPolicySidecarBackfillReadRepositoryImpl implements NormalizedPolicySidecarBackfillReadRepository {

    private final RawApiPayloadRepository rawApiPayloadRepository;
    private final WelfareServiceRepository welfareServiceRepository;
    private final EntityManager entityManager;

    @Override
    public Optional<WelfareService> findServiceBySourceTypeAndSourceId(WelfareService.SourceType sourceType, String sourceId) {
        return welfareServiceRepository.findBySourceTypeAndSourceId(sourceType, sourceId);
    }

    @Override
    public List<NormalizedPolicySidecarBackfillTarget> findTargetsBySourceTypeAndApiCategoryOrderByFetchedAtAsc(
            WelfareService.SourceType sourceType,
            RawApiPayload.ApiCategory apiCategory,
            int limitPerSource
    ) {
        List<RawApiPayload> payloads = rawApiPayloadRepository
                .findAllBySourceTypeAndApiCategoryOrderByFetchedAtAsc(sourceType, apiCategory);
        return limit(payloads, limitPerSource).stream()
                .map(this::toTarget)
                .toList();
    }

    @Override
    public List<NormalizedPolicySidecarBackfillTarget> findTargetsMissingSummarySlotsBySourceTypeAndApiCategoryOrderByFetchedAtAsc(
            WelfareService.SourceType sourceType,
            RawApiPayload.ApiCategory apiCategory,
            List<String> requiredSummarySlotKeys,
            int limitPerSource
    ) {
        if (requiredSummarySlotKeys == null || requiredSummarySlotKeys.isEmpty()) {
            return List.of();
        }

        var query = entityManager.createNativeQuery("""
                SELECT rap.id
                FROM raw_api_payloads rap
                JOIN welfare_services ws
                  ON ws.source_type = rap.source_type
                 AND ws.source_id = rap.source_id
                LEFT JOIN service_taxonomy_summary_slots sts
                  ON sts.service_id = ws.id
                 AND sts.slot_key IN (:slotKeys)
                WHERE rap.source_type = :sourceType
                  AND rap.api_category = :apiCategory
                GROUP BY rap.id, rap.fetched_at
                HAVING COUNT(DISTINCT sts.slot_key) < :slotCount
                ORDER BY rap.fetched_at ASC, rap.id ASC
                """)
                .setParameter("sourceType", sourceType.name())
                .setParameter("apiCategory", apiCategory.name())
                .setParameter("slotKeys", requiredSummarySlotKeys)
                .setParameter("slotCount", requiredSummarySlotKeys.size());
        if (limitPerSource > 0) {
            query.setMaxResults(limitPerSource);
        }

        @SuppressWarnings("unchecked")
        List<Number> rawIds = query.getResultList();
        List<Long> ids = rawIds.stream()
                .map(Number::longValue)
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }

        Map<Long, RawApiPayload> payloadsById = rawApiPayloadRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(RawApiPayload::getId, Function.identity()));
        return ids.stream()
                .map(payloadsById::get)
                .filter(raw -> raw != null)
                .map(this::toTarget)
                .toList();
    }

    private List<RawApiPayload> limit(List<RawApiPayload> payloads, int limitPerSource) {
        if (limitPerSource <= 0 || payloads.size() <= limitPerSource) {
            return payloads;
        }
        return payloads.subList(0, limitPerSource);
    }

    private NormalizedPolicySidecarBackfillTarget toTarget(RawApiPayload raw) {
        return new NormalizedPolicySidecarBackfillTarget(
                raw,
                welfareServiceRepository.findBySourceTypeAndSourceId(raw.getSourceType(), raw.getSourceId())
                        .orElse(null)
        );
    }
}
