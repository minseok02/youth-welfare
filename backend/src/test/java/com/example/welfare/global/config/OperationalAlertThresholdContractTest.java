package com.example.welfare.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalAlertThresholdContractTest {

    private static final Path THRESHOLD_DOC = Path.of("../docs/core/log-alert-thresholds.md");
    private static final Path VERIFY_SCRIPT = Path.of("../deploy/smoke/verify-operational-alert-thresholds.sh");
    private static final Path EVALUATOR_SCRIPT = Path.of("../deploy/smoke/evaluate-operational-alert-thresholds.sh");
    private static final Path DB_SUMMARY_SCRIPT = Path.of("../deploy/smoke/generate-operational-alert-db-summaries.sh");

    @Test
    @DisplayName("운영 alert threshold 문서는 dashboard 핵심 지표별 warning/critical 기준을 가진다")
    void thresholdDocumentContainsDashboardAlertContracts() throws IOException {
        String doc = Files.readString(THRESHOLD_DOC);

        List<String> alertIds = List.of(
                "COLLECT_FAILED_JOB_RATE",
                "SEARCH_ZERO_RESULT_RATE",
                "RECOMMENDATION_RUN_FAILURE_RATE",
                "NOTIFICATION_RETRY_BACKLOG",
                "WEB_PUSH_DISABLED_RATIO"
        );

        for (String alertId : alertIds) {
            assertThat(doc).contains("`" + alertId + "`");
        }

        assertThat(doc)
                .contains("warning:")
                .contains("critical:")
                .contains("collect_failed_jobs_in_window")
                .contains("chat_observability_window_7d_zero_result_rate_pct")
                .contains("recommendation_run_logs")
                .contains("retryable_failed_total")
                .contains("retryable_failed_due_now")
                .contains("terminal_failed_total")
                .contains("web_push_subscriptions")
                .contains("disabled subscription ratio");
    }

    @Test
    @DisplayName("운영 alert threshold 검증 스크립트는 같은 alert_id와 source artifact를 확인한다")
    void verificationScriptChecksDashboardAlertContracts() throws IOException {
        String script = Files.readString(VERIFY_SCRIPT);

        assertThat(script)
                .contains("COLLECT_FAILED_JOB_RATE")
                .contains("SEARCH_ZERO_RESULT_RATE")
                .contains("RECOMMENDATION_RUN_FAILURE_RATE")
                .contains("NOTIFICATION_RETRY_BACKLOG")
                .contains("WEB_PUSH_DISABLED_RATIO")
                .contains("warning:")
                .contains("critical:")
                .contains("run-local-ops-observation-suite.sh")
                .contains("run-local-chat-observability-audit.sh")
                .contains("run-local-notification-backlog-audit.sh")
                .contains("recommendation_run_logs")
                .contains("notification_attempt_logs")
                .contains("web_push_subscriptions");
    }

    @Test
    @DisplayName("운영 alert evaluator는 threshold 문서의 핵심 alert_id와 artifact 입력을 평가한다")
    void evaluatorScriptChecksDashboardAlertContracts() throws IOException {
        String script = Files.readString(EVALUATOR_SCRIPT);

        assertThat(script)
                .contains("OPS_SUMMARY")
                .contains("NOTIFICATION_BACKLOG_SUMMARY")
                .contains("CHAT_OBSERVABILITY_SUMMARY")
                .contains("RECOMMENDATION_RUN_SUMMARY")
                .contains("WEB_PUSH_SUMMARY")
                .contains("COLLECT_FAILED_JOB_RATE")
                .contains("SEARCH_ZERO_RESULT_RATE")
                .contains("RECOMMENDATION_RUN_FAILURE_RATE")
                .contains("NOTIFICATION_RETRY_BACKLOG")
                .contains("WEB_PUSH_DISABLED_RATIO")
                .contains("OP_ALERT_STATUS")
                .contains("OP_ALERT_COLLECT_WARN_FAILED_RATE_PCT")
                .contains("OP_ALERT_WEB_PUSH_CRIT_DISABLED_RATIO_PCT");
    }

    @Test
    @DisplayName("운영 alert DB summary helper는 evaluator의 선택 입력 artifact를 생성한다")
    void dbSummaryScriptGeneratesOptionalEvaluatorArtifacts() throws IOException {
        String script = Files.readString(DB_SUMMARY_SCRIPT);

        assertThat(script)
                .contains("recommendation_run_logs")
                .contains("web_push_subscriptions")
                .contains("notification_attempt_logs")
                .contains("latest-recommendation-run-summary.txt")
                .contains("latest-web-push-summary.txt")
                .contains("recommendation_failure_rate_30m_pct")
                .contains("web_push_disabled_ratio_pct");
    }
}
