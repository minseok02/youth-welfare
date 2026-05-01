package com.example.welfare.recommend.support;

import com.example.welfare.collect.support.NormalizationKeySupport;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * recommendation projection 조립에 쓰이는 heuristic 규칙과 bucket 이름을 공통화한다.
 */
public final class RecommendationProjectionHeuristicSupport {

    public static final String BENEFICIARY_SUPPORT_BUCKET = "BENEFICIARY_SUPPORT";
    public static final String SPECIAL_TARGET_RURAL = "농어촌";
    public static final String SPECIAL_TARGET_SELF_RELIANCE = "자립준비청년";
    public static final String SPECIAL_TARGET_FAMILY_CARE = "가족돌봄";
    public static final String SPECIAL_TARGET_MULTICULTURAL = "다문화";
    public static final String SPECIAL_TARGET_DEFECTOR = "북한이탈";
    public static final String SPECIAL_TARGET_SINGLE_PARENT = "한부모";
    public static final String SPECIAL_TARGET_GRANDPARENT = "조손";
    public static final String SPECIAL_TARGET_VETERAN = "보훈";
    public static final String SPECIAL_TARGET_DISABILITY = "장애";
    public static final String SPECIAL_TARGET_MILITARY = "병역";

    private static final Set<String> BENEFICIARY_TERMS = Set.of("기초생활수급자", "차상위계층");
    private static final int YOUTH_MIN_AGE = 18;
    private static final int YOUTH_MAX_AGE = 39;
    private static final double EXPLICIT_YOUTH_BONUS = 15.0;
    private static final double FOCUSED_LIFE_STAGE_BONUS = 8.0;
    private static final double AGE_RANGE_ONLY_BONUS = 3.0;
    private static final Set<String> YOUTH_SIGNALS = Set.of(
            "청년",
            "미취업청년",
            "취업준비생",
            "사회초년생",
            "대학생",
            "대학원생",
            "청년층"
    );
    private static final Set<String> BROAD_LIFE_STAGE_SIGNALS = Set.of(
            "영유아",
            "아동",
            "청소년",
            "중장년",
            "노년",
            "임신",
            "출산"
    );

    private RecommendationProjectionHeuristicSupport() {
    }

    public static boolean isBeneficiaryDetailTerm(String termLabel, String sourceField) {
        return BENEFICIARY_TERMS.contains(termLabel)
                && NormalizationKeySupport.sourceFieldContains(sourceField, NormalizationKeySupport.SOURCE_FIELD_TARGET_DETAIL)
                && NormalizationKeySupport.sourceFieldContains(sourceField, NormalizationKeySupport.SOURCE_FIELD_SELECTION_CRITERIA);
    }

    public static double audienceRelevanceBonus(String title,
                                                String summary,
                                                Set<String> targetGroupsRaw,
                                                Set<String> keywordTags,
                                                Set<String> interestThemes,
                                                Set<String> lifeStages,
                                                Integer minAge,
                                                Integer maxAge) {
        boolean explicitYouthSignal = audienceTextSignals(title, summary, targetGroupsRaw, keywordTags, interestThemes, lifeStages).stream()
                .anyMatch(RecommendationProjectionHeuristicSupport::containsYouthSignal);
        boolean focusedLifeStage = hasYouthFocusedLifeStage(lifeStages);
        boolean youthFocusedAgeRange = isYouthFocusedAgeRange(minAge, maxAge);

        double bonus = 0.0;
        if (explicitYouthSignal) {
            bonus += EXPLICIT_YOUTH_BONUS;
        }
        if (focusedLifeStage) {
            bonus += FOCUSED_LIFE_STAGE_BONUS;
        }
        if (!explicitYouthSignal && !focusedLifeStage && youthFocusedAgeRange) {
            bonus += AGE_RANGE_ONLY_BONUS;
        }
        return bonus;
    }

    public static void collectSpecialTargetBuckets(Set<String> buckets, String raw) {
        String normalized = normalize(raw);
        if (normalized == null || normalized.isBlank()) {
            return;
        }
        if (normalized.contains("장애")) {
            buckets.add(SPECIAL_TARGET_DISABILITY);
        }
        if (normalized.contains("농어촌") || normalized.contains("농촌") || normalized.contains("어촌")) {
            buckets.add(SPECIAL_TARGET_RURAL);
        }
        if (normalized.contains("자립준비") || normalized.contains("보호종료")) {
            buckets.add(SPECIAL_TARGET_SELF_RELIANCE);
        }
        if (normalized.contains("가족돌봄")) {
            buckets.add(SPECIAL_TARGET_FAMILY_CARE);
        }
        if (normalized.contains("다문화")) {
            buckets.add(SPECIAL_TARGET_MULTICULTURAL);
        }
        if (normalized.contains("북한이탈")) {
            buckets.add(SPECIAL_TARGET_DEFECTOR);
        }
        if (normalized.contains("한부모")) {
            buckets.add(SPECIAL_TARGET_SINGLE_PARENT);
        }
        if (normalized.contains("조손")) {
            buckets.add(SPECIAL_TARGET_GRANDPARENT);
        }
        if (normalized.contains("보훈")) {
            buckets.add(SPECIAL_TARGET_VETERAN);
        }
        if (normalized.contains("현역병") || normalized.contains("병역")) {
            buckets.add(SPECIAL_TARGET_MILITARY);
        }
    }

    public static boolean educationPriorityBoostEligible(String unifiedCategoryCompat, String youthMajorLabel) {
        return "기타".equals(unifiedCategoryCompat) && "교육".equals(youthMajorLabel);
    }

    private static List<String> audienceTextSignals(String title,
                                                    String summary,
                                                    Set<String> targetGroupsRaw,
                                                    Set<String> keywordTags,
                                                    Set<String> interestThemes,
                                                    Set<String> lifeStages) {
        List<String> signals = new ArrayList<>();
        if (title != null) {
            signals.add(title);
        }
        if (summary != null) {
            signals.add(summary);
        }
        signals.addAll(emptyIfNull(targetGroupsRaw));
        signals.addAll(emptyIfNull(keywordTags));
        signals.addAll(emptyIfNull(interestThemes));
        signals.addAll(emptyIfNull(lifeStages));
        return signals;
    }

    private static boolean hasYouthFocusedLifeStage(Set<String> lifeStages) {
        if (lifeStages == null || lifeStages.isEmpty()) {
            return false;
        }
        boolean hasYouth = lifeStages.stream().anyMatch(RecommendationProjectionHeuristicSupport::containsYouthSignal);
        boolean hasBroadOtherStage = lifeStages.stream()
                .map(RecommendationProjectionHeuristicSupport::normalize)
                .filter(value -> value != null)
                .anyMatch(value -> BROAD_LIFE_STAGE_SIGNALS.stream().anyMatch(value::contains));
        return hasYouth && !hasBroadOtherStage;
    }

    private static boolean isYouthFocusedAgeRange(Integer minAge, Integer maxAge) {
        if (minAge == null && maxAge == null) {
            return false;
        }
        if (maxAge == null) {
            return false;
        }
        int effectiveMin = minAge != null ? minAge : 0;
        return effectiveMin <= YOUTH_MAX_AGE && maxAge <= YOUTH_MAX_AGE && maxAge >= YOUTH_MIN_AGE;
    }

    private static boolean containsYouthSignal(String raw) {
        String normalized = normalize(raw);
        return normalized != null
                && YOUTH_SIGNALS.stream()
                .map(RecommendationProjectionHeuristicSupport::normalize)
                .anyMatch(normalized::contains);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static Set<String> emptyIfNull(Set<String> values) {
        return values == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(values));
    }
}
