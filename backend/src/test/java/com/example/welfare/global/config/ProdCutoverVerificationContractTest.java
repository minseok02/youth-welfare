package com.example.welfare.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProdCutoverVerificationContractTest {

    private static final Path PROD_CUTOVER = Path.of("../deploy/smoke/run-prod-cutover-verification.sh");
    private static final Path RUNTIME_CUTOVER_PREFLIGHT = Path.of("../deploy/smoke/preflight-runtime-cutover-env.sh");
    private static final Path DEPLOYMENT_DOC = Path.of("../docs/deployment.md");

    @Test
    @DisplayName("운영 cutover wrapper는 render 가능성 확인을 preflight/RDS/edge보다 먼저 수행한다")
    void prodCutoverWrapperRunsRenderCheckBeforePreflightDbAndEdge() throws IOException {
        String script = Files.readString(PROD_CUTOVER);

        assertThat(script)
                .contains("VERIFY_RUNTIME_ENV_RENDER=\"${VERIFY_RUNTIME_ENV_RENDER:-true}\"")
                .contains("RUNTIME_ENV_RENDER_TMP_DIR=\"\"")
                .contains("run_step \"runtime env render check\"")
                .contains("SOURCE_ENV_FILE=\"${ENV_FILE}\"")
                .contains("TARGET_ENV_FILE=\"${RUNTIME_ENV_RENDER_TMP_DIR}/.env.runtime.production\"")
                .contains("STRICT_REQUIRED_RUNTIME_ENV=true")
                .contains("bash \"${ROOT_DIR}/deploy/env/render-app-runtime-env.sh\"");

        assertThat(script.indexOf("runtime env render check"))
                .isLessThan(script.indexOf("env preflight"));
        assertThat(script.indexOf("env preflight"))
                .isLessThan(script.indexOf("rds runtime privilege verify"));
        assertThat(script.indexOf("rds runtime privilege verify"))
                .isLessThan(script.indexOf("nginx edge baseline verify"));
    }

    @Test
    @DisplayName("운영 cutover wrapper는 app 재기동/runtime API smoke를 묶지 않고 임시 render secret 파일을 보존하지 않는다")
    void prodCutoverWrapperKeepsSideEffectsBounded() throws IOException {
        String script = Files.readString(PROD_CUTOVER);

        assertThat(script)
                .contains("rm -rf \"${RUNTIME_ENV_RENDER_TMP_DIR}\"")
                .contains("mkdir -p \"${ARTIFACT_DIR}\"")
                .contains("chmod 700 \"${ARTIFACT_DIR}\"")
                .contains("runtime_env_render_output=${ARTIFACT_DIR}/render-app-runtime-env.txt")
                .doesNotContain("docker compose --env-file")
                .doesNotContain("run-local-runtime-api-smoke.sh")
                .doesNotContain("TARGET_ENV_FILE=\"${ROOT_DIR}/.env.runtime.production\"");
    }

    @Test
    @DisplayName("운영 cutover wrapper는 단계 stdout/stderr와 보존 artifact를 redaction한다")
    void prodCutoverWrapperRedactsStepOutputAndRetainedArtifacts() throws IOException {
        String script = Files.readString(PROD_CUTOVER);

        assertThat(script)
                .contains("source \"${ROOT_DIR}/deploy/smoke/smoke-common.sh\"")
                .contains("\"$@\" 2>&1 | smoke_redact_stream_for_log | tee \"${output_file}\"")
                .contains("pipe_status=(\"${PIPESTATUS[@]}\")")
                .contains("smoke_sanitize_artifacts \"${ARTIFACT_DIR}\"")
                .contains("smoke_sanitize_artifacts \"${ARTIFACT_DIR}\"\n  if [[ \"${KEEP_ARTIFACTS}\" == \"true\" ]]")
                .contains("} | tee \"${SUMMARY_FILE}\"\nsmoke_sanitize_artifacts \"${ARTIFACT_DIR}\"");
    }

    @Test
    @DisplayName("runtime cutover preflight는 JDBC URL 오류 로그에서 password/userinfo를 redaction한다")
    void runtimeCutoverPreflightRedactsJdbcUrlInFailureLogs() throws IOException {
        String script = Files.readString(RUNTIME_CUTOVER_PREFLIGHT);

        assertThat(script)
                .contains("redact_jdbc_url_for_log")
                .contains("<redacted>@")
                .contains("(password|sslpassword)")
                .contains("$(redact_jdbc_url_for_log \"${jdbc_url}\")")
                .doesNotContain("URL: ${jdbc_url}")
                .doesNotContain("database: ${jdbc_url}")
                .doesNotContain("schema name: ${jdbc_url}")
                .doesNotContain(": ${jdbc_url}\" >&2");
    }

    @Test
    @DisplayName("운영 배포 문서는 wrapper의 비파괴 strict render check와 제외 범위를 명시한다")
    void deploymentDocsDescribeProdCutoverWrapperScope() throws IOException {
        String doc = Files.readString(DEPLOYMENT_DOC);

        assertThat(doc)
                .contains("`run-prod-cutover-verification.sh` 는 1번의 strict render 가능성 확인과 2, 3, 5번을 한 번에 묶는 wrapper다.")
                .contains("실제 `.env.runtime.production` 을 덮어쓰지 않고 임시 0600 파일로 렌더 가능성만 확인한 뒤 삭제한다.")
                .contains("app/redis 재기동과 runtime API smoke는 포함하지 않으므로 별도로 실행한다.");
    }
}
