package com.example.welfare.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BootRunCorsContractTest {

    private static final Path BUILD_GRADLE = Path.of("build.gradle");

    @Test
    @DisplayName("bootRun은 운영 CORS env가 있어도 로컬 Vite origin을 허용 목록에 유지한다")
    void bootRunKeepsLocalViteOriginsInCorsAllowedOrigins() throws IOException {
        String buildGradle = Files.readString(BUILD_GRADLE);

        assertThat(buildGradle)
                .contains("def bootRunCorsAllowedOrigins")
                .contains("'http://127.0.0.1:5173', 'http://localhost:5173'")
                .contains("environment 'SECURITY_CORS_ALLOWED_ORIGINS', bootRunCorsAllowedOrigins(runtimeEnv('SECURITY_CORS_ALLOWED_ORIGINS'))");
    }

    @Test
    @DisplayName("integrationTest DB URL은 운영 DB_URL을 fallback으로 재사용하지 않는다")
    void integrationTestDoesNotFallbackToRuntimeDatabaseUrl() throws IOException {
        String buildGradle = Files.readString(BUILD_GRADLE);

        assertThat(buildGradle)
                .contains("def sanitizedIntegrationJdbcUrl")
                .contains("Integration tests must use a loopback PostgreSQL URL")
                .contains("environment 'INTEGRATION_DB_URL', sanitizedIntegrationJdbcUrl(runtimeEnv('INTEGRATION_DB_URL'), 'jdbc:postgresql://127.0.0.1:5433/youth_welfare?sslmode=disable')");
        assertThat(buildGradle)
                .doesNotContain("runtimeEnv('INTEGRATION_DB_URL') ?: runtimeEnv('DB_URL')")
                .doesNotContain("runtimeEnv('INTEGRATION_ADMIN_RO_DB_URL') ?: runtimeEnv('ADMIN_RO_DB_URL') ?: runtimeEnv('DB_URL')")
                .doesNotContain("runtimeEnv('INTEGRATION_APP_PII_DB_URL') ?: runtimeEnv('APP_PII_DB_URL')");
    }
}
