package com.example.welfare.collect.support;

import java.util.Map;

/**
 * source별 원본 category label을 legacy compat category로 정규화한다.
 */
public final class CollectCategorySupport {

    private static final String COMPAT_OTHER = "기타";
    private static final Map<String, String> YOUTH_COMPAT_CATEGORIES = Map.ofEntries(
            Map.entry("일자리", "일자리"),
            Map.entry("주거", "주거"),
            Map.entry("교육", "교육·직업훈련"),
            Map.entry("교육지원", "교육·직업훈련"),
            Map.entry("교육·직업훈련", "교육·직업훈련"),
            Map.entry("복지문화", "금융·생활지원"),
            Map.entry("금융·복지·문화", "금융·생활지원"),
            Map.entry("참여권리", "참여·기회"),
            Map.entry("참여·기반", "참여·기회")
    );
    private static final Map<String, String> BOKJIRO_COMPAT_CATEGORIES = Map.ofEntries(
            Map.entry("일자리", "일자리"),
            Map.entry("주거", "주거"),
            Map.entry("교육", "교육·직업훈련"),
            Map.entry("민간금융", "금융·생활지원"),
            Map.entry("생활지원", "금융·생활지원"),
            Map.entry("문화·여가", "문화·여가"),
            Map.entry("신체건강", "건강·의료"),
            Map.entry("정신건강", "건강·의료"),
            Map.entry("보육", "가족·돌봄"),
            Map.entry("보호·돌봄", "가족·돌봄"),
            Map.entry("임신·출산", "가족·돌봄"),
            Map.entry("안전·위기", "안전·위기")
    );

    private CollectCategorySupport() {
    }

    // 외형상 같아 보이는 중점 유니코드 변형(･ U+FF65, · U+00B7, · U+22C5 등)을 표준 중점으로 통일
    private static String normalizeMiddleDot(String s) {
        return s.replace('･', '·')  // 반각 가타카나 중점
                .replace('⋅', '·')  // dot operator
                .replace('‧', '·'); // hyphenation point
    }

    public static String mapYouthCompatCategory(String rawYouthMajor) {
        if (rawYouthMajor == null || rawYouthMajor.isBlank()) {
            return COMPAT_OTHER;
        }
        String firstLabel = normalizeMiddleDot(rawYouthMajor.split(",")[0].trim());
        return YOUTH_COMPAT_CATEGORIES.getOrDefault(firstLabel, COMPAT_OTHER);
    }

    public static String mapBokjiroCompatCategory(String rawInterestThemesCsv) {
        if (rawInterestThemesCsv == null || rawInterestThemesCsv.isBlank()) {
            return COMPAT_OTHER;
        }
        String firstLabel = normalizeMiddleDot(rawInterestThemesCsv.split(",")[0].trim());
        return BOKJIRO_COMPAT_CATEGORIES.getOrDefault(firstLabel, COMPAT_OTHER);
    }
}
