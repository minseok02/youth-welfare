package com.example.welfare.collect.support;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * source별 원본 category label을 legacy compat category로 정규화한다.
 */
public final class CollectCategorySupport {

    private static final String COMPAT_OTHER = "기타";
    private static final String COMPAT_FINANCE_LIFE = "금융·생활지원";
    private static final String COMPAT_CULTURE = "문화·여가";
    private static final String COMPAT_HEALTH = "건강·의료";
    private static final String COMPAT_FAMILY = "가족·돌봄";
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
    private static final Set<String> YOUTH_BROAD_WELFARE_CULTURE_LABELS = Set.of(
            "복지문화",
            "금융·복지·문화"
    );
    private static final Map<String, String> BOKJIRO_COMPAT_CATEGORIES = Map.ofEntries(
            Map.entry("일자리", "일자리"),
            Map.entry("주거", "주거"),
            Map.entry("교육", "교육·직업훈련"),
            Map.entry("민간금융", "금융·생활지원"),
            Map.entry("서민금융", "금융·생활지원"), // 복지로 API 실제 응답값. 민간금융과 동일 계열이나 별도 키로 전달됨
            Map.entry("생활지원", "금융·생활지원"),
            Map.entry("문화·여가", "문화·여가"),
            Map.entry("신체건강", "건강·의료"),
            Map.entry("정신건강", "건강·의료"),
            Map.entry("보육", "가족·돌봄"),
            Map.entry("보호·돌봄", "가족·돌봄"),
            Map.entry("임신·출산", "가족·돌봄"),
            Map.entry("입양·위탁", "가족·돌봄"),  // 복지로 API 실제 응답값. 맵 누락으로 기타 분류되던 것 추가
            Map.entry("안전·위기", "안전·위기")
    );
    private static final Set<String> HEALTH_HINTS = Set.of(
            "정신건강", "심리", "상담", "의료", "의료비", "건강", "치료", "병원", "중독", "검진"
    );
    private static final Set<String> CULTURE_HINTS = Set.of(
            "문화", "예술", "문화예술", "여가", "공연", "전시", "축제", "관람", "독서", "동아리",
            "창작", "콘서트", "영화", "스포츠", "체육"
    );
    private static final Set<String> FAMILY_HINTS = Set.of(
            "돌봄", "보육", "가족", "임신", "출산", "양육", "육아"
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
        return mapYouthCompatCategory(rawYouthMajor, null, null, null);
    }

    public static String mapYouthCompatCategory(String rawYouthMajor,
                                                String title,
                                                String description,
                                                String keyword) {
        if (rawYouthMajor == null || rawYouthMajor.isBlank()) {
            return COMPAT_OTHER;
        }
        String firstLabel = normalizeMiddleDot(rawYouthMajor.split(",")[0].trim());
        String mapped = YOUTH_COMPAT_CATEGORIES.getOrDefault(firstLabel, COMPAT_OTHER);
        if (!COMPAT_FINANCE_LIFE.equals(mapped) || !YOUTH_BROAD_WELFARE_CULTURE_LABELS.contains(firstLabel)) {
            return mapped;
        }

        String heuristicText = combinedNormalizedText(title, description, keyword);
        if (containsAny(heuristicText, HEALTH_HINTS)) {
            return COMPAT_HEALTH;
        }
        if (containsAny(heuristicText, CULTURE_HINTS)) {
            return COMPAT_CULTURE;
        }
        if (containsAny(heuristicText, FAMILY_HINTS)) {
            return COMPAT_FAMILY;
        }
        return mapped;
    }

    public static String mapBokjiroCompatCategory(String rawInterestThemesCsv) {
        if (rawInterestThemesCsv == null || rawInterestThemesCsv.isBlank()) {
            return COMPAT_OTHER;
        }
        String firstLabel = normalizeMiddleDot(rawInterestThemesCsv.split(",")[0].trim());
        return BOKJIRO_COMPAT_CATEGORIES.getOrDefault(firstLabel, COMPAT_OTHER);
    }

    private static String combinedNormalizedText(String... values) {
        Set<String> segments = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            segments.add(normalizeMiddleDot(value).toLowerCase());
        }
        return String.join(" ", segments);
    }

    private static boolean containsAny(String text, Set<String> hints) {
        if (text.isBlank()) {
            return false;
        }
        return hints.stream().anyMatch(text::contains);
    }
}
