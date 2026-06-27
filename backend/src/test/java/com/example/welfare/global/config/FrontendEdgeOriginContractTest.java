package com.example.welfare.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FrontendEdgeOriginContractTest {

    private static final String APEX_ORIGIN = "https://youthmoa.kr";
    private static final String WWW_ORIGIN = "https://www.youthmoa.kr";
    private static final String EXPECTED_CONNECT_SRC =
            "connect-src 'self' https://youthmoa.kr https://www.youthmoa.kr;";

    @Test
    @DisplayName("프론트 production 예시는 same-origin API 호출을 기본값으로 둔다")
    void frontendEnvExampleKeepsApiBaseUrlSameOriginByDefault() throws IOException {
        String frontendEnvExample = Files.readString(Path.of("../frontend/.env.example"));
        String frontendReadme = Files.readString(Path.of("../frontend/README.md"));

        assertThat(frontendEnvExample)
                .contains("VITE_API_BASE_URL=")
                .doesNotContain("VITE_API_BASE_URL=http://")
                .doesNotContain("VITE_API_BASE_URL=https://");
        assertThat(frontendReadme)
                .contains("Leave it empty when nginx serves the frontend and proxies `/api` on the same origin.");
    }

    @Test
    @DisplayName("운영 env 예시는 backend CORS origin을 apex와 www 모두로 고정한다")
    void productionEnvExamplesKeepCorsOriginsAlignedWithPublicHosts() throws IOException {
        for (Path envExample : List.of(Path.of("../.env.example"), Path.of("../env.production.example"))) {
            assertThat(Files.readString(envExample))
                    .as(envExample.toString())
                    .contains("APP_BASE_URL=" + APEX_ORIGIN)
                    .contains("SECURITY_CORS_ALLOWED_ORIGINS=" + APEX_ORIGIN + "," + WWW_ORIGIN);
        }
    }

    @Test
    @DisplayName("nginx CSP connect-src는 운영 public host와 CORS origin을 모두 포함한다")
    void nginxCspConnectSrcIncludesApexAndWwwOrigins() throws IOException {
        for (Path nginxConfig : List.of(
                Path.of("../deploy/nginx/youth-welfare.conf"),
                Path.of("../deploy/nginx/youth-welfare.bootstrap.conf")
        )) {
            assertThat(Files.readString(nginxConfig))
                    .as(nginxConfig.toString())
                    .contains("server_name youthmoa.kr www.youthmoa.kr;")
                    .contains(EXPECTED_CONNECT_SRC);
        }
    }

    @Test
    @DisplayName("edge 검증 스크립트는 live CSP connect-src의 필수 source를 확인한다")
    void edgeVerificationChecksRequiredCspConnectSrcSources() throws IOException {
        String edgeVerifyScript = Files.readString(Path.of("../deploy/nginx/verify-edge-baseline.sh"));

        assertThat(edgeVerifyScript)
                .contains("EXPECTED_CSP_CONNECT_SRC_CSV=\"${EXPECTED_CSP_CONNECT_SRC_CSV:-'self',https://youthmoa.kr,https://www.youthmoa.kr}\"")
                .contains("assert_csp_connect_src_contains_expected")
                .contains("csp_connect_src=");
    }

    @Test
    @DisplayName("nginx HTTPS/bootstrap은 민감 정적 경로와 관리 프록시를 같은 edge guard로 막는다")
    void nginxTemplatesKeepSensitiveStaticAndAdminProxyGuardsAligned() throws IOException {
        for (Path nginxConfig : List.of(
                Path.of("../deploy/nginx/youth-welfare.conf"),
                Path.of("../deploy/nginx/youth-welfare.bootstrap.conf")
        )) {
            String config = Files.readString(nginxConfig);

            assertThat(config)
                    .as(nginxConfig.toString())
                    .contains("location ~ /\\.(?!well-known/acme-challenge/) {")
                    .contains("location ~* \\.(?:env|ini|log|sql|bak|backup|old|orig|save|swp|tar|gz|zip|7z|archive)$ {")
                    .contains("location ~* ^/(?:wp-admin/|wp-login\\.php|xmlrpc\\.php|dana-na/|cgi-bin/|cgi-mod/) {")
                    .contains("location ^~ /actuator {")
                    .contains("location ^~ /swagger-ui {")
                    .contains("location ^~ /v3/api-docs {")
                    .contains("allow 127.0.0.1;")
                    .contains("allow ::1;")
                    .contains("deny all;");
        }
    }

    @Test
    @DisplayName("nginx access log format은 request/referer query string을 기록하지 않는다")
    void nginxAccessLogFormatDoesNotRecordQueryStrings() throws IOException {
        for (Path nginxConfig : List.of(
                Path.of("../deploy/nginx/youth-welfare.conf"),
                Path.of("../deploy/nginx/youth-welfare.bootstrap.conf")
        )) {
            String config = Files.readString(nginxConfig);

            assertThat(config)
                    .as(nginxConfig.toString())
                    .contains("map $http_referer $youth_welfare_safe_referer {")
                    .contains("\"~^(?<safe_referer>https?://[^?#]*)\" $safe_referer;")
                    .contains("log_format youth_welfare_timed")
                    .contains("\"$request_method $uri $server_protocol\" $status $body_bytes_sent \"$youth_welfare_safe_referer\" \"$http_user_agent\"")
                    .doesNotContain("\"$request\" $status")
                    .doesNotContain("\"$request_uri\"")
                    .doesNotContain("\"$http_referer\" \"$http_user_agent\"");
        }
    }

    @Test
    @DisplayName("performance artifact publish는 nginx log tail sample을 publish 전에 sanitization한다")
    void performanceArtifactsAreSanitizedBeforeLatestPublish() throws IOException {
        String perfCommon = Files.readString(Path.of("../deploy/performance/perf-common.sh"));

        assertThat(perfCommon)
                .contains("perf_publish_latest()")
                .contains("smoke_sanitize_artifacts \"${artifact_dir}\"\n  smoke_publish_dir_snapshot \"${artifact_dir}\" \"${latest_dir}\"");
    }

    @Test
    @DisplayName("edge 검증 스크립트는 actuator 외 Swagger/OpenAPI/정적 민감 경로도 외부 차단 상태로 확인한다")
    void edgeVerificationChecksSensitiveBlockedPaths() throws IOException {
        String edgeVerifyScript = Files.readString(Path.of("../deploy/nginx/verify-edge-baseline.sh"));

        assertThat(edgeVerifyScript)
                .contains("ALLOWED_BLOCKED_STATUS_CSV=\"${ALLOWED_BLOCKED_STATUS_CSV:-403,404}\"")
                .contains("BLOCKED_EDGE_PATHS_CSV=\"${BLOCKED_EDGE_PATHS_CSV:-/actuator,/swagger-ui,/swagger-ui/index.html,/v3/api-docs,/v3/api-docs/swagger-config,/.env,/application.log,/dump.sql,/backup.tar.gz,/backup.archive,/wp-login.php,/xmlrpc.php,/cgi-bin/test.cgi}\"")
                .contains("assert_blocked_edge_paths")
                .contains("unexpected blocked edge path status")
                .contains("blocked_path_status=");
    }
}
