package com.example.welfare.collect.support;

import com.example.welfare.collect.validation.RawFieldValidator;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 복지로 source에 공통인 텍스트 정규화 규칙을 모은다.
 */
public final class BokjiroNormalizationSupport {

    private static final List<String> BASIC_LIVELIHOOD_LABELS = List.of(
            "국민기초생활보장수급자",
            "기초생활수급자",
            "생계급여 수급자",
            "의료급여 수급자",
            "주거급여 수급자",
            "교육급여 수급자",
            "수급권자"
    );

    private BokjiroNormalizationSupport() {
    }

    public static List<String> beneficiaryLabels(String... texts) {
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        if (texts == null) {
            return List.of();
        }
        for (String text : texts) {
            collectBeneficiaryLabels(labels, text);
        }
        return List.copyOf(labels);
    }

    private static void collectBeneficiaryLabels(Set<String> labels, String text) {
        String normalizedText = RawFieldValidator.normalize(text);
        if (normalizedText == null) {
            return;
        }
        if (containsAny(normalizedText, BASIC_LIVELIHOOD_LABELS)) {
            labels.add("기초생활수급자");
        }
        if (normalizedText.contains("차상위")) {
            labels.add("차상위계층");
        }
    }

    private static boolean containsAny(String text, List<String> candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
