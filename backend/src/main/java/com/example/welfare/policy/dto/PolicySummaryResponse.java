package com.example.welfare.policy.dto;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class PolicySummaryResponse {

    private Long id;
    private String title;
    private String description;
    private String unifiedCategory;
    private String status;
    private String sourceType;
    private String hostOrg;
    private String operatingOrg;
    // 복지로 지자체 정책 중 hostOrg가 없는 경우 카드 source 표시에 사용 (service_regions.sido_name)
    private String sido;
    private Integer minAge;
    private Integer maxAge;
    private String applyMethodName;
    private String youthMajorLabel;
    private String youthMidLabel;
    private String provisionMethodLabel;
    private List<String> youthEmploymentRequirementLabels;
    private List<String> youthEducationRequirementLabels;
    private List<String> youthSpecialRequirementLabels;
    private String youthMaritalStatusLabel;
    private String youthIncomeConditionTypeLabel;
    private String gov24ServiceFieldLabel;
    private String gov24UserTypeLabel;
    private String gov24BenefitTypeLabel;
    private LocalDate applyStartDate;
    private LocalDate applyEndDate;
    private Boolean isOnlineApply;
    private Long apiViewCount;
    private Integer viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime registeredAt;
    private LocalDateTime lastModifiedAt;
    private boolean bookmarked;

    public static PolicySummaryResponse from(WelfareService ws, boolean bookmarked) {
        return from(ws, bookmarked, null, null);
    }

    public static PolicySummaryResponse from(WelfareService ws,
                                             boolean bookmarked,
                                             RecommendationCandidateProjection projection) {
        return from(ws, bookmarked, projection, null);
    }

    public static PolicySummaryResponse from(WelfareService ws,
                                             boolean bookmarked,
                                             RecommendationCandidateProjection projection,
                                             String sido) {
        return PolicySummaryResponse.builder()
                .id(ws.getId())
                .title(ws.getTitle())
                .description(resolveDescription(ws, projection))
                .unifiedCategory(resolveUnifiedCategory(ws, projection))
                .status(ws.getStatus().name())
                .sourceType(ws.getSourceType().name())
                .hostOrg(ws.getHostOrg())
                .operatingOrg(ws.getOperatingOrg())
                .sido(sido)
                .minAge(ws.getMinAge())
                .maxAge(ws.getMaxAge())
                .applyMethodName(ws.getApplyMethodName())
                .youthMajorLabel(resolveYouthMajorLabel(projection))
                .youthMidLabel(resolveYouthMidLabel(projection))
                .provisionMethodLabel(resolveProvisionMethodLabel(projection))
                .youthEmploymentRequirementLabels(resolveYouthEmploymentRequirementLabels(projection))
                .youthEducationRequirementLabels(resolveYouthEducationRequirementLabels(projection))
                .youthSpecialRequirementLabels(resolveYouthSpecialRequirementLabels(projection))
                .youthMaritalStatusLabel(resolveYouthMaritalStatusLabel(projection))
                .youthIncomeConditionTypeLabel(resolveYouthIncomeConditionTypeLabel(projection))
                .gov24ServiceFieldLabel(resolveGov24ServiceFieldLabel(projection))
                .gov24UserTypeLabel(resolveGov24UserTypeLabel(projection))
                .gov24BenefitTypeLabel(resolveGov24BenefitTypeLabel(projection))
                .applyStartDate(ws.getApplyStartDate())
                .applyEndDate(ws.getApplyEndDate())
                .isOnlineApply(ws.getIsOnlineApply())
                .apiViewCount(ws.getApiViewCount())
                .viewCount(ws.getViewCount())
                .createdAt(ws.getCreatedAt())
                .registeredAt(ws.getRegisteredAt())
                .lastModifiedAt(ws.getLastModifiedAt())
                .bookmarked(bookmarked)
                .build();
    }

    private static String resolveDescription(WelfareService ws,
                                             RecommendationCandidateProjection projection) {
        if (projection != null && projection.summary() != null && !projection.summary().isBlank()) {
            return projection.summary();
        }
        if (ws.getSupportContent() != null && !ws.getSupportContent().isBlank()) {
            return ws.getSupportContent();
        }
        return ws.getDescription();
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

    private static List<String> resolveYouthEmploymentRequirementLabels(RecommendationCandidateProjection projection) {
        return projection != null ? projection.youthEmploymentRequirementLabels() : List.of();
    }

    private static List<String> resolveYouthEducationRequirementLabels(RecommendationCandidateProjection projection) {
        return projection != null ? projection.youthEducationRequirementLabels() : List.of();
    }

    private static List<String> resolveYouthSpecialRequirementLabels(RecommendationCandidateProjection projection) {
        return projection != null ? projection.youthSpecialRequirementLabels() : List.of();
    }

    private static String resolveYouthMaritalStatusLabel(RecommendationCandidateProjection projection) {
        return projection != null ? projection.youthMaritalStatusLabel() : null;
    }

    private static String resolveYouthIncomeConditionTypeLabel(RecommendationCandidateProjection projection) {
        return projection != null ? projection.youthIncomeConditionTypeLabel() : null;
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
