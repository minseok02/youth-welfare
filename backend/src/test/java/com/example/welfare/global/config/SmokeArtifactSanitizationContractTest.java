package com.example.welfare.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SmokeArtifactSanitizationContractTest {

    private static final Path SMOKE_COMMON = Path.of("../deploy/smoke/smoke-common.sh");
    private static final Path SMOKE_DIR = Path.of("../deploy/smoke");
    private static final Path NIGHTLY_OPS_HANDOFF = Path.of("../deploy/smoke/run-nightly-ops-handoff.sh");

    private static final List<Path> TOP_LEVEL_HANDOFF_WRAPPERS = List.of(
            Path.of("../deploy/smoke/run-local-active-baseline-suite.sh"),
            Path.of("../deploy/smoke/run-local-current-priority-suite.sh")
    );

    private static final Set<String> RECOMMENDATION_CHAT_COLLECT_GOV24_ADDITIONAL_SMOKE_NAMES = Set.of(
            "run-local-bookmark-consistency-smoke.sh",
            "run-local-education-priority-replay.sh",
            "run-local-housing-standard-code-effect-audit.sh",
            "run-local-housing-standard-code-matrix-audit.sh",
            "run-local-no-priority-candidate-audit.sh",
            "run-local-no-priority-top1-sample.sh",
            "run-local-public-profile-chat-smoke.sh",
            "run-local-similar-users-viewed-smoke.sh",
            "run-local-welfare-standard-code-matrix-audit.sh"
    );

    private static final List<Path> AUTH_ADMIN_OPS_SMOKE_ENTRYPOINTS = List.of(
            Path.of("../deploy/smoke/run-local-runtime-api-smoke.sh"),
            Path.of("../deploy/smoke/run-local-login-failure-tracking-smoke.sh"),
            Path.of("../deploy/smoke/run-local-account-lockout-smoke.sh"),
            Path.of("../deploy/smoke/run-local-withdraw-smoke.sh"),
            Path.of("../deploy/smoke/run-local-admin-forced-logout-smoke.sh"),
            Path.of("../deploy/smoke/run-local-admin-authorization-smoke.sh"),
            Path.of("../deploy/smoke/run-local-admin-dashboard-smoke.sh"),
            Path.of("../deploy/smoke/run-local-auth-session-smoke.sh"),
            Path.of("../deploy/smoke/run-local-auth-observation-suite.sh"),
            Path.of("../deploy/smoke/run-local-ops-baseline-suite.sh"),
            Path.of("../deploy/smoke/run-local-ops-observation-suite.sh"),
            Path.of("../deploy/smoke/run-local-admin-collect-failures-smoke.sh"),
            Path.of("../deploy/smoke/run-local-admin-recommendation-breakdowns-smoke.sh"),
            Path.of("../deploy/smoke/run-local-admin-attention-feed-smoke.sh"),
            Path.of("../deploy/smoke/run-local-admin-recommendation-review-gate-promotion-approval-record-smoke.sh")
    );

    @Test
    @DisplayName("smoke 공통 유틸은 보존 아티팩트에서 쿠키 파일을 삭제하고 토큰/식별자를 redaction한다")
    void smokeCommonProvidesArtifactSanitizerForRetainedOutputs() throws IOException {
        String script = Files.readString(SMOKE_COMMON);

        assertThat(script)
                .contains("smoke_sanitize_artifacts()")
                .contains("smoke_redact_stream_for_log()")
                .contains("smoke_print_redacted_file_for_log()")
                .contains("-name '*.cookie'")
                .contains("-name '*cookie*'")
                .contains("-name '*cookies*'")
                .contains("<redacted-jwt>")
                .contains("Bearer\\s+")
                .contains("Set-Cookie:")
                .contains("(?:jdbc:)?(?:postgresql|postgres|mysql)://")
                .contains("password|sslpassword|token|access_token|refresh_token|api_key|apikey|client_secret|secret|key|authorization_code|verification_code|reset_code|code")
                .contains("env_key_pattern")
                .contains("<redacted-email>")
                .contains("accessToken|refreshToken|idToken|token|password|authorization|cookie|setCookie|jwtSecret|jwt_secret|apiKey|api_key|clientSecret|client_secret|secret|email|userKey")
                .contains("access_token|refresh_token|admin_access_token|jwt_secret|api_key|client_secret|secret|password|authorization|cookie|set_cookie|email|user_key|userKey|targetUserKey")
                .contains("path.write_text(redacted, encoding=\"utf-8\")");
    }

    @Test
    @DisplayName("smoke 공통 실패 출력은 응답 body를 stderr로 내보내기 전에 redaction한다")
    void smokeCommonRedactsFailureResponseLogs() throws IOException {
        String script = Files.readString(SMOKE_COMMON);

        assertThat(script)
                .contains("smoke_print_redacted_file_for_log \"${login_response}\"")
                .contains("smoke_print_redacted_file_for_log \"${health_stderr_file}\"")
                .contains("smoke_print_redacted_file_for_log \"${health_response_file}\"")
                .contains("smoke_print_redacted_file_for_log \"${file_path}\"")
                .doesNotContain("cat \"${login_response}\" >&2")
                .doesNotContain("cat \"${health_stderr_file}\" >&2")
                .doesNotContain("cat \"${health_response_file}\" >&2")
                .doesNotContain("cat \"${file_path}\" >&2");
    }

    @Test
    @DisplayName("auth/admin/ops smoke entrypoint는 cleanup에서 아티팩트를 먼저 sanitization한다")
    void authAdminOpsSmokeEntrypointsSanitizeArtifactsDuringCleanup() throws IOException {
        for (Path smokeScript : AUTH_ADMIN_OPS_SMOKE_ENTRYPOINTS) {
            String script = Files.readString(smokeScript);

            assertThat(script)
                    .as(smokeScript.toString())
                    .contains("cleanup() {")
                    .contains("smoke_sanitize_artifacts \"${ARTIFACT_DIR}\"")
                    .contains("trap cleanup EXIT");

            if (script.contains("rm -rf \"${ARTIFACT_DIR}\"")) {
                assertThat(script.indexOf("smoke_sanitize_artifacts \"${ARTIFACT_DIR}\""))
                        .as(smokeScript + " sanitizes before deleting or preserving artifacts")
                        .isLessThan(script.indexOf("rm -rf \"${ARTIFACT_DIR}\""));
            }
        }
    }

    @Test
    @DisplayName("latest snapshot을 publish하는 observation wrapper는 publish 직전에 sanitization한다")
    void observationWrappersSanitizeArtifactsBeforePublishingLatestSnapshot() throws IOException {
        for (Path suiteScript : List.of(
                Path.of("../deploy/smoke/run-local-auth-observation-suite.sh"),
                Path.of("../deploy/smoke/run-local-ops-observation-suite.sh")
        )) {
            String script = Files.readString(suiteScript);

            assertThat(script)
                    .as(suiteScript.toString())
                    .contains("smoke_sanitize_artifacts \"${ARTIFACT_DIR}\"\nsmoke_publish_dir_snapshot \"${ARTIFACT_DIR}\"");
        }
    }

    @Test
    @DisplayName("recommendation/chat/collect/Gov24 진단 smoke는 보존 아티팩트 sanitization 경계를 가진다")
    void recommendationChatCollectGov24SmokeScriptsSanitizeRetainedArtifacts() throws IOException {
        for (Path smokeScript : recommendationChatCollectGov24SmokeScripts()) {
            String script = Files.readString(smokeScript);

            if (!hasArtifactSecuritySurface(script)) {
                continue;
            }

            assertThat(script)
                    .as(smokeScript.toString())
                    .contains("smoke_sanitize_artifacts");
            assertThat(script)
                    .as(smokeScript + " must source smoke-common before calling artifact sanitizer")
                    .contains("source \"${ROOT_DIR}/deploy/smoke/smoke-common.sh\"");
        }
    }

    @Test
    @DisplayName("recommendation/chat/collect/Gov24 latest snapshot publish는 publish 직전에 sanitization한다")
    void recommendationChatCollectGov24LatestSnapshotsAreSanitizedBeforePublish() throws IOException {
        for (Path smokeScript : recommendationChatCollectGov24SmokeScripts()) {
            List<String> lines = Files.readAllLines(smokeScript);

            for (int index = 0; index < lines.size(); index++) {
                String trimmed = lines.get(index).trim();
                if (!isArtifactLatestPublishLine(trimmed)) {
                    continue;
                }

                assertThat(previousNonBlankLine(lines, index))
                        .as(smokeScript + ":" + (index + 1))
                        .isEqualTo("smoke_sanitize_artifacts \"${ARTIFACT_DIR}\"");
            }
        }
    }

    @Test
    @DisplayName("active/current-priority 상위 handoff wrapper는 cleanup과 latest publish 전에 sanitization한다")
    void topLevelHandoffWrappersSanitizeBeforeCleanupAndLatestPublish() throws IOException {
        for (Path wrapper : TOP_LEVEL_HANDOFF_WRAPPERS) {
            String script = Files.readString(wrapper);

            assertThat(script)
                    .as(wrapper.toString())
                    .contains("cleanup() {")
                    .contains("smoke_sanitize_artifacts \"${ARTIFACT_DIR}\"")
                    .contains("trap cleanup EXIT")
                    .contains("smoke_sanitize_artifacts \"${ARTIFACT_DIR}\"\nsmoke_publish_dir_snapshot \"${ARTIFACT_DIR}\" \"${LATEST_ARTIFACT_LINK}\"");

            assertThat(script.indexOf("smoke_sanitize_artifacts \"${ARTIFACT_DIR}\""))
                    .as(wrapper + " sanitizes before deleting or preserving top-level artifacts")
                    .isLessThan(script.indexOf("rm -rf \"${ARTIFACT_DIR}\""));
        }
    }

    @Test
    @DisplayName("nightly ops handoff는 child stdout RUN_DIR을 step 직후와 exit trap에서 sanitization한다")
    void nightlyOpsHandoffSanitizesRunDirArtifacts() throws IOException {
        String script = Files.readString(NIGHTLY_OPS_HANDOFF);

        assertThat(script)
                .contains("cleanup() {")
                .contains("smoke_sanitize_artifacts \"${RUN_DIR}\"")
                .contains("trap cleanup EXIT")
                .contains("printf '%s=skipped\\n' \"${label}\" | tee \"${output_file}\" >/dev/null\n    smoke_sanitize_artifacts \"${RUN_DIR}\"")
                .contains("bash \"${script_path}\" | tee \"${output_file}\"\n  smoke_sanitize_artifacts \"${RUN_DIR}\"");
    }

    @Test
    @DisplayName("parent smoke suite가 child artifact를 보존하더라도 suite cleanup에서 다시 sanitization한다")
    void parentSmokeSuitesResanitizeChildArtifactsKeptForSummary() throws IOException {
        for (Path suiteScript : List.of(
                Path.of("../deploy/smoke/run-local-auth-session-smoke.sh"),
                Path.of("../deploy/smoke/run-local-ops-baseline-suite.sh"),
                Path.of("../deploy/smoke/run-local-ops-observation-suite.sh")
        )) {
            String script = Files.readString(suiteScript);

            assertThat(script)
                    .as(suiteScript.toString())
                    .contains("KEEP_ARTIFACTS=true")
                    .contains("smoke_sanitize_artifacts \"${ARTIFACT_DIR}\"");
        }
    }

    @Test
    @DisplayName("문서는 운영 smoke 보존 아티팩트의 민감값 sanitization 경계를 기록한다")
    void docsDescribeSmokeArtifactSanitizationBoundary() throws IOException {
        assertThat(Files.readString(Path.of("../docs/core/deep-audit-checklist.md")))
                .contains("auth observation/session, admin dashboard/collect/recommendation/attention, ops baseline/observation smoke는 cleanup에서 `smoke_sanitize_artifacts` 를 호출한다.")
                .contains("recommendation/chat/collect/Gov24/real-user 진단 smoke는 단독 실행 cleanup과 `latest` publish 직전에 같은 sanitizer를 다시 호출한다.")
                .contains("nightly/active-baseline/current-priority 상위 handoff wrapper도 child stdout과 latest handoff를 publish하기 전에 sanitizer를 호출한다.")
                .contains("보존 아티팩트에 남는 cookie jar는 삭제하고 token/password/API key/email/userKey 계열 값은 redaction한다.");

        assertThat(Files.readString(Path.of("../docs/core/security-hardening-current-state.md")))
                .contains("운영 smoke 보존 아티팩트는 cleanup 단계에서 `smoke_sanitize_artifacts` 를 거친다.")
                .contains("recommendation/chat/collect/Gov24/real-user 진단 smoke는 단독 실행과 latest snapshot publish 직전 모두 sanitizer 경계를 가진다.")
                .contains("nightly/active-baseline/current-priority 상위 handoff wrapper도 child stdout과 latest handoff 산출물을 publish하기 전에 sanitizer를 통과시킨다.")
                .contains("cookie jar는 삭제하고 access/refresh token, password, API key/secret, email, userKey 계열 값은 redaction한다.");

        assertThat(Files.readString(Path.of("../docs/phase-plan.md")))
                .contains("nightly/active-baseline/current-priority 상위 handoff wrapper의 artifact 경계도 닫았다.")
                .contains("운영 smoke 아티팩트 sanitizer 범위를 recommendation/chat/collect/Gov24/real-user 진단 smoke까지 확장했다.")
                .contains("운영 smoke 로그/아티팩트 보안도 좁혔다.")
                .contains("auth observation/session, admin dashboard/collect/recommendation/attention, ops baseline/observation 계열 smoke cleanup에 공통 `smoke_sanitize_artifacts` 를 연결했다.")
                .contains("latest snapshot을 publish하는 auth/ops observation wrapper는 publish 직전에 한 번 더 sanitize한다.")
                .contains("이 함수는 `KEEP_ARTIFACTS=true` 로 디버깅 산출물을 보존하더라도 cookie jar를 삭제하고 token/password/API key/email/userKey 계열 값을 redaction한다.");
    }

    private static List<Path> recommendationChatCollectGov24SmokeScripts() throws IOException {
        try (Stream<Path> stream = Files.list(SMOKE_DIR)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(SmokeArtifactSanitizationContractTest::isRecommendationChatCollectGov24SmokeScript)
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private static boolean isRecommendationChatCollectGov24SmokeScript(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".sh")
                && (
                name.startsWith("run-local-recommendation")
                        || name.startsWith("run-local-chat")
                        || name.startsWith("run-local-collect")
                        || name.startsWith("run-local-gov24")
                        || name.startsWith("run-local-real-user")
                        || RECOMMENDATION_CHAT_COLLECT_GOV24_ADDITIONAL_SMOKE_NAMES.contains(name)
        );
    }

    private static boolean hasArtifactSecuritySurface(String script) {
        return script.contains("cleanup() {")
                || script.contains("smoke_publish_dir_snapshot")
                || script.contains("smoke_update_links")
                || script.contains("KEEP_ARTIFACTS=true")
                || script.contains("accessToken")
                || script.contains("COOKIE_JAR")
                || script.contains("COOKIE_DIR")
                || script.contains("Authorization: Bearer");
    }

    private static boolean isArtifactLatestPublishLine(String trimmedLine) {
        return trimmedLine.equals("smoke_publish_dir_snapshot \"${ARTIFACT_DIR}\" \"${LATEST_ARTIFACT_LINK}\"")
                || trimmedLine.equals("smoke_update_links \\");
    }

    private static String previousNonBlankLine(List<String> lines, int index) {
        for (int cursor = index - 1; cursor >= 0; cursor--) {
            String candidate = lines.get(cursor).trim();
            if (!candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }
}
