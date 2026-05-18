package com.example.welfare.collect.support;

import com.example.welfare.collect.validation.RawFieldValidator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * canonical normalization 단계에서 재사용되는 term/fact 식별자를 한 곳에 모은다.
 */
public final class NormalizationKeySupport {

    public static final String SUMMARY_KEY_YOUTH_MAJOR = "YOUTH_MAJOR";
    public static final String SUMMARY_KEY_YOUTH_MID = "YOUTH_MID";
    public static final String SUMMARY_KEY_GOV24_SERVICE_FIELD = "GOV24_SERVICE_FIELD";
    public static final String SUMMARY_KEY_GOV24_USER_TYPE = "GOV24_USER_TYPE";
    public static final String SUMMARY_KEY_GOV24_BENEFIT_TYPE = "GOV24_BENEFIT_TYPE";

    public static final String TERM_GROUP_YOUTH_MAJOR = "YOUTH_MAJOR";
    public static final String TERM_GROUP_YOUTH_MID = "YOUTH_MID";
    public static final String TERM_GROUP_YOUTH_MID_RAW_ALIAS = "YOUTH_MID_RAW_ALIAS";
    public static final String TERM_GROUP_YOUTH_KEYWORD = "YOUTH_KEYWORD";
    public static final String TERM_GROUP_LIFE_STAGE = "LIFE_STAGE";
    public static final String TERM_GROUP_INTEREST_THEME = "INTEREST_THEME";
    public static final String TERM_GROUP_TARGET_GROUP = "TARGET_GROUP";

    public static final String FACT_GROUP_AGE = "AGE";
    public static final String FACT_GROUP_INCOME = "INCOME";
    public static final String FACT_GROUP_APPLY_END_DATE = "APPLY_END_DATE";

    public static final String FACT_CODE_YOUTH_AGE = "YOUTH_AGE";
    public static final String FACT_CODE_YOUTH_INCOME_MIN = "YOUTH_INCOME_MIN";
    public static final String FACT_CODE_YOUTH_INCOME_MAX = "YOUTH_INCOME_MAX";
    public static final String FACT_CODE_YOUTH_APPLY_END_DATE = "YOUTH_APPLY_END_DATE";
    public static final String FACT_CODE_YOUTH_INCOME_CONDITION_TYPE = "YOUTH_INCOME_CONDITION_TYPE";
    public static final String FACT_CODE_BOKJIRO_AGE = "BOKJIRO_RULE_AGE";
    public static final String FACT_CODE_BOKJIRO_APPLY_END_DATE = "BOKJIRO_RULE_APPLY_END_DATE";

    public static final String FACT_MERGE_KEY_YOUTH_AGE = "YOUTH_AGE_ELIGIBILITY";
    public static final String FACT_MERGE_KEY_YOUTH_INCOME_MIN = "YOUTH_INCOME_MIN";
    public static final String FACT_MERGE_KEY_YOUTH_INCOME_MAX = "YOUTH_INCOME_MAX";
    public static final String FACT_MERGE_KEY_YOUTH_APPLY_END_DATE = "YOUTH_APPLY_END_DATE";
    public static final String FACT_MERGE_KEY_YOUTH_INCOME_CONDITION_TYPE = "YOUTH_INCOME_CONDITION_TYPE";
    public static final String FACT_MERGE_KEY_BOKJIRO_AGE = "BK_AGE_ELIGIBILITY";
    public static final String FACT_MERGE_KEY_BOKJIRO_APPLY_END_DATE = "BK_APPLY_END_DATE";

    public static final String SOURCE_FIELD_YOUTH_MAJOR = "lclsfNm";
    public static final String SOURCE_FIELD_YOUTH_CATEGORY_SUB = "category_sub";
    public static final String SOURCE_FIELD_YOUTH_KEYWORD = "plcyKywdNm";
    public static final String SOURCE_FIELD_YOUTH_AGE = "sprtTrgtMinAge/sprtTrgtMaxAge";
    public static final String SOURCE_FIELD_YOUTH_INCOME_MIN = "earnMinAmt";
    public static final String SOURCE_FIELD_YOUTH_INCOME_MAX = "earnMaxAmt";
    public static final String SOURCE_FIELD_YOUTH_INCOME_CONDITION_TYPE = "earnCndSeCd";
    public static final String SOURCE_FIELD_YOUTH_APPLY_END_DATE = "aplyYmd";
    public static final String SOURCE_FIELD_BOKJIRO_DIGEST = "servDgst";
    public static final String SOURCE_FIELD_BOKJIRO_LIFE_ARRAY = "lifeArray";
    public static final String SOURCE_FIELD_BOKJIRO_INTEREST_THEME_ARRAY = "intrsThemaArray";
    public static final String SOURCE_FIELD_BOKJIRO_TARGET_GROUP_ARRAY = "trgterIndvdlArray";
    public static final String SOURCE_FIELD_BOKJIRO_LIFE_NM_ARRAY = "lifeNmArray";
    public static final String SOURCE_FIELD_BOKJIRO_INTEREST_THEME_NM_ARRAY = "intrsThemaNmArray";
    public static final String SOURCE_FIELD_BOKJIRO_TARGET_GROUP_NM_ARRAY = "trgterIndvdlNmArray";
    public static final String SOURCE_FIELD_TARGET_DETAIL = "targetDetail";
    public static final String SOURCE_FIELD_SELECTION_CRITERIA = "selectionCriteria";
    public static final String SOURCE_FIELD_APPLY_METHOD_DETAIL = "applyMethodDetail";
    public static final String SOURCE_FIELD_SUPPORT_DETAIL = "supportDetail";
    public static final String SOURCE_FIELD_TARGET_DETAIL_SELECTION_CRITERIA = "targetDetail/selectionCriteria";
    public static final String SOURCE_FIELD_APPLY_METHOD_DETAIL_SUPPORT_DETAIL = "applyMethodDetail/supportDetail";

    private static final Set<String> OFFICIAL_YOUTH_MID_LABELS = Set.of(
            "취업",
            "재직자",
            "창업",
            "주택 및 거주지",
            "기숙사",
            "전월세 및 주거급여 지원",
            "미래역량강화",
            "교육비지원",
            "온라인교육",
            "취약계층 및 금융지원",
            "건강",
            "예술인지원",
            "문화활동",
            "청년참여",
            "정책인프라구축",
            "청년국제교류",
            "권익보호"
    );
    private static final Map<String, Integer> SOURCE_FIELD_PRIORITY = sourceFieldPriorityMap();

    private NormalizationKeySupport() {
    }

    public static boolean isOfficialYouthMidLabel(String label) {
        String normalized = RawFieldValidator.normalize(label);
        return normalized != null && OFFICIAL_YOUTH_MID_LABELS.contains(normalized);
    }

    public static List<String> refreshScopeGroups(String termGroup) {
        if (TERM_GROUP_YOUTH_MID.equals(termGroup) || TERM_GROUP_YOUTH_MID_RAW_ALIAS.equals(termGroup)) {
            return List.of(TERM_GROUP_YOUTH_MID, TERM_GROUP_YOUTH_MID_RAW_ALIAS);
        }
        return List.of(termGroup);
    }

    public static int sourceFieldPriority(String sourceField) {
        if (sourceField == null || sourceField.isBlank()) {
            return Integer.MAX_VALUE;
        }

        int priority = Integer.MAX_VALUE;
        for (String token : sourceField.split("/")) {
            String normalized = token == null ? "" : token.strip();
            priority = Math.min(priority, SOURCE_FIELD_PRIORITY.getOrDefault(normalized, Integer.MAX_VALUE));
        }
        return priority;
    }

    public static boolean sourceFieldContains(String sourceField, String token) {
        if (sourceField == null || sourceField.isBlank() || token == null || token.isBlank()) {
            return false;
        }
        String normalizedToken = token.strip();
        for (String rawToken : sourceField.split("/")) {
            String normalized = rawToken == null ? "" : rawToken.strip();
            if (normalizedToken.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Integer> sourceFieldPriorityMap() {
        LinkedHashMap<String, Integer> priorities = new LinkedHashMap<>();
        priorities.put(SOURCE_FIELD_TARGET_DETAIL, 0);
        priorities.put(SOURCE_FIELD_SELECTION_CRITERIA, 1);
        priorities.put(SOURCE_FIELD_BOKJIRO_DIGEST, 2);
        priorities.put(SOURCE_FIELD_APPLY_METHOD_DETAIL, 3);
        priorities.put(SOURCE_FIELD_SUPPORT_DETAIL, 4);
        return Map.copyOf(priorities);
    }
}
