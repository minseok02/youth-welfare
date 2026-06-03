package com.example.welfare.policy.dto;

import com.example.welfare.policy.entity.ServiceRegion;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.entity.WelfareServiceDetail;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class PolicyDetailResponse {

    private Long id;
    private String title;
    private String description;
    private String unifiedCategory;
    private String status;
    private String sourceType;
    private String hostOrg;
    private String operatingOrg;
    private String providerName;
    private Integer minAge;
    private Integer maxAge;
    private Integer minIncome;
    private Integer maxIncome;
    private String supportContent;
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
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate applyStartDate;
    private LocalDate applyEndDate;
    private String applicationPeriod;
    private String statusLabel;
    private String lifeStage;
    private String detailUrl;
    private String supportCycle;
    private String provisionType;
    private Boolean isOnlineApply;
    private Integer viewCount;
    private boolean bookmarked;

    // 상세 정보
    private String targetDetail;
    private String supportDetail;
    private String applyMethodDetail;
    private String selectionCriteria;
    private String contactList;
    private String homepageUrl;
    private String relatedLaw;
    private String formFiles;
    private String referenceUrlsJson;

    // 관련 목록
    private String regionLabel;
    private List<String> regions;
    private List<TagItem> tags;

    @Getter
    @Builder
    public static class TagItem {
        private String tagType;
        private String tagValue;
    }

    public static PolicyDetailResponse of(WelfareService ws,
                                           WelfareServiceDetail detail,
                                           List<ServiceRegion> regions,
                                           List<ServiceTag> tags,
                                           boolean bookmarked) {
        return of(ws, detail, regions, tags, bookmarked, null);
    }

    public static PolicyDetailResponse of(WelfareService ws,
                                          WelfareServiceDetail detail,
                                          List<ServiceRegion> regions,
                                          List<ServiceTag> tags,
                                          boolean bookmarked,
                                          RecommendationCandidateProjection projection) {
        List<String> regionNames = (regions != null ? regions : List.<ServiceRegion>of()).stream()
                .map(r -> r.getSidoName() != null
                        ? r.getSidoName() + (r.getSggName() != null ? " " + r.getSggName() : "")
                        : r.getRegionCode())
                .collect(Collectors.toList());

        List<TagItem> tagItems = (tags != null ? tags : List.<ServiceTag>of()).stream()
                .map(t -> TagItem.builder()
                        .tagType(t.getTagType().name())
                        .tagValue(t.getTagValue())
                        .build())
                .collect(Collectors.toList());
        String regionLabel = regionNames.isEmpty() ? null : regionNames.get(0);

        return PolicyDetailResponse.builder()
                .id(ws.getId())
                .title(ws.getTitle())
                .description(ws.getDescription())
                .unifiedCategory(resolveUnifiedCategory(ws, projection))
                .status(ws.getStatus().name())
                .sourceType(ws.getSourceType().name())
                .hostOrg(ws.getHostOrg())
                .operatingOrg(ws.getOperatingOrg())
                .providerName(resolveProviderName(ws))
                .minAge(ws.getMinAge())
                .maxAge(ws.getMaxAge())
                .minIncome(ws.getMinIncome())
                .maxIncome(ws.getMaxIncome())
                .supportContent(ws.getSupportContent())
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
                .startDate(ws.getStartDate())
                .endDate(ws.getEndDate())
                .applyStartDate(ws.getApplyStartDate())
                .applyEndDate(ws.getApplyEndDate())
                .applicationPeriod(resolveApplicationPeriod(ws))
                .statusLabel(resolveStatusLabel(ws))
                .lifeStage(ws.getLifeStage())
                .detailUrl(ws.getDetailUrl())
                .supportCycle(detail != null && detail.getSupportCycle() != null ? detail.getSupportCycle() : ws.getSupportCycle())
                .provisionType(detail != null && detail.getProvisionType() != null ? detail.getProvisionType() : ws.getProvisionType())
                .isOnlineApply(ws.getIsOnlineApply())
                .viewCount(ws.getViewCount())
                .bookmarked(bookmarked)
                .targetDetail(detail != null ? detail.getTargetDetail() : null)
                .supportDetail(detail != null ? detail.getSupportDetail() : null)
                .applyMethodDetail(detail != null ? detail.getApplyMethodDetail() : null)
                .selectionCriteria(detail != null ? detail.getSelectionCriteria() : null)
                .contactList(detail != null ? detail.getContactList() : null)
                .homepageUrl(detail != null ? detail.getHomepageUrl() : null)
                .relatedLaw(detail != null ? detail.getRelatedLaw() : null)
                .formFiles(detail != null ? detail.getFormFiles() : null)
                .referenceUrlsJson(detail != null ? detail.getReferenceUrlsJson() : null)
                .regionLabel(regionLabel)
                .regions(regionNames)
                .tags(tagItems)
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
                .map(PolicyDetailResponse::normalizeBlank)
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
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
