package com.example.welfare.policy.dto;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Getter
@Builder
@JsonDeserialize(builder = PolicySummaryResponse.PolicySummaryResponseBuilder.class)
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
    private String providerName;
    private String regionLabel;
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
    private String applicationPeriod;
    private String statusLabel;
    private Boolean isOnlineApply;
    private Long apiViewCount;
    private Integer viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime registeredAt;
    private LocalDateTime lastModifiedAt;
    private boolean bookmarked;

    @JsonPOJOBuilder(withPrefix = "")
    public static class PolicySummaryResponseBuilder {
    }

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
                                             String regionLabel) {
        String normalizedRegionLabel = normalizeBlank(regionLabel);
        return PolicySummaryResponse.builder()
                .id(ws.getId())
                .title(ws.getTitle())
                .description(resolveDescription(ws, projection))
                .unifiedCategory(resolveUnifiedCategory(ws, projection))
                .status(ws.getStatus().name())
                .sourceType(ws.getSourceType().name())
                .hostOrg(ws.getHostOrg())
                .operatingOrg(ws.getOperatingOrg())
                .sido(resolveSido(normalizedRegionLabel))
                .providerName(resolveProviderName(ws))
                .regionLabel(normalizedRegionLabel)
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
                .applicationPeriod(resolveApplicationPeriod(ws))
                .statusLabel(resolveStatusLabel(ws))
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

    private static String resolveProviderName(WelfareService ws) {
        return Arrays.stream(new String[]{ws.getHostOrg(), ws.getOperatingOrg()})
                .map(PolicySummaryResponse::normalizeBlank)
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }

    private static String resolveSido(String regionLabel) {
        String normalized = normalizeBlank(regionLabel);
        if (normalized == null) {
            return null;
        }
        int separator = normalized.indexOf(' ');
        return separator > 0 ? normalized.substring(0, separator) : normalized;
    }

    private static String resolveApplicationPeriod(WelfareService ws) {
        String applyPeriod = formatPeriod(ws.getApplyStartDate(), ws.getApplyEndDate());
        return applyPeriod != null ? applyPeriod : formatPeriod(ws.getStartDate(), ws.getEndDate());
    }

    private static String resolveStatusLabel(WelfareService ws) {
        if (ws.getStatus() == WelfareService.ServiceStatus.CLOSED) {
            return "종료";
        }
        if (ws.getApplyEndDate() != null && ws.getApplyEndDate().isBefore(LocalDate.now())) {
            return "종료";
        }
        if (ws.getStatus() == WelfareService.ServiceStatus.ACTIVE) {
            return "진행중";
        }
        if (ws.getStatus() == WelfareService.ServiceStatus.UPCOMING) {
            return "예정";
        }
        return "상태 정보 없음";
    }

    private static String formatPeriod(LocalDate start, LocalDate end) {
        if (start != null && end != null) {
            return "%s ~ %s".formatted(start, end);
        }
        if (start != null) {
            return "%s ~".formatted(start);
        }
        if (end != null) {
            return "~ %s".formatted(end);
        }
        return null;
    }

    private static String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
