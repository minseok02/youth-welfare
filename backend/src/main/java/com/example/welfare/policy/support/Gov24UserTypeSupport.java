package com.example.welfare.policy.support;

import java.util.Set;

public final class Gov24UserTypeSupport {

    public static final Set<String> MANAGED_TOKENS = Set.of(
            "개인",
            "가구",
            "법인/시설/단체",
            "소상공인"
    );

    private Gov24UserTypeSupport() {
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
