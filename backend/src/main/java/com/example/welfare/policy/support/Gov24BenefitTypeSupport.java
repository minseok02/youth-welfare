package com.example.welfare.policy.support;

import java.util.List;
import java.util.Set;

public final class Gov24BenefitTypeSupport {

    private static final List<String> MANAGED_TOKEN_LIST = List.of(
            "현금",
            "현물",
            "기타",
            "현금(감면)",
            "이용권",
            "서비스(의료)",
            "시설이용",
            "기타(교육)",
            "현금(보험)",
            "현금(장학금)",
            "현금(융자)",
            "기타(상담)",
            "서비스(돌봄)",
            "서비스(일자리)",
            "의료지원",
            "상담/법률지원",
            "기술지원",
            "문화/여가지원",
            "민원",
            "봉사/기부"
    );
    public static final Set<String> MANAGED_TOKENS = Set.copyOf(MANAGED_TOKEN_LIST);

    private Gov24BenefitTypeSupport() {
    }

    public static List<String> managedTokens() {
        return MANAGED_TOKEN_LIST;
    }

    public static String normalizeManagedToken(String rawToken) {
        if (rawToken == null) {
            return null;
        }
        String normalized = rawToken.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return MANAGED_TOKENS.contains(normalized) ? normalized : null;
    }
}
