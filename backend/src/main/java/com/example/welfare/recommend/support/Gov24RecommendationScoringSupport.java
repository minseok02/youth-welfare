package com.example.welfare.recommend.support;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.PriorityPreference;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class Gov24RecommendationScoringSupport {

    private static final double SERVICE_FIELD_MATCH_BONUS = 4.0;
    private static final double BENEFIT_TYPE_MATCH_BONUS = 3.0;
    private static final double INDIVIDUAL_USER_TYPE_BONUS = 2.0;
    private static final double HOUSEHOLD_USER_TYPE_BONUS = 1.0;
    private static final double MAX_TOTAL_BONUS = 6.0;

    private static final Set<String> CATEGORY_PRIORITY_CODES = Set.of(
            "HOUSING",
            "JOB",
            "EDUCATION",
            "FINANCE",
            "CULTURE",
            "PARTICIPATION",
            "FAMILY"
    );

    private static final Map<String, String> SERVICE_FIELD_PRIORITY_CODES = Map.of(
            "주거·자립", "HOUSING",
            "고용·창업", "JOB",
            "보육·교육", "EDUCATION",
            "생활안정", "FINANCE",
            "문화·환경", "CULTURE",
            "보호·돌봄", "FAMILY",
            "임신·출산", "FAMILY"
    );

    private static final Map<String, String> BENEFIT_TYPE_PRIORITY_CODES = Map.ofEntries(
            Map.entry("현금", "FINANCE"),
            Map.entry("현금(감면)", "FINANCE"),
            Map.entry("현금(보험)", "FINANCE"),
            Map.entry("현금(융자)", "FINANCE"),
            Map.entry("현금(장학금)", "EDUCATION"),
            Map.entry("기타(교육)", "EDUCATION"),
            Map.entry("서비스(일자리)", "JOB"),
            Map.entry("기술지원", "JOB"),
            Map.entry("문화/여가지원", "CULTURE"),
            Map.entry("서비스(돌봄)", "FAMILY")
    );

    private Gov24RecommendationScoringSupport() {
    }

    public static double softBonus(WelfareService service,
                                   RecommendationUserSnapshot user,
                                   RecommendationCandidateProjection projection) {
        if (service == null || service.getSourceType() != WelfareService.SourceType.GOV24 || projection == null) {
            return 0.0;
        }

        Set<String> priorityCodes = user.priorities().stream()
                .map(PriorityPreference::code)
                .filter(CATEGORY_PRIORITY_CODES::contains)
                .collect(Collectors.toSet());

        double categoryBonus = Math.max(
                resolveServiceFieldBonus(priorityCodes, projection.gov24ServiceFieldLabel()),
                resolveBenefitTypeBonus(priorityCodes, projection.gov24BenefitTypeTokens())
        );
        double audienceBonus = resolveUserTypeBonus(user, projection.gov24UserTypeTokens());

        return Math.min(MAX_TOTAL_BONUS, categoryBonus + audienceBonus);
    }

    private static double resolveServiceFieldBonus(Set<String> priorityCodes, String serviceFieldLabel) {
        if (priorityCodes.isEmpty() || serviceFieldLabel == null || serviceFieldLabel.isBlank()) {
            return 0.0;
        }
        String mappedPriorityCode = SERVICE_FIELD_PRIORITY_CODES.get(serviceFieldLabel);
        return mappedPriorityCode != null && priorityCodes.contains(mappedPriorityCode)
                ? SERVICE_FIELD_MATCH_BONUS
                : 0.0;
    }

    private static double resolveBenefitTypeBonus(Set<String> priorityCodes, List<String> benefitTypeTokens) {
        if (priorityCodes.isEmpty() || benefitTypeTokens == null || benefitTypeTokens.isEmpty()) {
            return 0.0;
        }
        return benefitTypeTokens.stream()
                .map(BENEFIT_TYPE_PRIORITY_CODES::get)
                .filter(priorityCodes::contains)
                .findFirst()
                .map(ignored -> BENEFIT_TYPE_MATCH_BONUS)
                .orElse(0.0);
    }

    private static double resolveUserTypeBonus(RecommendationUserSnapshot user, List<String> userTypeTokens) {
        if (userTypeTokens == null || userTypeTokens.isEmpty()) {
            return 0.0;
        }
        if (userTypeTokens.contains("개인")) {
            return INDIVIDUAL_USER_TYPE_BONUS;
        }
        if (userTypeTokens.contains("가구") && user.householdType() != null && !user.householdType().isBlank()) {
            return HOUSEHOLD_USER_TYPE_BONUS;
        }
        return 0.0;
    }
}
