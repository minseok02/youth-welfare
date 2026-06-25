package com.example.welfare.policy.support;

import java.util.List;
import java.util.Set;

public final class Gov24UserTypeSupport {

    private static final List<String> MANAGED_TOKEN_LIST = List.of(
            "개인",
            "가구",
            "법인/시설/단체",
            "소상공인"
    );
    public static final Set<String> MANAGED_TOKENS = Set.copyOf(MANAGED_TOKEN_LIST);

    private Gov24UserTypeSupport() {
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
