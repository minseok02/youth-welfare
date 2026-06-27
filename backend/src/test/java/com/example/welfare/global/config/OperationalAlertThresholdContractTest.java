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
    private static final Path LOG_ALERT_EVALUATOR_SCRIPT = Path.of("../deploy/performance/evaluate-log-alert-thresholds.sh");
    private static final Path SEND_LOG_ALERT_SCRIPT = Path.of("../deploy/ops/send-log-alert.sh");
    private static final Path PERF_COMMON = Path.of("../deploy/performance/perf-common.sh");
    private static final Path APP_LOG_BASELINE_SCRIPT = Path.of("../deploy/performance/run-local-app-log-observability-baseline.sh");
    private static final Path NGINX_LOG_BASELINE_SCRIPT = Path.of("../deploy/performance/run-local-nginx-log-observability-baseline.sh");
    private static final Path TUNE_LOG_ALERT_SCRIPT = Path.of("../deploy/performance/tune-log-alert-thresholds.sh");

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

    @Test
    @DisplayName("운영 log alert 전파 경로는 stdout/webhook과 artifact를 redaction한다")
    void logAlertPropagationRedactsOutputsAndArtifacts() throws IOException {
        String sendAlert = Files.readString(SEND_LOG_ALERT_SCRIPT);
        String logEvaluator = Files.readString(LOG_ALERT_EVALUATOR_SCRIPT);
        String operationalEvaluator = Files.readString(EVALUATOR_SCRIPT);

        assertThat(sendAlert)
                .contains("source \"${ROOT_DIR}/deploy/smoke/smoke-common.sh\"")
                .contains("run_redacted_to_file()")
                .contains("| smoke_redact_stream_for_log > \"${output_file}\"")
                .contains("RAW_ALERT_OUTPUT=\"$(bash deploy/performance/evaluate-log-alert-thresholds.sh 2>&1)\"")
                .contains("ALERT_OUTPUT=\"$(printf '%s\\n' \"${RAW_ALERT_OUTPUT}\" | smoke_redact_stream_for_log)\"")
                .contains("send_alert_webhook \"${MESSAGE}\"");
        assertThat(logEvaluator)
                .contains("source \"${ROOT_DIR}/deploy/smoke/smoke-common.sh\"")
                .contains("<<'PY' | smoke_redact_stream_for_log");
        assertThat(operationalEvaluator)
                .contains("source \"${ROOT_DIR}/deploy/smoke/smoke-common.sh\"")
                .contains("<<'PY' | smoke_redact_stream_for_log");
    }

    @Test
    @DisplayName("app/nginx log observability artifact는 raw tail 저장 전후 모두 sanitizer 경계를 가진다")
    void logObservabilityArtifactsAreSanitizedOnCaptureAndExit() throws IOException {
        String perfCommon = Files.readString(PERF_COMMON);
        String appLogBaseline = Files.readString(APP_LOG_BASELINE_SCRIPT);
        String nginxLogBaseline = Files.readString(NGINX_LOG_BASELINE_SCRIPT);
        String tuneLogAlert = Files.readString(TUNE_LOG_ALERT_SCRIPT);

        assertThat(perfCommon)
                .contains("perf_sanitize_artifacts_on_exit()")
                .contains("smoke_sanitize_artifacts \"${PERF_SANITIZE_ARTIFACT_DIR}\"");
        assertThat(appLogBaseline)
                .contains("perf_sanitize_artifacts_on_exit \"${ARTIFACT_DIR}\"")
                .contains("tail -n \"${APP_LOG_TAIL_LINES}\" \"${APP_LOG_FILE}\" 2>/dev/null | smoke_redact_stream_for_log > \"${LOG_SAMPLE}\"")
                .contains("docker compose -f \"${APP_LOG_COMPOSE_FILE}\" logs --no-color --since=\"${APP_LOG_SINCE}\" \"${APP_LOG_COMPOSE_SERVICE}\" 2>/dev/null | smoke_redact_stream_for_log > \"${LOG_SAMPLE}\"");
        assertThat(nginxLogBaseline)
                .contains("perf_sanitize_artifacts_on_exit \"${ARTIFACT_DIR}\"")
                .contains("tail -n \"${NGINX_LOG_TAIL_LINES}\" \"${NGINX_ACCESS_LOG}\" 2>/dev/null | smoke_redact_stream_for_log > \"${ACCESS_SAMPLE}\"")
                .contains("tail -n 1000 \"${NGINX_ERROR_LOG}\" 2>/dev/null | smoke_redact_stream_for_log > \"${ERROR_SAMPLE}\"");
        assertThat(tuneLogAlert)
                .contains("perf_sanitize_artifacts_on_exit \"${ARTIFACT_DIR}\"")
                .contains("<<'PY' | smoke_redact_stream_for_log");
    }
}
