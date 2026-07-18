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
    private static final Set<String> HOUSING_HINTS = Set.of(
            "주거", "주택", "월세", "전세", "보증금", "임대료", "임차료", "주거급여"
    );
    private static final Set<String> DIRECT_HOUSING_BENEFIT_HINTS = Set.of(
            "청년월세", "월세", "전월세", "전세", "임차보증금", "임대보증금", "임대료", "임차료",
            "주거비", "주거자금", "주거안정", "주택구입", "공공임대주택", "임대주택", "중개보수",
            "기숙사", "학숙"
    );
    private static final Set<String> ASSET_FORMATION_HINTS = Set.of(
            "통장", "적금", "저축", "자산형성", "자립형성", "자립정착금", "매칭 적립", "매칭금"
    );
    private static final Set<String> HOUSING_PURPOSE_ASSET_HINTS = Set.of(
            "주택드림", "청약통장", "주택청약"
    );
    private static final Set<String> RESIDENTIAL_HOUSING_KEEP_HINTS = Set.of(
            "무주택", "숙소", "기숙사", "학숙", "주거비", "주거 비용", "주거 안정", "주거안정",
            "임시 거주", "거주 공간", "주택 임차", "주택임차", "임차보증금", "임대보증금",
            "전월세", "월세", "전세", "이사비", "주택자금", "주택구입", "공공임대주택", "임대주택"
    );
    private static final Set<String> BUSINESS_STARTUP_JOB_HINTS = Set.of(
            "창업", "창업자", "청년창업", "사업자", "소상공인", "사업장", "점포", "공유오피스",
            "인큐베이팅", "푸드빌리지", "창업농", "영농정착", "어촌정착", "수산업 경영인"
    );
    private static final Set<String> BUSINESS_RENT_SPACE_OR_SETTLEMENT_HINTS = Set.of(
            "사업장 임대료", "사업장 임차료", "사업장임대료", "사업장임차료", "점포 임차료", "점포임차료",
            "창업자 임차료", "사업자 임차료", "사업자 월 임차료", "창업 청년 임대료", "창업공간",
            "창업 공간", "창업공유공간", "창업 공유공간", "공유오피스", "사무공간", "실험실",
            "외식창업공간", "푸드빌리지", "창업비용", "영농정착", "어촌정착", "정착금"
    );
    private static final Set<String> FINANCE_LIFE_HINTS = Set.of(
            "생활안정", "생활지원", "생활비", "생계", "자금", "융자", "대출", "보증", "월세보증금"
    );
    private static final Set<String> EDUCATION_HINTS = Set.of(
            "교육", "훈련", "직무", "학습", "어학", "자격증", "장학"
    );
    private static final Set<String> BOKJIRO_BROAD_FINANCE_LIFE_LABELS = Set.of(
            "생활지원",
            "민간금융",
            "서민금융"
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
        String heuristicText = combinedNormalizedText(firstLabel, title, description, keyword);
        if ("주거".equals(mapped) && shouldUseJobForStartupBusinessSupport(heuristicText)) {
            return "일자리";
        }
        if (!COMPAT_FINANCE_LIFE.equals(mapped) || !YOUTH_BROAD_WELFARE_CULTURE_LABELS.contains(firstLabel)) {
            return mapped;
        }

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
        return mapBokjiroCompatCategory(rawInterestThemesCsv, null, null, null);
    }

    public static String mapBokjiroCompatCategory(String rawInterestThemesCsv,
                                                  String title,
                                                  String description,
                                                  String provisionType) {
        if (rawInterestThemesCsv == null || rawInterestThemesCsv.isBlank()) {
            return inferBokjiroCompatCategory(COMPAT_OTHER, null, title, description, provisionType);
        }
        String firstLabel = normalizeMiddleDot(rawInterestThemesCsv.split(",")[0].trim());
        String mapped = BOKJIRO_COMPAT_CATEGORIES.getOrDefault(firstLabel, COMPAT_OTHER);
        String inferred = inferBokjiroCompatCategory(mapped, firstLabel, title, description, provisionType);
        String heuristicText = combinedNormalizedText(firstLabel, title, description, provisionType);
        if ("주거".equals(inferred) && shouldUseJobForStartupBusinessSupport(heuristicText)) {
            return "일자리";
        }
        return refineAssetFormationCategory(inferred, title, description, provisionType);
    }

    public static String mapGov24CompatCategory(String rawServiceField,
                                                String title,
                                                String description,
                                                String supportType) {
        String normalizedField = normalizeMiddleDot(rawServiceField == null ? "" : rawServiceField).trim();
        String heuristicText = combinedNormalizedText(normalizedField, title, description, supportType);
        String titleSummaryText = combinedNormalizedText(title, description);

        if ((normalizedField.contains("주거") || normalizedField.contains("주택"))
                && containsAny(titleSummaryText, RESIDENTIAL_HOUSING_KEEP_HINTS)) {
            return "주거";
        }
        if ((normalizedField.contains("주거") || normalizedField.contains("주택"))
                && shouldUseJobForStartupBusinessSupport(heuristicText)) {
            return "일자리";
        }
        if (normalizedField.contains("일자리") || normalizedField.contains("고용") || heuristicText.contains("취업")) {
            return "일자리";
        }
        if (containsAny(titleSummaryText, DIRECT_HOUSING_BENEFIT_HINTS)
                && (normalizedField.contains("금융")
                || normalizedField.contains("생활")
                || normalizedField.contains("복지"))) {
            return "주거";
        }
        if ((normalizedField.contains("주거") || normalizedField.contains("주택"))
                && shouldUseFinanceLifeForAssetFormation(heuristicText)) {
            return COMPAT_FINANCE_LIFE;
        }
        if (normalizedField.contains("주거") || normalizedField.contains("주택")) {
            return "주거";
        }
        if (normalizedField.contains("교육") || normalizedField.contains("훈련")) {
            return "교육·직업훈련";
        }
        if (normalizedField.contains("문화") || normalizedField.contains("여가") || containsAny(heuristicText, CULTURE_HINTS)) {
            return COMPAT_CULTURE;
        }
        if (normalizedField.contains("건강") || normalizedField.contains("의료") || containsAny(heuristicText, HEALTH_HINTS)) {
            return COMPAT_HEALTH;
        }
        if (normalizedField.contains("가족") || normalizedField.contains("돌봄") || containsAny(heuristicText, FAMILY_HINTS)) {
            return COMPAT_FAMILY;
        }
        if (normalizedField.contains("금융") || normalizedField.contains("생활") || normalizedField.contains("복지")) {
            return COMPAT_FINANCE_LIFE;
        }
        if (normalizedField.contains("안전") || normalizedField.contains("위기")) {
            return "안전·위기";
        }
        return COMPAT_OTHER;
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

    private static String inferBokjiroCompatCategory(String mapped,
                                                     String firstLabel,
                                                     String title,
                                                     String description,
                                                     String provisionType) {
        String heuristicText = combinedNormalizedText(firstLabel, title, description, provisionType);
        boolean broadFinanceLife = firstLabel != null && BOKJIRO_BROAD_FINANCE_LIFE_LABELS.contains(firstLabel);

        if (containsAny(heuristicText, HOUSING_HINTS) && (COMPAT_OTHER.equals(mapped) || broadFinanceLife)) {
            return "주거";
        }
        if (containsAny(heuristicText, HEALTH_HINTS) && (COMPAT_OTHER.equals(mapped) || broadFinanceLife)) {
            return COMPAT_HEALTH;
        }
        if (containsAny(heuristicText, FAMILY_HINTS) && (COMPAT_OTHER.equals(mapped) || broadFinanceLife)) {
            return COMPAT_FAMILY;
        }
        if (containsAny(heuristicText, EDUCATION_HINTS) && COMPAT_OTHER.equals(mapped)) {
            return "교육·직업훈련";
        }
        if (containsAny(heuristicText, CULTURE_HINTS) && COMPAT_OTHER.equals(mapped)) {
            return COMPAT_CULTURE;
        }
        if (containsAny(heuristicText, FINANCE_LIFE_HINTS) && COMPAT_OTHER.equals(mapped)) {
            return COMPAT_FINANCE_LIFE;
        }
        return mapped;
    }

    private static String refineAssetFormationCategory(String mapped,
                                                       String title,
                                                       String description,
                                                       String provisionType) {
        if (!"주거".equals(mapped)) {
            return mapped;
        }
        String heuristicText = combinedNormalizedText(title, description, provisionType);
        if (shouldUseFinanceLifeForAssetFormation(heuristicText)) {
            return COMPAT_FINANCE_LIFE;
        }
        return mapped;
    }

    private static boolean shouldUseFinanceLifeForAssetFormation(String heuristicText) {
        return containsAny(heuristicText, ASSET_FORMATION_HINTS)
                && !containsAny(heuristicText, DIRECT_HOUSING_BENEFIT_HINTS)
                && !containsAny(heuristicText, HOUSING_PURPOSE_ASSET_HINTS);
    }

    private static boolean shouldUseJobForStartupBusinessSupport(String heuristicText) {
        return containsAny(heuristicText, BUSINESS_STARTUP_JOB_HINTS)
                && containsAny(heuristicText, BUSINESS_RENT_SPACE_OR_SETTLEMENT_HINTS)
                && !looksLikeResidentialHousingBenefit(heuristicText);
    }

    private static boolean looksLikeResidentialHousingBenefit(String heuristicText) {
        return containsAny(heuristicText, RESIDENTIAL_HOUSING_KEEP_HINTS)
                && !containsAny(heuristicText, BUSINESS_RENT_SPACE_OR_SETTLEMENT_HINTS);
    }
}
