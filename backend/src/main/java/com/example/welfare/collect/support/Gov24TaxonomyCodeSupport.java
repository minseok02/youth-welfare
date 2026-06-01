package com.example.welfare.collect.support;

import com.example.welfare.collect.validation.RawFieldValidator;

import java.util.Map;

public final class Gov24TaxonomyCodeSupport {

    private static final Map<String, String> SERVICE_FIELD_CODES = Map.of(
            "생활안정", "LIFE_STABILITY",
            "농림축산어업", "AGRI_FISHERY",
            "보육·교육", "CHILD_EDUCATION",
            "보건·의료", "HEALTH_MEDICAL",
            "임신·출산", "PREGNANCY_BIRTH",
            "고용·창업", "JOB_STARTUP",
            "문화·환경", "CULTURE_ENVIRONMENT",
            "보호·돌봄", "PROTECTION_CARE",
            "행정·안전", "ADMIN_SAFETY",
            "주거·자립", "HOUSING_SELF_RELIANCE"
    );
    private static final Map<String, String> USER_TYPE_TOKEN_CODES = Map.of(
            "개인", "INDIVIDUAL",
            "법인/시설/단체", "ORG_FACILITY_GROUP",
            "가구", "HOUSEHOLD",
            "소상공인", "SMALL_BUSINESS"
    );
    private static final Map<String, String> BENEFIT_TYPE_TOKEN_CODES = Map.ofEntries(
            Map.entry("현금", "CASH"),
            Map.entry("현물", "IN_KIND"),
            Map.entry("기타", "OTHER"),
            Map.entry("현금(감면)", "CASH_REDUCTION"),
            Map.entry("이용권", "VOUCHER"),
            Map.entry("서비스(의료)", "MEDICAL_SERVICE"),
            Map.entry("시설이용", "FACILITY_USE"),
            Map.entry("기타(교육)", "EDUCATION_OTHER"),
            Map.entry("현금(보험)", "CASH_INSURANCE"),
            Map.entry("현금(장학금)", "CASH_SCHOLARSHIP"),
            Map.entry("현금(융자)", "CASH_LOAN"),
            Map.entry("기타(상담)", "COUNSELING_OTHER"),
            Map.entry("서비스(돌봄)", "CARE_SERVICE"),
            Map.entry("서비스(일자리)", "JOB_SERVICE"),
            Map.entry("의료지원", "MEDICAL_SUPPORT"),
            Map.entry("상담/법률지원", "COUNSEL_LEGAL_SUPPORT"),
            Map.entry("기술지원", "TECHNICAL_SUPPORT"),
            Map.entry("문화/여가지원", "CULTURE_LEISURE_SUPPORT"),
            Map.entry("민원", "CIVIL_SERVICE"),
            Map.entry("봉사/기부", "VOLUNTEER_DONATION")
    );

    private Gov24TaxonomyCodeSupport() {
    }

    public static String serviceFieldCode(String label) {
        String normalized = normalize(label);
        return normalized == null ? null : SERVICE_FIELD_CODES.get(normalized);
    }

    public static String userTypeTokenCode(String label) {
        String normalized = normalize(label);
        return normalized == null ? null : USER_TYPE_TOKEN_CODES.get(normalized);
    }

    public static String benefitTypeTokenCode(String label) {
        String normalized = normalize(label);
        return normalized == null ? null : BENEFIT_TYPE_TOKEN_CODES.get(normalized);
    }

    public static YouthBridge youthBridge(String serviceFieldLabel,
                                          String benefitTypeLabel,
                                          String title,
                                          String summary) {
        String serviceField = normalize(serviceFieldLabel);
        if (serviceField == null) {
            return YouthBridge.empty();
        }
        return switch (serviceField) {
            case "주거·자립" -> new YouthBridge("주거", "주택 및 거주지");
            case "고용·창업" -> new YouthBridge("일자리", jobMidLabel(benefitTypeLabel, title, summary));
            case "보육·교육" -> new YouthBridge("교육", educationMidLabel(benefitTypeLabel, title, summary));
            case "생활안정" -> new YouthBridge("복지문화", "취약계층 및 금융지원");
            case "문화·환경" -> new YouthBridge("복지문화", "문화활동");
            case "보건·의료", "임신·출산" -> new YouthBridge("복지문화", "건강");
            case "보호·돌봄" -> new YouthBridge("복지문화", "권익보호");
            case "행정·안전" -> new YouthBridge("참여권리", "정책인프라구축");
            case "농림축산어업" -> new YouthBridge("일자리", "재직자");
            default -> YouthBridge.empty();
        };
    }

    private static String jobMidLabel(String benefitTypeLabel, String title, String summary) {
        if (containsAny(benefitTypeLabel, title, summary, "창업", "예비창업", "소상공인")) {
            return "창업";
        }
        if (containsAny(benefitTypeLabel, title, summary, "재직", "근로자", "직장인")) {
            return "재직자";
        }
        return "취업";
    }

    private static String educationMidLabel(String benefitTypeLabel, String title, String summary) {
        if (containsAny(benefitTypeLabel, title, summary, "장학", "교육비", "학비", "등록금", "입학금")) {
            return "교육비지원";
        }
        if (containsAny(benefitTypeLabel, title, summary, "온라인", "이러닝")) {
            return "온라인교육";
        }
        return "미래역량강화";
    }

    private static boolean containsAny(String first, String second, String third, String... tokens) {
        String haystack = "%s %s %s".formatted(
                RawFieldValidator.normalize(first),
                RawFieldValidator.normalize(second),
                RawFieldValidator.normalize(third)
        );
        for (String token : tokens) {
            if (haystack.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return RawFieldValidator.normalize(value);
    }

    public record YouthBridge(String youthMajorLabel, String youthMidLabel) {
        public static YouthBridge empty() {
            return new YouthBridge(null, null);
        }
    }
}
