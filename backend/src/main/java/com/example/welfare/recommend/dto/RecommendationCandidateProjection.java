package com.example.welfare.recommend.dto;

import lombok.Builder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * canonical sidecar 기반 추천 전용 read-model projection 초안.
 * persistence raw term과 scoring-friendly bucket을 같이 보관하되,
 * 실제 추천 경로 연결은 후속 task에서 진행한다.
 */
@Builder
public record RecommendationCandidateProjection(
        Long serviceId,
        String sourceType,
        String unifiedCategoryCompat,
        String compatCategoryCode,
        String compatPriorityBucket,
        String youthMajorLabel,
        String youthMidLabel,
        String provisionMethodLabel,
        String gov24ServiceFieldLabel,
        String gov24UserTypeLabel,
        String gov24BenefitTypeLabel,
        List<String> gov24UserTypeTokens,
        List<String> gov24BenefitTypeTokens,
        List<String> youthEmploymentRequirementCodes,
        List<String> youthEmploymentRequirementLabels,
        List<String> youthSpecialRequirementCodes,
        List<String> youthSpecialRequirementLabels,
        String youthIncomeConditionTypeCode,
        String youthIncomeConditionTypeLabel,
        String title,
        String summary,
        Integer minAge,
        Integer maxAge,
        Integer incomeMinLegacy,
        Integer incomeMaxLegacy,
        LocalDate applyEndDate,
        boolean youthRelevant,
        double audienceRelevanceBonus,
        boolean educationPriorityBoostEligible,
        Set<String> priorityBuckets,
        Set<String> interestThemes,
        Set<String> targetGroupsRaw,
        Set<String> targetGroupBuckets,
        Set<String> lifeStages,
        Set<String> keywordTags,
        Set<String> beneficiaryTerms,
        Set<String> specialTargetBuckets,
        Set<String> factKeys
) {

    public RecommendationCandidateProjection {
        gov24UserTypeTokens = immutableListCopy(gov24UserTypeTokens);
        gov24BenefitTypeTokens = immutableListCopy(gov24BenefitTypeTokens);
        youthEmploymentRequirementCodes = immutableListCopy(youthEmploymentRequirementCodes);
        youthEmploymentRequirementLabels = immutableListCopy(youthEmploymentRequirementLabels);
        youthSpecialRequirementCodes = immutableListCopy(youthSpecialRequirementCodes);
        youthSpecialRequirementLabels = immutableListCopy(youthSpecialRequirementLabels);
        priorityBuckets = immutableCopy(priorityBuckets);
        interestThemes = immutableCopy(interestThemes);
        targetGroupsRaw = immutableCopy(targetGroupsRaw);
        targetGroupBuckets = immutableCopy(targetGroupBuckets);
        lifeStages = immutableCopy(lifeStages);
        keywordTags = immutableCopy(keywordTags);
        beneficiaryTerms = immutableCopy(beneficiaryTerms);
        specialTargetBuckets = immutableCopy(specialTargetBuckets);
        factKeys = immutableCopy(factKeys);
    }

    private static Set<String> immutableCopy(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(new LinkedHashSet<>(values));
    }

    private static List<String> immutableListCopy(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return List.copyOf(new ArrayList<>(values));
    }
}
