package com.example.welfare.collect.repository;

import com.example.welfare.collect.entity.RawApiPayload;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.collect.repository.RawApiPayloadRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NormalizedPolicySidecarBackfillReadRepositoryImpl implements NormalizedPolicySidecarBackfillReadRepository {

    private final RawApiPayloadRepository rawApiPayloadRepository;
    private final WelfareServiceRepository welfareServiceRepository;

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
                .map(raw -> new NormalizedPolicySidecarBackfillTarget(
                        raw,
                        welfareServiceRepository.findBySourceTypeAndSourceId(raw.getSourceType(), raw.getSourceId())
                                .orElse(null)
                ))
                .toList();
    }

    private List<RawApiPayload> limit(List<RawApiPayload> payloads, int limitPerSource) {
        if (limitPerSource <= 0 || payloads.size() <= limitPerSource) {
            return payloads;
        }
        return payloads.subList(0, limitPerSource);
    }
}
