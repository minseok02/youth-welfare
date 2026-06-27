package com.example.welfare.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SmokeArtifactPermissionContractTest {

    private static final Path SMOKE_COMMON = Path.of("../deploy/smoke/smoke-common.sh");
    private static final Path RUNTIME_ENV_RENDER = Path.of("../deploy/env/render-app-runtime-env.sh");
    private static final Path PROD_CUTOVER = Path.of("../deploy/smoke/run-prod-cutover-verification.sh");
    private static final Path NIGHTLY_OPS_HANDOFF = Path.of("../deploy/smoke/run-nightly-ops-handoff.sh");

    private static final List<Path> TOP_LEVEL_HANDOFF_WRAPPERS = List.of(
            Path.of("../deploy/smoke/run-local-active-baseline-suite.sh"),
            Path.of("../deploy/smoke/run-local-current-priority-suite.sh")
    );

    @Test
    @DisplayName("smoke 공통 유틸은 artifact directory 700, file 600 권한 제한 함수를 제공한다")
    void smokeCommonProvidesArtifactPermissionHelpers() throws IOException {
        String script = Files.readString(SMOKE_COMMON);

        assertThat(script)
                .contains("smoke_secure_mkdir()")
                .contains("chmod 700 \"${dir}\"")
                .contains("smoke_restrict_artifact_permissions()")
                .contains("chmod 700 \"${artifact_dir}\"")
                .contains("find \"${artifact_dir}\" -type d -exec chmod 700 {} +")
                .contains("find \"${artifact_dir}\" -type f -exec chmod 600 {} +");
    }

    @Test
    @DisplayName("smoke sanitize/publish 경로는 latest artifact를 700/600 권한으로 제한한다")
    void smokeSanitizeAndPublishRestrictRetainedArtifactPermissions() throws IOException {
        String script = Files.readString(SMOKE_COMMON);

        assertThat(script)
                .contains("smoke_sanitize_artifacts()")
                .contains("smoke_restrict_artifact_permissions \"${artifact_dir}\"")
                .contains("smoke_publish_file()")
                .contains("install -m 600 \"${source_file}\" \"${dest_file}\"")
                .contains("smoke_publish_dir_snapshot()")
                .contains("smoke_secure_mkdir \"${dest_parent}\"")
                .contains("smoke_secure_mkdir \"${tmp_dir}\"")
                .contains("smoke_restrict_artifact_permissions \"${source_dir}\"")
                .contains("smoke_restrict_artifact_permissions \"${tmp_dir}\"")
                .contains("smoke_restrict_artifact_permissions \"${dest_dir}\"")
                .contains("smoke_update_links()")
                .contains("smoke_secure_mkdir \"${link_dir}\"");
    }

    @Test
    @DisplayName("runtime env render는 077 umask와 install -m 600으로 secret-like 파일을 만든다")
    void runtimeEnvRenderCreatesSecretFileWithOwnerOnlyPermissions() throws IOException {
        String script = Files.readString(RUNTIME_ENV_RENDER);

        assertThat(script)
                .contains("umask 077")
                .contains("tmp_file=\"$(mktemp \"${TARGET_ENV_DIR}/.runtime-env.XXXXXX\")\"")
                .contains("install -m 600 \"${tmp_file}\" \"${TARGET_ENV_FILE}\"");
    }

    @Test
    @DisplayName("prod cutover wrapper는 artifact와 임시 runtime render directory를 700으로 제한한다")
    void prodCutoverWrapperRestrictsArtifactAndRuntimeRenderDirectories() throws IOException {
        String script = Files.readString(PROD_CUTOVER);

        assertThat(script)
                .contains("chmod 700 \"${ARTIFACT_DIR}\"")
                .contains("RUNTIME_ENV_RENDER_TMP_DIR=\"$(mktemp -d)\"")
                .contains("chmod 700 \"${RUNTIME_ENV_RENDER_TMP_DIR}\"")
                .contains("rm -rf \"${RUNTIME_ENV_RENDER_TMP_DIR}\"");
    }

    @Test
    @DisplayName("상위 handoff wrapper는 publish/cleanup 전에 sanitizer를 호출해 권한 제한까지 적용한다")
    void topLevelHandoffWrappersApplySanitizerBeforeRetainingArtifacts() throws IOException {
        for (Path wrapper : TOP_LEVEL_HANDOFF_WRAPPERS) {
            String script = Files.readString(wrapper);

            assertThat(script)
                    .as(wrapper.toString())
                    .contains("smoke_sanitize_artifacts \"${ARTIFACT_DIR}\"")
                    .contains("smoke_publish_dir_snapshot \"${ARTIFACT_DIR}\" \"${LATEST_ARTIFACT_LINK}\"");
        }

        assertThat(Files.readString(NIGHTLY_OPS_HANDOFF))
                .contains("smoke_sanitize_artifacts \"${RUN_DIR}\"")
                .contains("trap cleanup EXIT")
                .contains("bash \"${script_path}\" | tee \"${output_file}\"\n  smoke_sanitize_artifacts \"${RUN_DIR}\"");
    }

    @Test
    @DisplayName("문서는 운영 artifact 권한 제한 기준을 기록한다")
    void docsDescribeArtifactPermissionBoundary() throws IOException {
        assertThat(Files.readString(Path.of("../docs/core/deep-audit-checklist.md")))
                .contains("smoke 공통 sanitize/publish 경로는 retained artifact directory를 `700`, artifact file을 `600`으로 제한한다.")
                .contains("runtime env render는 `umask 077` 과 `install -m 600` 으로 `.env.runtime.production` 계열 파일을 만든다.");

        assertThat(Files.readString(Path.of("../docs/core/security-hardening-current-state.md")))
                .contains("retained smoke artifact directory는 `700`, artifact file은 `600`으로 제한한다.")
                .contains("runtime env render는 `umask 077` 과 `install -m 600` 으로 runtime env 파일을 생성한다.");

        assertThat(Files.readString(Path.of("../docs/phase-plan.md")))
                .contains("운영 artifact directory / secret-like 파일 권한도 공통 경로로 고정했다.")
                .contains("`smoke_sanitize_artifacts` 는 redaction 뒤 artifact tree의 directory `700`, file `600`을 적용하고")
                .contains("`smoke_publish_file` 은 `install -m 600`");
    }
}
