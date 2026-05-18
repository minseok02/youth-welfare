package com.example.welfare.collect.support;

import com.example.welfare.collect.validation.RawFieldValidator;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 온통청년 공식 코드 축을 runtime label로 해석한다.
 * 현재는 정책제공방법코드만 summary slot에 반영하고,
 * 값이 비어 있으면 기존 apply-method 기반 label을 compatibility fallback으로 유지한다.
 */
public final class YouthOfficialCodeSupport {

    private static final Map<String, String> PROVISION_METHOD_LABELS = provisionMethodLabels();

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
}
