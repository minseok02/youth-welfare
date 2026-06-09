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
    @DisplayName("fresh schema는 관리자 정책 검수 read model 테이블을 포함한다")
    void schemaSqlContainsAdminPolicyReviewTables() throws IOException {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));

        assertThat(schema).contains("CREATE TABLE IF NOT EXISTS policy_duplicate_review_records");
        assertThat(schema).contains("CREATE UNIQUE INDEX IF NOT EXISTS uq_policy_duplicate_review_records_group");
        assertThat(schema).contains("CREATE TABLE IF NOT EXISTS policy_link_review_records");
        assertThat(schema).contains("CREATE INDEX IF NOT EXISTS idx_plrr_reviewed_at");
    }
}
