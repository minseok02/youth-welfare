package com.example.welfare.policy.dto;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonDeserialize(builder = PolicyRankingResponse.PolicyRankingResponseBuilder.class)
public class PolicyRankingResponse {

    private Long serviceId;
    private String title;
    private String unifiedCategory;
    private String youthMajorLabel;
    private String youthMidLabel;
    private String provisionMethodLabel;
    private String gov24ServiceFieldLabel;
    private String gov24UserTypeLabel;
    private String gov24BenefitTypeLabel;
    private String sourceType;
    private Long uniqueViewCount7d;
    private Long viewCount;
    private Long apiViewCount;
    private double rankingScore;

    @JsonPOJOBuilder(withPrefix = "")
    public static class PolicyRankingResponseBuilder {
    }

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
                .youthMajorLabel(resolveYouthMajorLabel(projection))
                .youthMidLabel(resolveYouthMidLabel(projection))
                .provisionMethodLabel(resolveProvisionMethodLabel(projection))
                .gov24ServiceFieldLabel(resolveGov24ServiceFieldLabel(projection))
                .gov24UserTypeLabel(resolveGov24UserTypeLabel(projection))
                .gov24BenefitTypeLabel(resolveGov24BenefitTypeLabel(projection))
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

    private static String resolveYouthMidLabel(RecommendationCandidateProjection projection) {
        return projection != null ? projection.youthMidLabel() : null;
    }

    private static String resolveYouthMajorLabel(RecommendationCandidateProjection projection) {
        return projection != null ? projection.youthMajorLabel() : null;
    }

    private static String resolveProvisionMethodLabel(RecommendationCandidateProjection projection) {
        return projection != null ? projection.provisionMethodLabel() : null;
    }

    private static String resolveGov24ServiceFieldLabel(RecommendationCandidateProjection projection) {
        return projection != null ? projection.gov24ServiceFieldLabel() : null;
    }

    private static String resolveGov24UserTypeLabel(RecommendationCandidateProjection projection) {
        return projection != null ? projection.gov24UserTypeLabel() : null;
    }

    private static String resolveGov24BenefitTypeLabel(RecommendationCandidateProjection projection) {
        return projection != null ? projection.gov24BenefitTypeLabel() : null;
    }
}
