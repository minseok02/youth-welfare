package com.example.welfare.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalAlertEvaluatorScriptTest {

    private static final Path EVALUATOR_SCRIPT = Path.of("../deploy/smoke/evaluate-operational-alert-thresholds.sh");

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("운영 alert evaluator는 정상 artifact에서 ok를 출력하고 DB/API 선택 입력은 skipped로 둔다")
    void evaluatorReturnsOkForHealthyRequiredArtifacts() throws Exception {
        Fixture fixture = writeFixture(
                """
                collect_failed_jobs_in_window=0
                collect_partial_success_jobs_in_window=0
                open_collect_circuits=0
                collect_lane_count=10
                chat_observability_window_7d_zero_result_rate_pct=19.23
                """,
                """
                retryable_failed_total=0
                retryable_failed_due_now=0
                terminal_failed_total=0
                """,
                """
                window_7d_zero_result_rate_pct=19.23
                window_7d_zero_result_non_branch_rate_pct=0.00
                """
        );

        ScriptResult result = runEvaluator(Map.of(
                "OPS_SUMMARY", fixture.ops().toString(),
                "NOTIFICATION_BACKLOG_SUMMARY", fixture.notification().toString(),
                "CHAT_OBSERVABILITY_SUMMARY", fixture.chat().toString()
        ));

        assertThat(result.exitCode()).isZero();
        assertThat(result.output())
                .contains("OP_ALERT_STATUS=ok")
                .contains("skipped=RECOMMENDATION_RUN_FAILURE_RATE")
                .contains("skipped=WEB_PUSH_DISABLED_RATIO")
                .doesNotContain("critical=")
                .doesNotContain("warning=");
    }

    @Test
    @DisplayName("운영 alert evaluator는 retry terminal failure를 critical로 출력한다")
    void evaluatorReturnsCriticalForTerminalNotificationFailure() throws Exception {
        Fixture fixture = writeFixture(
                """
                collect_failed_jobs_in_window=0
                collect_partial_success_jobs_in_window=0
                open_collect_circuits=0
                collect_lane_count=10
                chat_observability_window_7d_zero_result_rate_pct=0
                """,
                """
                retryable_failed_total=0
                retryable_failed_due_now=0
                terminal_failed_total=1
                """,
                """
                window_7d_zero_result_rate_pct=0
                window_7d_zero_result_non_branch_rate_pct=0
                """
        );

        ScriptResult result = runEvaluator(Map.of(
                "OPS_SUMMARY", fixture.ops().toString(),
                "NOTIFICATION_BACKLOG_SUMMARY", fixture.notification().toString(),
                "CHAT_OBSERVABILITY_SUMMARY", fixture.chat().toString()
        ));

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.output())
                .contains("OP_ALERT_STATUS=critical")
                .contains("critical=NOTIFICATION_RETRY_BACKLOG: terminal_failed_total 1 > 0");
    }

    @Test
    @DisplayName("운영 alert evaluator는 선택 DB summary source가 없으면 해당 alert만 skipped로 둔다")
    void evaluatorSkipsUnavailableOptionalDbSummaries() throws Exception {
        Fixture fixture = writeFixture(
                """
                collect_failed_jobs_in_window=0
                collect_partial_success_jobs_in_window=0
                open_collect_circuits=0
                collect_lane_count=10
                chat_observability_window_7d_zero_result_rate_pct=0
                """,
                """
                retryable_failed_total=0
                retryable_failed_due_now=0
                terminal_failed_total=0
                """,
                """
                window_7d_zero_result_rate_pct=0
                window_7d_zero_result_non_branch_rate_pct=0
                """
        );
        Path recommendation = tempDir.resolve("recommendation-summary.txt");
        Path webPush = tempDir.resolve("web-push-summary.txt");
        Files.writeString(recommendation, """
                operational_alert_source_available=false
                source_missing=recommendation_run_logs
                """, StandardCharsets.UTF_8);
        Files.writeString(webPush, """
                operational_alert_source_available=false
                source_missing=web_push_subscriptions,notification_attempt_logs
                """, StandardCharsets.UTF_8);

        ScriptResult result = runEvaluator(Map.of(
                "OPS_SUMMARY", fixture.ops().toString(),
                "NOTIFICATION_BACKLOG_SUMMARY", fixture.notification().toString(),
                "CHAT_OBSERVABILITY_SUMMARY", fixture.chat().toString(),
                "RECOMMENDATION_RUN_SUMMARY", recommendation.toString(),
                "WEB_PUSH_SUMMARY", webPush.toString()
        ));

        assertThat(result.exitCode()).isZero();
        assertThat(result.output())
                .contains("OP_ALERT_STATUS=ok")
                .contains("skipped=RECOMMENDATION_RUN_FAILURE_RATE: source unavailable recommendation_run_logs")
                .contains("skipped=WEB_PUSH_DISABLED_RATIO: source unavailable web_push_subscriptions,notification_attempt_logs");
    }

    private Fixture writeFixture(String ops, String notification, String chat) throws IOException {
        Path opsPath = tempDir.resolve("ops-summary.txt");
        Path notificationPath = tempDir.resolve("notification-summary.txt");
        Path chatPath = tempDir.resolve("chat-summary.txt");
        Files.writeString(opsPath, ops, StandardCharsets.UTF_8);
        Files.writeString(notificationPath, notification, StandardCharsets.UTF_8);
        Files.writeString(chatPath, chat, StandardCharsets.UTF_8);
        return new Fixture(opsPath, notificationPath, chatPath);
    }

    private ScriptResult runEvaluator(Map<String, String> env) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder("bash", EVALUATOR_SCRIPT.toString())
                .directory(Path.of(".").toFile())
                .redirectErrorStream(true);
        processBuilder.environment().putAll(env);
        Process process = processBuilder.start();
        boolean finished = process.waitFor(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        assertThat(finished).isTrue();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ScriptResult(process.exitValue(), output);
    }

    private record Fixture(Path ops, Path notification, Path chat) {
    }

    private record ScriptResult(int exitCode, String output) {
    }
}
