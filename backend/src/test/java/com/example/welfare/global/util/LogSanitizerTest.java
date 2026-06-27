package com.example.welfare.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Test
    @DisplayName("로그 문자열의 token/query/header/cookie/JDBC userinfo를 마스킹한다")
    void sanitizeRedactsSecretLikeValues() {
        String jwtHeader = "eyJhbGciOiJI" + "UzI1NiJ9";
        String jwtPayload = "eyJzdWIiOiJ1c2Vy" + "LWtleSIsImlhdCI6MTIzfQ";
        String jwt = jwtHeader + "." + jwtPayload + ".fake-signature-value";
        String source = """
                url=https://api.example/path?serviceKey=service-secret&code=oauth-code&page=1
                Authorization: Bearer access-secret
                Cookie: refreshToken=refresh-secret; Path=/
                jdbc=jdbc:postgresql://app_user:db-secret@db.example:5432/app?sslpassword=ssl-secret
                token=%s apiKey=api-secret key=generic-key
                """.formatted(jwt);

        String sanitized = LogSanitizer.sanitize(source);

        assertThat(sanitized)
                .contains("serviceKey=<redacted>")
                .contains("code=<redacted>")
                .contains("page=1")
                .contains("Authorization: Bearer <redacted>")
                .contains("Cookie: <redacted>")
                .contains("jdbc:postgresql://<redacted>@db.example")
                .contains("sslpassword=<redacted>")
                .contains("token=<redacted>")
                .contains("apiKey=<redacted>")
                .contains("key=<redacted>")
                .doesNotContain("service-secret")
                .doesNotContain("oauth-code")
                .doesNotContain("access-secret")
                .doesNotContain("refresh-secret")
                .doesNotContain("db-secret")
                .doesNotContain("ssl-secret")
                .doesNotContain("api-secret")
                .doesNotContain("generic-key")
                .doesNotContain(jwt);
    }

    @Test
    @DisplayName("로그 문자열의 직접 식별자와 labeled userKey/email도 마스킹한다")
    void sanitizeRedactsDirectIdentifiersAndLabeledValues() {
        String source = "email=user@example.com userKey=user-key-raw phone=010-1234-5678 birth=2001-04-30";

        String sanitized = LogSanitizer.sanitize(source);

        assertThat(sanitized)
                .contains("email=<redacted>")
                .contains("userKey=<redacted>")
                .contains("[REDACTED_PHONE]")
                .contains("[REDACTED_BIRTH_DATE]")
                .doesNotContain("user@example.com")
                .doesNotContain("user-key-raw")
                .doesNotContain("010-1234-5678")
                .doesNotContain("2001-04-30");
    }

    @Test
    @DisplayName("단일 라인 로그 값은 개행을 제거하고 길이를 제한한다")
    void sanitizeSingleLineNormalizesAndLimitsLength() {
        String source = "token=secret\n" + "x".repeat(80);

        String sanitized = LogSanitizer.sanitizeSingleLine(source, 24);

        assertThat(sanitized)
                .hasSize(24)
                .doesNotContain("\n")
                .doesNotContain("secret");
    }
}
