package com.example.welfare.global.util;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

public final class SensitiveTextRedactor {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("(?i)\\b[0-9a-z._%+-]+@[0-9a-z.-]+\\.[a-z]{2,}\\b");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?<!\\d)(?:\\+82[-. ]?10|01[0-9])[-. ]?[0-9]{3,4}[-. ]?[0-9]{4}(?!\\d)");
    private static final Pattern BIRTH_DATE_PATTERN =
            Pattern.compile("\\b(?:19|20)\\d{2}[-./](?:0[1-9]|1[0-2])[-./](?:0[1-9]|[12]\\d|3[01])\\b");
    private static final Pattern COMPACT_BIRTH_DATE_PATTERN =
            Pattern.compile("\\b(?:19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\b");
    private static final Pattern KOREAN_BIRTH_DATE_PATTERN =
            Pattern.compile("\\b(?:19|20)\\d{2}\\s*년\\s*(?:0?[1-9]|1[0-2])\\s*월\\s*(?:0?[1-9]|[12]\\d|3[01])\\s*일\\b");
    private static final Pattern RESIDENT_REGISTRATION_PATTERN =
            Pattern.compile("\\b\\d{6}[- ]?[1-8]\\d{6}\\b");
    private static final Pattern ACCOUNT_PATTERN =
            Pattern.compile("(?i)(?:계좌(?:번호)?(?:[은는이가]|번호는)?|account)\\s*[:=]?\\s*[0-9-]{8,}");
    private static final Pattern NAME_LABEL_PATTERN =
            Pattern.compile("(?i)\\b(이름|성함|본명|name)(?:은|는|이|가)?\\s*(?:[:=]\\s*|\\s+)([가-힣A-Za-z]{2,20})\\b");
    private static final Pattern ADDRESS_LABEL_PATTERN =
            Pattern.compile("(?i)\\b(주소|집\\s?주소|거주지|사는\\s?곳|address)(?:는|은|이|가)?\\s*(?:[:=]\\s*|\\s+)(.{4,80}?)(?=\\s*(?:,|\\n|$|학교명|학교|회사명|회사|직장|근무지|소속|school|company|organization))");
    private static final Pattern ORGANIZATION_LABEL_PATTERN =
            Pattern.compile("(?i)\\b(학교명|학교|회사명|회사|직장|근무지|소속|school|company|organization)(?:은|는|이|가)?\\s*(?:[:=]\\s*|\\s+)(.{2,60}?)(?=\\s*(?:,|\\n|$|학교명|학교|회사명|회사|직장|근무지|소속|school|company|organization))");

    private SensitiveTextRedactor() {
    }

    public static String redactDirectIdentifiers(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String redacted = EMAIL_PATTERN.matcher(value).replaceAll("[REDACTED_EMAIL]");
        redacted = PHONE_PATTERN.matcher(redacted).replaceAll("[REDACTED_PHONE]");
        redacted = BIRTH_DATE_PATTERN.matcher(redacted).replaceAll("[REDACTED_BIRTH_DATE]");
        redacted = COMPACT_BIRTH_DATE_PATTERN.matcher(redacted).replaceAll("[REDACTED_BIRTH_DATE]");
        redacted = KOREAN_BIRTH_DATE_PATTERN.matcher(redacted).replaceAll("[REDACTED_BIRTH_DATE]");
        redacted = RESIDENT_REGISTRATION_PATTERN.matcher(redacted).replaceAll("[REDACTED_RRN]");
        redacted = ACCOUNT_PATTERN.matcher(redacted).replaceAll("[REDACTED_ACCOUNT]");
        redacted = redactLabeledValue(redacted, NAME_LABEL_PATTERN, "[REDACTED_NAME]");
        redacted = redactLabeledValue(redacted, ADDRESS_LABEL_PATTERN, "[REDACTED_ADDRESS]");
        redacted = redactLabeledValue(redacted, ORGANIZATION_LABEL_PATTERN, "[REDACTED_ORG]");
        return redacted;
    }

    private static String redactLabeledValue(String value, Pattern pattern, String replacementToken) {
        return pattern.matcher(value).replaceAll("$1 " + replacementToken);
    }
}
