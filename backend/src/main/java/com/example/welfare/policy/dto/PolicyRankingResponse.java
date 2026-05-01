package com.example.welfare.policy.dto;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PolicyRankingResponse {

    private Long serviceId;
    private String title;
    private String unifiedCategory;
    private String sourceType;
    private Long uniqueViewCount7d;
    private Long viewCount;
    private Long apiViewCount;
    private double rankingScore;

    public static PolicyRankingResponse of(WelfareService service, long uniqueViewCount7d, double score) {
        return of(service, uniqueViewCount7d, score, null);
    }

    public static PolicyRankingResponse of(WelfareService service,
                                           long uniqueViewCount7d,
                                           double score,
                                           RecommendationCandidateProjection projection) {
        return PolicyRankingResponse.builder()
                .serviceId(service.getId())
                .title(service.getTitle())
                .unifiedCategory(resolveUnifiedCategory(service, projection))
                .sourceType(service.getSourceType().name())
                .uniqueViewCount7d(uniqueViewCount7d)
                .viewCount(service.getViewCount() != null ? service.getViewCount() : 0L)
                .apiViewCount(service.getApiViewCount() != null ? service.getApiViewCount() : 0L)
                .rankingScore(score)
                .build();
    }

    private static String resolveUnifiedCategory(WelfareService service,
                                                 RecommendationCandidateProjection projection) {
        if (projection != null && projection.unifiedCategoryCompat() != null) {
            return projection.unifiedCategoryCompat();
        }
        return service.getUnifiedCategory();
    }
}
