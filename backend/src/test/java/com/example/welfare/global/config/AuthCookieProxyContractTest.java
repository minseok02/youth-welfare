package com.example.welfare.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthCookieProxyContractTest {

    @Test
    @DisplayName("prod profile은 reverse proxy forwarded header를 framework 전략으로 해석한다")
    void prodProfileUsesForwardedHeaders() throws IOException {
        String prodConfig = Files.readString(Path.of("src/main/resources/application-prod.yml"));

        assertThat(prodConfig).contains("forward-headers-strategy: framework");
    }

    @Test
    @DisplayName("운영 refresh cookie secure 플래그는 env 예시와 runtime render allowlist에 명시된다")
    void refreshCookieSecureIsExplicitInRuntimeEnvContract() throws IOException {
        String appConfig = Files.readString(Path.of("src/main/resources/application.yml"));
        String renderScript = Files.readString(Path.of("../deploy/env/render-app-runtime-env.sh"));

        assertThat(appConfig).contains("cookie-secure: ${AUTH_REFRESH_COOKIE_SECURE:true}");
        assertThat(renderScript).contains("AUTH_REFRESH_COOKIE_SECURE");

        for (Path envExample : List.of(Path.of("../.env.example"), Path.of("../env.production.example"))) {
            assertThat(Files.readString(envExample))
                    .as(envExample.toString())
                    .contains("AUTH_REFRESH_COOKIE_SECURE=true");
        }
    }

    @Test
    @DisplayName("runtime cutover preflight는 운영 refresh cookie Secure=false를 허용하지 않는다")
    void runtimeCutoverPreflightRequiresSecureRefreshCookie() throws IOException {
        String preflight = Files.readString(Path.of("../deploy/smoke/preflight-runtime-cutover-env.sh"));

        assertThat(preflight)
                .contains("require_non_empty AUTH_REFRESH_COOKIE_SECURE")
                .contains("assert_equals AUTH_REFRESH_COOKIE_SECURE \"${AUTH_REFRESH_COOKIE_SECURE}\" \"true\"")
                .contains("- AUTH_REFRESH_COOKIE_SECURE: ${AUTH_REFRESH_COOKIE_SECURE}");
    }

    @Test
    @DisplayName("nginx HTTPS proxy는 Spring forwarded header 해석에 필요한 proto/host/port를 전달한다")
    void httpsNginxProxyForwardsProtoHostAndPort() throws IOException {
        String nginxConfig = Files.readString(Path.of("../deploy/nginx/youth-welfare.conf"));

        assertThat(nginxConfig)
                .contains("proxy_set_header X-Forwarded-Proto https;")
                .contains("proxy_set_header X-Forwarded-Port 443;")
                .contains("proxy_set_header X-Forwarded-Host $host;");
    }

    @Test
    @DisplayName("bootstrap nginx proxy도 HTTP proto/port를 명시해 TLS 전환 전제를 분리한다")
    void bootstrapNginxProxyForwardsHttpProtoAndPort() throws IOException {
        String bootstrapConfig = Files.readString(Path.of("../deploy/nginx/youth-welfare.bootstrap.conf"));

        assertThat(bootstrapConfig)
                .contains("proxy_set_header X-Forwarded-Proto http;")
                .contains("proxy_set_header X-Forwarded-Port 80;")
                .contains("proxy_set_header X-Forwarded-Host $host;");
    }
}
