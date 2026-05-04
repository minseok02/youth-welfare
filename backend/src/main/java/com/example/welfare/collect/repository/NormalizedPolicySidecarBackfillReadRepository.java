package com.example.welfare.collect.repository;

import com.example.welfare.collect.entity.RawApiPayload;
import com.example.welfare.policy.entity.WelfareService;

import java.util.List;
import java.util.Optional;

public interface NormalizedPolicySidecarBackfillReadRepository {

    Optional<WelfareService> findServiceBySourceTypeAndSourceId(WelfareService.SourceType sourceType, String sourceId);

    List<NormalizedPolicySidecarBackfillTarget> findTargetsBySourceTypeAndApiCategoryOrderByFetchedAtAsc(
            WelfareService.SourceType sourceType,
            RawApiPayload.ApiCategory apiCategory,
            int limitPerSource
    );
}
