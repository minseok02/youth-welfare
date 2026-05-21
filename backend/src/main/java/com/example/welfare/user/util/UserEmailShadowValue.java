package com.example.welfare.user.util;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

public final class UserEmailShadowValue {

    private static final String SHADOW_PREFIX = "shadow_";
    private static final Set<String> SAFE_TEST_DOMAINS = Set.of(
            "example.com",
            "example.org",
            "example.net",
            "youth-welfare.dev",
            "realuser.app",
            "smoke.local",
            "localhost"
    );

    private UserEmailShadowValue() {
    }

    public static String from(String rawEmail) {
        String normalized = EmailLookupKeyGenerator.normalize(rawEmail);
        if (!StringUtils.hasText(normalized)) {
            return normalized;
        }
        if (isSafeTestDomain(normalized)) {
            return normalized;
        }
        return SHADOW_PREFIX + EmailLookupKeyGenerator.hash(normalized);
    }

    public static boolean isShadowValue(String value) {
        return StringUtils.hasText(value) && value.startsWith(SHADOW_PREFIX);
    }

    private static boolean isSafeTestDomain(String normalizedEmail) {
        int atIndex = normalizedEmail.lastIndexOf('@');
        if (atIndex < 0 || atIndex == normalizedEmail.length() - 1) {
            return false;
        }
        String domain = normalizedEmail.substring(atIndex + 1).toLowerCase(Locale.ROOT);
        return SAFE_TEST_DOMAINS.contains(domain)
                || domain.endsWith(".local")
                || domain.endsWith(".test")
                || domain.endsWith(".invalid");
    }
}
