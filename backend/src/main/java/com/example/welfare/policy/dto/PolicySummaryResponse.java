package com.example.welfare.policy.dto;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class PolicySummaryResponse {

    private Long id;
    private String title;
    private String description;
    private String unifiedCategory;
    private String status;
    private String hostOrg;
    private Integer minAge;
    private Integer maxAge;
    private String applyMethodName;
    private String youthMajorLabel;
    private String youthMidLabel;
    private String provisionMethodLabel;
    private String gov24ServiceFieldLabel;
    private String gov24UserTypeLabel;
    private String gov24BenefitTypeLabel;
    private LocalDate applyStartDate;
    private LocalDate applyEndDate;
    private Boolean isOnlineApply;
    private boolean bookmarked;

    public static PolicySummaryResponse from(WelfareService ws, boolean bookmarked) {
        return from(ws, bookmarked, null);
    }

    public static PolicySummaryResponse from(WelfareService ws,
                                             boolean bookmarked,
                                             RecommendationCandidateProjection projection) {
        return PolicySummaryResponse.builder()
                .id(ws.getId())
                .title(ws.getTitle())
                .description(ws.getDescription())
                .unifiedCategory(resolveUnifiedCategory(ws, projection))
                .status(ws.getStatus().name())
                .hostOrg(ws.getHostOrg())
                .minAge(ws.getMinAge())
                .maxAge(ws.getMaxAge())
                .applyMethodName(ws.getApplyMethodName())
                .youthMajorLabel(resolveYouthMajorLabel(projection))
                .youthMidLabel(resolveYouthMidLabel(projection))
                .provisionMethodLabel(resolveProvisionMethodLabel(projection))
                .gov24ServiceFieldLabel(resolveGov24ServiceFieldLabel(projection))
                .gov24UserTypeLabel(resolveGov24UserTypeLabel(projection))
                .gov24BenefitTypeLabel(resolveGov24BenefitTypeLabel(projection))
                .applyStartDate(ws.getApplyStartDate())
                .applyEndDate(ws.getApplyEndDate())
                .isOnlineApply(ws.getIsOnlineApply())
                .bookmarked(bookmarked)
                .build();
    }

    private static String resolveUnifiedCategory(WelfareService ws,
                                                 RecommendationCandidateProjection projection) {
        if (projection != null && projection.unifiedCategoryCompat() != null) {
            return projection.unifiedCategoryCompat();
        }
        return ws.getUnifiedCategory();
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
