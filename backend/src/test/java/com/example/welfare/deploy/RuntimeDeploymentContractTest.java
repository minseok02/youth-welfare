package com.example.welfare.deploy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeDeploymentContractTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    @DisplayName("runtime env renderer는 HA 운영에 필요한 scheduler와 pool key를 전달한다")
    void runtimeEnvRendererAllowsHaRuntimeKeys() throws IOException {
        String script = Files.readString(REPO_ROOT.resolve("deploy/env/render-app-runtime-env.sh"));

        assertThat(script).contains(
                "APP_SCHEDULER_ENABLED",
                "DB_POOL_MAX_SIZE",
                "DB_POOL_MIN_IDLE",
                "DB_APP_PII_POOL_MAX_SIZE",
                "DB_ADMIN_RO_POOL_MAX_SIZE",
                "DB_NOTIFICATION_PII_RO_POOL_MAX_SIZE"
        );
    }

    @Test
    @DisplayName("ALB nginx template은 HTTP target, health check, edge guard 계약을 유지한다")
    void albNginxTemplateKeepsEdgeContracts() throws IOException {
        String nginx = Files.readString(REPO_ROOT.resolve("deploy/nginx/youth-welfare.alb.conf"));

        assertThat(nginx).contains(
                "listen 80;",
                "location = /alb-health",
                "proxy_pass http://127.0.0.1:8082/actuator/health;",
                "proxy_set_header X-Forwarded-Proto https;",
                "proxy_set_header X-Forwarded-Port 443;",
                "add_header Strict-Transport-Security",
                "add_header Content-Security-Policy",
                "location ^~ /actuator",
                "location ^~ /swagger-ui",
                "location ^~ /v3/api-docs"
        );
        assertThat(nginx).doesNotContain("return 301 https://$host$request_uri");
        assertThat(nginx).doesNotContain("listen 443 ssl");
    }
}
