package com.example.welfare.policy.support;

import java.util.Map;

/**
 * legacy compat category label을 공통 코드/priority bucket 의미로 정규화한다.
 */
public final class CompatCategorySupport {

    private static final String COMPAT_OTHER_CODE = "OTHER";
    private static final Map<String, String> COMPAT_CODES = Map.of(
            "일자리", "JOB",
            "주거", "HOUSING",
            "교육·직업훈련", "EDUCATION_TRAINING",
            "금융·생활지원", "FINANCE_LIFE_SUPPORT",
            "문화·여가", "CULTURE_LEISURE",
            "건강·의료", "HEALTH_MEDICAL",
            "가족·돌봄", "FAMILY_CARE",
            "안전·위기", "SAFETY_CRISIS",
            "참여·기회", "PARTICIPATION_OPPORTUNITY"
    );
    private static final Map<String, String> PRIORITY_BUCKETS = Map.of(
            "일자리", "JOB",
            "주거", "HOUSING",
            "교육·직업훈련", "EDUCATION",
            "금융·생활지원", "FINANCE",
            "문화·여가", "CULTURE",
            "가족·돌봄", "FAMILY",
            "참여·기회", "PARTICIPATION"
    );

    private CompatCategorySupport() {
    }

    public static String compatCode(String compatLabel) {
        if (compatLabel == null || compatLabel.isBlank()) {
            return null;
        }
        return COMPAT_CODES.getOrDefault(compatLabel.trim(), COMPAT_OTHER_CODE);
    }

    public static String priorityBucket(String compatLabel) {
        if (compatLabel == null || compatLabel.isBlank()) {
            return null;
        }
        return PRIORITY_BUCKETS.get(compatLabel.trim());
    }

    public static boolean isOtherCompatCode(String compatCode) {
        return COMPAT_OTHER_CODE.equals(compatCode);
    }
}
