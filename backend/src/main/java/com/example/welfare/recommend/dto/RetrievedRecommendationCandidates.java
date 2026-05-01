package com.example.welfare.recommend.dto;

import com.example.welfare.policy.entity.WelfareService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * retrieval 단계에서 legacy candidate entity 와 canonical read-model projection 을 병행 보관한다.
 * scoring 전환 전까지는 facade 가 candidates 만 꺼내 쓰고, projection map 은 후속 task 에서 소비한다.
 */
public record RetrievedRecommendationCandidates(
        List<WelfareService> candidates,
        Map<Long, RecommendationCandidateProjection> projections
) {

    public RetrievedRecommendationCandidates {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        projections = projections == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(projections));
    }

    public boolean isEmpty() {
        return candidates.isEmpty();
    }
}
