package com.example.welfare.global.util;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

public final class SensitiveTextRedactor {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("(?i)\\b[0-9a-z._%+-]+@[0-9a-z.-]+\\.[a-z]{2,}\\b");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("\\b01[0-9][- ]?[0-9]{3,4}[- ]?[0-9]{4}\\b");
    private static final Pattern BIRTH_DATE_PATTERN =
            Pattern.compile("\\b(?:19|20)\\d{2}[-./](?:0[1-9]|1[0-2])[-./](?:0[1-9]|[12]\\d|3[01])\\b");

    private SensitiveTextRedactor() {
    }

    public static String redactDirectIdentifiers(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String redacted = EMAIL_PATTERN.matcher(value).replaceAll("[REDACTED_EMAIL]");
        redacted = PHONE_PATTERN.matcher(redacted).replaceAll("[REDACTED_PHONE]");
        redacted = BIRTH_DATE_PATTERN.matcher(redacted).replaceAll("[REDACTED_BIRTH_DATE]");
        return redacted;
    }
}
