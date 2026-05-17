package com.example.welfare.collect.support;

import com.example.welfare.collect.validation.RawFieldValidator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Gov24 raw label을 additive token으로만 분해한다.
 * raw exact label은 그대로 유지하고, 관찰/진단용 내부 해석만 제공한다.
 */
public final class Gov24LabelTokenSupport {

    private static final String MULTI_VALUE_DELIMITER = "\\|\\|";

    private Gov24LabelTokenSupport() {
    }

    public static List<String> userTypeTokens(String rawUserTypeLabel) {
        return splitMultiValueLabel(rawUserTypeLabel);
    }

    public static List<String> benefitTypeTokens(String rawBenefitTypeLabel) {
        return splitMultiValueLabel(rawBenefitTypeLabel);
    }

    static List<String> splitMultiValueLabel(String rawLabel) {
        String normalized = RawFieldValidator.normalize(rawLabel);
        if (normalized == null) {
            return List.of();
        }

        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String rawToken : normalized.split(MULTI_VALUE_DELIMITER)) {
            String token = RawFieldValidator.normalize(rawToken);
            if (token != null) {
                tokens.add(token);
            }
        }

        return tokens.isEmpty() ? List.of() : List.copyOf(new ArrayList<>(tokens));
    }
}
