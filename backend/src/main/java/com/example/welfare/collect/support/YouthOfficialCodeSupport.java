package com.example.welfare.collect.support;

import com.example.welfare.collect.validation.RawFieldValidator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 온통청년 공식 코드 축을 runtime label로 해석한다.
 * 현재는 정책제공방법코드만 summary slot에 반영하고,
 * 값이 비어 있으면 기존 apply-method 기반 label을 compatibility fallback으로 유지한다.
 */
public final class YouthOfficialCodeSupport {

    private static final Map<String, String> PROVISION_METHOD_LABELS = provisionMethodLabels();
    private static final Map<String, String> MARITAL_STATUS_LABELS = maritalStatusLabels();
    private static final Map<String, String> INCOME_CONDITION_TYPE_LABELS = incomeConditionTypeLabels();
    private static final Map<String, String> MAJOR_REQUIREMENT_LABELS = majorRequirementLabels();
    private static final Map<String, String> EMPLOYMENT_REQUIREMENT_LABELS = employmentRequirementLabels();
    private static final Map<String, String> EDUCATION_REQUIREMENT_LABELS = educationRequirementLabels();
    private static final Map<String, String> SPECIAL_REQUIREMENT_LABELS = specialRequirementLabels();

    private YouthOfficialCodeSupport() {
    }

    public static String resolveProvisionMethodLabel(String provisionMethodCode, String fallbackLabel) {
        String normalizedCode = RawFieldValidator.normalize(provisionMethodCode);
        if (normalizedCode != null) {
            String officialLabel = PROVISION_METHOD_LABELS.get(normalizedCode);
            if (officialLabel != null) {
                return officialLabel;
            }
        }
        return RawFieldValidator.normalize(fallbackLabel);
    }

    public static List<String> splitOfficialCodes(String rawCodeCsv) {
        String normalized = RawFieldValidator.normalize(rawCodeCsv);
        if (normalized == null) {
            return List.of();
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (String rawToken : normalized.split(",")) {
            String code = RawFieldValidator.normalize(rawToken);
            if (code != null) {
                codes.add(code);
            }
        }
        return List.copyOf(codes);
    }

    public static List<String> resolveMaritalStatusLabels(String rawCodeCsv) {
        return resolveOfficialLabels(rawCodeCsv, MARITAL_STATUS_LABELS);
    }

    public static List<String> resolveIncomeConditionTypeLabels(String rawCodeCsv) {
        return resolveOfficialLabels(rawCodeCsv, INCOME_CONDITION_TYPE_LABELS);
    }

    public static List<String> resolveMajorRequirementLabels(String rawCodeCsv) {
        return resolveOfficialLabels(rawCodeCsv, MAJOR_REQUIREMENT_LABELS);
    }

    public static List<String> resolveEmploymentRequirementLabels(String rawCodeCsv) {
        return resolveOfficialLabels(rawCodeCsv, EMPLOYMENT_REQUIREMENT_LABELS);
    }

    public static List<String> resolveEducationRequirementLabels(String rawCodeCsv) {
        return resolveOfficialLabels(rawCodeCsv, EDUCATION_REQUIREMENT_LABELS);
    }

    public static List<String> resolveSpecialRequirementLabels(String rawCodeCsv) {
        return resolveOfficialLabels(rawCodeCsv, SPECIAL_REQUIREMENT_LABELS);
    }

    private static List<String> resolveOfficialLabels(String rawCodeCsv, Map<String, String> labelsByCode) {
        List<String> resolved = new ArrayList<>();
        for (String code : splitOfficialCodes(rawCodeCsv)) {
            String label = labelsByCode.get(code);
            if (label != null && !resolved.contains(label)) {
                resolved.add(label);
            }
        }
        return List.copyOf(resolved);
    }

    private static Map<String, String> provisionMethodLabels() {
        LinkedHashMap<String, String> labels = new LinkedHashMap<>();
        labels.put("0042001", "인프라 구축");
        labels.put("0042002", "프로그램");
        labels.put("0042003", "직접대출");
        labels.put("0042004", "공공기관");
        labels.put("0042005", "계약(위탁운영)");
        labels.put("0042006", "보조금");
        labels.put("0042007", "대출보증");
        labels.put("0042008", "공적보험");
        labels.put("0042009", "조세지출");
        labels.put("0042010", "바우처");
        labels.put("0042011", "정보제공");
        labels.put("0042012", "경제적 규제");
        labels.put("0042013", "기타");
        return Map.copyOf(labels);
    }

    private static Map<String, String> maritalStatusLabels() {
        LinkedHashMap<String, String> labels = new LinkedHashMap<>();
        labels.put("0055001", "기혼");
        labels.put("0055002", "미혼");
        labels.put("0055003", "제한없음");
        return Map.copyOf(labels);
    }

    private static Map<String, String> incomeConditionTypeLabels() {
        LinkedHashMap<String, String> labels = new LinkedHashMap<>();
        labels.put("0043001", "무관");
        labels.put("0043002", "연소득");
        labels.put("0043003", "기타");
        return Map.copyOf(labels);
    }

    private static Map<String, String> majorRequirementLabels() {
        LinkedHashMap<String, String> labels = new LinkedHashMap<>();
        labels.put("0011001", "인문계열");
        labels.put("0011002", "사회계열");
        labels.put("0011003", "상경계열");
        labels.put("0011004", "이학계열");
        labels.put("0011005", "공학계열");
        labels.put("0011006", "예체능계열");
        labels.put("0011007", "농산업계열");
        labels.put("0011008", "기타");
        labels.put("0011009", "제한없음");
        return Map.copyOf(labels);
    }

    private static Map<String, String> employmentRequirementLabels() {
        LinkedHashMap<String, String> labels = new LinkedHashMap<>();
        labels.put("0013001", "재직자");
        labels.put("0013002", "자영업자");
        labels.put("0013003", "미취업자");
        labels.put("0013004", "프리랜서");
        labels.put("0013005", "일용근로자");
        labels.put("0013006", "(예비)창업자");
        labels.put("0013007", "단기근로자");
        labels.put("0013008", "영농종사자");
        labels.put("0013009", "기타");
        labels.put("0013010", "제한없음");
        return Map.copyOf(labels);
    }

    private static Map<String, String> educationRequirementLabels() {
        LinkedHashMap<String, String> labels = new LinkedHashMap<>();
        labels.put("0049001", "고졸 미만");
        labels.put("0049002", "고교 재학");
        labels.put("0049003", "고졸 예정");
        labels.put("0049004", "고교 졸업");
        labels.put("0049005", "대학 재학");
        labels.put("0049006", "대졸 예정");
        labels.put("0049007", "대학 졸업");
        labels.put("0049008", "석·박사");
        labels.put("0049009", "기타");
        labels.put("0049010", "제한없음");
        return Map.copyOf(labels);
    }

    private static Map<String, String> specialRequirementLabels() {
        LinkedHashMap<String, String> labels = new LinkedHashMap<>();
        labels.put("0014001", "중소기업");
        labels.put("0014002", "여성");
        labels.put("0014003", "기초생활수급자");
        labels.put("0014004", "한부모가정");
        labels.put("0014005", "장애인");
        labels.put("0014006", "농업인");
        labels.put("0014007", "군인");
        labels.put("0014008", "지역인재");
        labels.put("0014009", "기타");
        labels.put("0014010", "제한없음");
        return Map.copyOf(labels);
    }
}
