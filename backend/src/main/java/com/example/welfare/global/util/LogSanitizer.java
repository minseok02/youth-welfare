package com.example.welfare.global.util;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

public final class LogSanitizer {

    private static final Pattern JWT_VALUE = Pattern.compile(
            "\\b[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{10,}\\b"
    );
    private static final Pattern AUTHORIZATION_VALUE = Pattern.compile(
            "(?i)\\b((?:authorization|proxy-authorization)\\s*[:=]\\s*(?:Bearer|Basic)\\s+)[A-Za-z0-9._~+/=-]+"
    );
    private static final Pattern BEARER_VALUE = Pattern.compile(
            "(?i)\\b(Bearer\\s+)[A-Za-z0-9._~+/=-]+"
    );
    private static final Pattern COOKIE_HEADER = Pattern.compile(
            "(?i)\\b((?:cookie|set-cookie)\\s*[:=]\\s*)[^\\r\\n]+"
    );
    private static final Pattern URL_USER_INFO = Pattern.compile(
            "(?i)\\b((?:[a-z][a-z0-9+.-]*:)*[a-z][a-z0-9+.-]*://)[^\\s/@:]+(?::[^\\s/@]*)?@"
    );
    private static final Pattern QUERY_SECRET_VALUE = Pattern.compile(
            "(?i)([?&;](?:password|sslpassword|token|access[_-]?token|refresh[_-]?token|id[_-]?token|api[_-]?key|apikey|client[_-]?secret|secret|service[_-]?key|serviceKey|key|authorization[_-]?code|verification[_-]?code|reset[_-]?code|code)=)([^&#\\s,;]*)"
    );
    private static final Pattern KEY_VALUE_SECRET = Pattern.compile(
            "(?i)(^|[\\s,;{\\[(])([\"']?(?:password|sslpassword|token|access[_-]?token|refresh[_-]?token|id[_-]?token|api[_-]?key|apikey|client[_-]?secret|secret|service[_-]?key|serviceKey|key|authorization[_-]?code|verification[_-]?code|reset[_-]?code|user[_-]?key|email)[\"']?\\s*[:=]\\s*[\"']?)([^\"'\\s,;&}\\])]+)"
    );

    private LogSanitizer() {
    }

    public static String sanitize(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String sanitized = URL_USER_INFO.matcher(value).replaceAll("$1<redacted>@");
        sanitized = SensitiveTextRedactor.redactDirectIdentifiers(sanitized);
        sanitized = JWT_VALUE.matcher(sanitized).replaceAll("<redacted-jwt>");
        sanitized = AUTHORIZATION_VALUE.matcher(sanitized).replaceAll("$1<redacted>");
        sanitized = BEARER_VALUE.matcher(sanitized).replaceAll("$1<redacted>");
        sanitized = COOKIE_HEADER.matcher(sanitized).replaceAll("$1<redacted>");
        sanitized = QUERY_SECRET_VALUE.matcher(sanitized).replaceAll("$1<redacted>");
        sanitized = KEY_VALUE_SECRET.matcher(sanitized).replaceAll("$1$2<redacted>");
        return sanitized;
    }

    public static String sanitizeSingleLine(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String sanitized = sanitize(value)
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim();
        if (maxLength <= 0 || sanitized.length() <= maxLength) {
            return sanitized;
        }
        return sanitized.substring(0, maxLength);
    }
}
