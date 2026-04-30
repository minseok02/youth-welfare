package com.example.welfare.recommend.service;

import com.example.welfare.recommend.dto.PriorityPreference;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.policy.entity.WelfareService;

public interface PriorityMatcher {

    boolean matches(PriorityPreference priority, WelfareService service);

    default boolean matches(PriorityPreference priority,
                            WelfareService service,
                            RecommendationCandidateProjection projection) {
        return matches(priority, service);
    }
}
