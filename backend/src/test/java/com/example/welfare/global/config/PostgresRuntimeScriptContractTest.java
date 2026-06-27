package com.example.welfare.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresRuntimeScriptContractTest {

    private static final Path RDS_BOOTSTRAP = Path.of("../deploy/postgres/bootstrap-rds-runtime.sh");
    private static final Path RUNTIME_CUTOVER_PREFLIGHT = Path.of("../deploy/smoke/preflight-runtime-cutover-env.sh");
    private static final Path RUNTIME_ENV_RENDER = Path.of("../deploy/env/render-app-runtime-env.sh");
    private static final Path OPERATIONAL_DB_AUDIT = Path.of("../deploy/postgres/audit-operational-db-state.sh");
    private static final Path NIGHTLY_OPS_HANDOFF = Path.of("../deploy/smoke/run-nightly-ops-handoff.sh");

    @Test
    @DisplayName("RDS bootstrap은 앱에서 쓰는 PostgreSQL runtime role을 fresh DB에도 모두 생성한다")
    void rdsBootstrapCreatesAllRuntimeRoles() throws IOException {
        String script = Files.readString(RDS_BOOTSTRAP);

        List<String> roleVariables = List.of(
                "db_username",
                "db_app_pii_username",
                "db_notification_pii_ro_username",
                "db_admin_ro_username",
                "db_recommendation_review_gate_command_username",
                "db_recommendation_persistence_command_username",
                "db_chat_session_cleanup_username",
                "db_cluster_ai_cleanup_username",
                "db_recommendation_retention_cleanup_username",
                "db_collect_execution_lock_cleanup_username",
                "db_web_push_subscription_cleanup_username",
                "db_migration_username"
        );

        for (String roleVariable : roleVariables) {
            assertThat(script)
                    .contains("CREATE ROLE %I LOGIN PASSWORD %L', :'" + roleVariable + "'");
            assertThat(script)
                    .contains("ALTER ROLE %I LOGIN PASSWORD %L', :'" + roleVariable + "'");
        }
    }

    @Test
    @DisplayName("RDS bootstrap은 app_core_rw에서 전용 command/cleanup DELETE 권한을 회수한다")
    void rdsBootstrapRevokesDeletePrivilegesMovedToDedicatedRoles() throws IOException {
        String script = Files.readString(RDS_BOOTSTRAP);

        assertThat(script).contains("REVOKE DELETE ON TABLE public.chat_sessions");
        assertThat(script).contains("REVOKE DELETE ON TABLE public.cluster_ai_results");
        assertThat(script).contains("REVOKE DELETE ON TABLE public.collect_execution_locks");
        assertThat(script).contains("REVOKE DELETE ON TABLE public.web_push_subscriptions");
        assertThat(script).contains("REVOKE DELETE ON TABLE public.recent_policy_views");
        assertThat(script).contains("REVOKE DELETE ON TABLE public.user_recommendations");
        assertThat(script).contains("REVOKE ALL PRIVILEGES ON TABLE public.recommendation_review_gate_promotion_approvals");
    }

    @Test
    @DisplayName("runtime cutover preflight는 운영 DB role password 재사용을 기본 차단한다")
    void runtimeCutoverPreflightEnforcesDistinctRuntimeDbPasswords() throws IOException {
        String script = Files.readString(RUNTIME_CUTOVER_PREFLIGHT);

        assertThat(script)
                .contains("ALLOW_SHARED_RUNTIME_DB_PASSWORDS")
                .contains("assert_distinct_runtime_db_passwords")
                .contains("runtime DB role passwords must be distinct")
                .contains("DB_APP_PII_PASSWORD")
                .contains("DB_NOTIFICATION_PII_RO_PASSWORD")
                .contains("DB_RECOMMENDATION_PERSISTENCE_COMMAND_PASSWORD")
                .contains("runtime DB password separation: ");
    }

    @Test
    @DisplayName("runtime cutover preflight는 recommendation command role 누락을 DB_URL/DB_PASSWORD로 흡수하지 않는다")
    void runtimeCutoverPreflightDoesNotFallbackRecommendationCommandRolesToPrimaryDb() throws IOException {
        String script = Files.readString(RUNTIME_CUTOVER_PREFLIGHT);

        assertThat(script)
                .doesNotContain("RECOMMENDATION_REVIEW_GATE_COMMAND_DB_URL=\"${RECOMMENDATION_REVIEW_GATE_COMMAND_DB_URL:-${DB_URL:-}}\"")
                .doesNotContain("DB_RECOMMENDATION_REVIEW_GATE_COMMAND_PASSWORD=\"${DB_RECOMMENDATION_REVIEW_GATE_COMMAND_PASSWORD:-${DB_PASSWORD:-}}\"")
                .doesNotContain("RECOMMENDATION_PERSISTENCE_COMMAND_DB_URL=\"${RECOMMENDATION_PERSISTENCE_COMMAND_DB_URL:-${DB_URL:-}}\"")
                .doesNotContain("DB_RECOMMENDATION_PERSISTENCE_COMMAND_PASSWORD=\"${DB_RECOMMENDATION_PERSISTENCE_COMMAND_PASSWORD:-${DB_PASSWORD:-}}\"");
    }

    @Test
    @DisplayName("runtime env render는 prod app 필수 key 누락을 조용히 건너뛰지 않는다")
    void runtimeEnvRenderRequiresProdAppRuntimeKeysBeforeWritingTarget() throws IOException {
        String script = Files.readString(RUNTIME_ENV_RENDER);

        assertThat(script)
                .contains("STRICT_REQUIRED_RUNTIME_ENV=\"${STRICT_REQUIRED_RUNTIME_ENV:-true}\"")
                .contains("required_keys=(")
                .contains("validate_required_keys")
                .contains("missing required runtime env key in source file")
                .contains("RECOMMENDATION_REVIEW_GATE_COMMAND_DB_URL")
                .contains("RECOMMENDATION_PERSISTENCE_COMMAND_DB_URL")
                .contains("DB_RECOMMENDATION_REVIEW_GATE_COMMAND_PASSWORD")
                .contains("DB_RECOMMENDATION_PERSISTENCE_COMMAND_PASSWORD")
                .contains("AUTH_REFRESH_COOKIE_SECURE")
                .doesNotContain("DB_MIGRATION_USERNAME\n")
                .doesNotContain("DB_MIGRATION_PASSWORD\n");
    }

    @Test
    @DisplayName("RDS bootstrap/verify는 recommendation command role password를 DB_PASSWORD로 흡수하지 않는다")
    void rdsBootstrapAndVerifyDoNotFallbackRecommendationCommandRolePasswordsToPrimaryPassword() throws IOException {
        String bootstrap = Files.readString(RDS_BOOTSTRAP);
        String verify = Files.readString(Path.of("../deploy/postgres/verify-rds-runtime-privileges.sh"));

        assertThat(bootstrap)
                .doesNotContain("DB_RECOMMENDATION_REVIEW_GATE_COMMAND_PASSWORD=\"${DB_RECOMMENDATION_REVIEW_GATE_COMMAND_PASSWORD:-${DB_PASSWORD:-}}\"")
                .doesNotContain("DB_RECOMMENDATION_PERSISTENCE_COMMAND_PASSWORD=\"${DB_RECOMMENDATION_PERSISTENCE_COMMAND_PASSWORD:-${DB_PASSWORD:-}}\"");
        assertThat(verify)
                .doesNotContain("DB_RECOMMENDATION_REVIEW_GATE_COMMAND_PASSWORD=\"${DB_RECOMMENDATION_REVIEW_GATE_COMMAND_PASSWORD:-${DB_PASSWORD:-}}\"")
                .doesNotContain("DB_RECOMMENDATION_PERSISTENCE_COMMAND_PASSWORD=\"${DB_RECOMMENDATION_PERSISTENCE_COMMAND_PASSWORD:-${DB_PASSWORD:-}}\"");
    }

    @Test
    @DisplayName("RDS privilege verify는 admin read-only와 notification PII 최소권한을 확인한다")
    void rdsPrivilegeVerifyChecksReadOnlyAndPiiBoundaries() throws IOException {
        String script = Files.readString(Path.of("../deploy/postgres/verify-rds-runtime-privileges.sh"));

        assertThat(script)
                .contains("admin_ro_policy_error_reports_select")
                .contains("admin_ro_support_inquiries_select")
                .contains("admin_ro_policy_duplicate_review_records_select")
                .contains("admin_ro_notification_attempt_logs_select")
                .contains("admin_ro_support_inquiries_insert f")
                .contains("admin_ro_support_inquiries_update f")
                .contains("admin_ro_support_inquiries_delete f")
                .contains("notification_pii_ro_user_pii_select_user_key t")
                .contains("notification_pii_ro_user_pii_select_email_enc t")
                .contains("notification_pii_ro_user_pii_select_name_enc f")
                .contains("notification_pii_ro_user_pii_select_birth_date_enc f")
                .contains("notification_pii_ro_user_pii_insert f");
    }

    @Test
    @DisplayName("fresh schema는 관리자 정책 검수 read model 테이블을 포함한다")
    void schemaSqlContainsAdminPolicyReviewTables() throws IOException {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));

        assertThat(schema).contains("CREATE TABLE IF NOT EXISTS policy_duplicate_review_records");
        assertThat(schema).contains("CREATE UNIQUE INDEX IF NOT EXISTS uq_policy_duplicate_review_records_group");
        assertThat(schema).contains("CREATE TABLE IF NOT EXISTS policy_link_review_records");
        assertThat(schema).contains("CREATE INDEX IF NOT EXISTS idx_plrr_reviewed_at");
    }

    @Test
    @DisplayName("fresh schema와 runtime migration은 수동 DB 변경 이력 테이블을 포함한다")
    void schemaAndMigrationContainSchemaMigrationHistory() throws IOException {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V2026_06_27_01__add_schema_migration_history.sql"));
        String patch = Files.readString(Path.of(
                "../deploy/postgres/patches/V2026_06_27_01__add_schema_migration_history.sql"));

        for (String sql : List.of(schema, migration, patch)) {
            assertThat(sql)
                    .contains("CREATE TABLE IF NOT EXISTS schema_migration_history")
                    .contains("script_sha256 VARCHAR(64) NOT NULL")
                    .contains("CONSTRAINT uq_smh_script_name UNIQUE (script_name)")
                    .contains("CREATE INDEX IF NOT EXISTS idx_smh_applied_at");
        }
        assertThat(migration).contains("GRANT SELECT ON TABLE public.schema_migration_history TO admin_dashboard_ro");
        assertThat(patch).contains(":'admin_ro_username'");
    }

    @Test
    @DisplayName("fresh schema와 runtime migration은 user projection 테이블을 users에 FK로 묶는다")
    void schemaAndMigrationContainUserProjectionForeignKeys() throws IOException {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V2026_06_27_02__add_user_projection_foreign_keys.sql"));
        String patch = Files.readString(Path.of(
                "../deploy/postgres/patches/V2026_06_27_02__add_user_projection_foreign_keys.sql"));

        for (String sql : List.of(schema, migration, patch)) {
            assertThat(sql)
                    .contains("fk_auth_users_user_key")
                    .contains("fk_user_profiles_user_key")
                    .contains("fk_user_pii_user_key")
                    .contains("REFERENCES public.users(user_key)")
                    .contains("ON DELETE CASCADE");
        }
    }

    @Test
    @DisplayName("운영 DB audit helper는 권한 경계 밖의 쓰기 없이 핵심 무결성/락/이력 상태를 조회한다")
    void operationalDbAuditChecksCoreReadOnlySignals() throws IOException {
        String script = Files.readString(OPERATIONAL_DB_AUDIT);

        assertThat(script)
                .contains("AUDIT_OPERATIONAL_DB_STATE")
                .contains("auth_without_users")
                .contains("profiles_without_users")
                .contains("active_users_without_pii")
                .contains("withdrawn_or_inactive_users_without_pii")
                .contains("chat_snapshots_nonnull_orphan_session")
                .contains("notification_failed_like")
                .contains("active_queries_over_5m")
                .contains("waiting_locks")
                .contains("schema_migration_history")
                .contains("fk_auth_users_user_key")
                .contains("fk_user_profiles_user_key")
                .contains("fk_user_pii_user_key")
                .doesNotContain("DELETE FROM")
                .doesNotContain("UPDATE ")
                .doesNotContain("INSERT INTO");
    }

    @Test
    @DisplayName("nightly ops handoff는 운영 DB audit를 기본 artifact로 남긴다")
    void nightlyOpsHandoffRunsOperationalDbAudit() throws IOException {
        String script = Files.readString(NIGHTLY_OPS_HANDOFF);

        assertThat(script)
                .contains("OPERATIONAL_DB_AUDIT_SCRIPT")
                .contains("deploy/postgres/audit-operational-db-state.sh")
                .contains("OPERATIONAL_DB_AUDIT_OUTPUT")
                .contains("RUN_OPERATIONAL_DB_AUDIT=\"${RUN_OPERATIONAL_DB_AUDIT:-true}\"")
                .contains("run_step \"${RUN_OPERATIONAL_DB_AUDIT}\" \"operational_db_audit\"")
                .contains("export SMOKE_TRUSTED_ORIGIN=\"${SMOKE_TRUSTED_ORIGIN:-${FRONTEND_PUBLIC_BASE_URL}}\"")
                .contains("export SMOKE_TRUSTED_REFERER=\"${SMOKE_TRUSTED_REFERER:-${SMOKE_TRUSTED_ORIGIN}/}\"")
                .contains("db_audit=%s")
                .contains("operational_db_audit_output=%s");
    }

    @Test
    @DisplayName("fresh schema와 runtime patch는 chat 세션 삭제 시 retrieval snapshot도 cascade 정리한다")
    void chatRetrievalSnapshotsCascadeWithSessionDelete() throws IOException {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V2026_06_21_01__cascade_chat_retrieval_snapshots.sql"));
        String patch = Files.readString(Path.of(
                "../deploy/postgres/patches/V2026_06_21_01__cascade_chat_retrieval_snapshots.sql"));

        assertThat(schema)
                .contains("CONSTRAINT fk_crs_session FOREIGN KEY (session_id) REFERENCES chat_sessions(id) ON DELETE CASCADE")
                .contains("CREATE INDEX IF NOT EXISTS idx_crs_session_id");
        assertThat(migration)
                .contains("DELETE FROM chat_retrieval_snapshots crs")
                .contains("ADD CONSTRAINT fk_crs_session")
                .contains("ON DELETE CASCADE");
        assertThat(patch)
                .contains("DELETE FROM chat_retrieval_snapshots crs")
                .contains("ADD CONSTRAINT fk_crs_session")
                .contains("ON DELETE CASCADE");
    }

    @Test
    @DisplayName("local runtime patch는 운영 alert DB source 테이블 drift를 복구한다")
    void localRuntimePatchContainsOperationalAlertSources() throws IOException {
        String script = Files.readString(Path.of("../deploy/postgres/apply-local-runtime-schema-patch.sh"));
        String webPushPatch = Files.readString(Path.of(
                "../deploy/postgres/patches/V2026_05_15_03__add_web_push_subscriptions.sql"));
        String recommendationPatch = Files.readString(Path.of(
                "../deploy/postgres/patches/V2026_06_23_01__add_recommendation_run_logs.sql"));
        String notificationPatch = Files.readString(Path.of(
                "../deploy/postgres/patches/V2026_06_23_02__add_notification_attempt_logs.sql"));

        assertThat(script)
                .contains("patches=(\"${PATCH_DIR}\"/*.sql)")
                .contains("web_push_subscription_cleanup_username")
                .contains("recommendation_persistence_command_username")
                .contains("admin_ro_username")
                .contains("migration_username");
        assertThat(webPushPatch)
                .contains("CREATE TABLE IF NOT EXISTS web_push_subscriptions")
                .contains("CREATE INDEX IF NOT EXISTS idx_wps_user_key_enabled_created");
        assertThat(recommendationPatch)
                .contains("CREATE TABLE IF NOT EXISTS recommendation_run_logs")
                .contains("GRANT SELECT ON TABLE recommendation_run_logs TO admin_dashboard_ro")
                .contains("GRANT USAGE, SELECT ON SEQUENCE recommendation_run_logs_id_seq TO app_core_rw");
        assertThat(notificationPatch)
                .contains("CREATE TABLE IF NOT EXISTS notification_attempt_logs")
                .contains("GRANT SELECT ON TABLE notification_attempt_logs TO admin_dashboard_ro")
                .contains("GRANT USAGE, SELECT ON SEQUENCE notification_attempt_logs_id_seq TO app_core_rw");
    }
}
