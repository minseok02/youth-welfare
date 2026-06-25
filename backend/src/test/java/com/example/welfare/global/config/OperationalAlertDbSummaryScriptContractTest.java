package com.example.welfare.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalAlertDbSummaryScriptContractTest {

    private static final Path SCRIPT = Path.of("../deploy/smoke/generate-operational-alert-db-summaries.sh");

    @Test
    @DisplayName("운영 alert DB summary helper는 evaluator가 읽는 recommendation/web push key를 생성한다")
    void scriptGeneratesEvaluatorInputKeys() throws IOException {
        String script = Files.readString(SCRIPT);

        assertThat(script)
                .contains("recommendation_error_10m")
                .contains("recommendation_no_candidates_30m")
                .contains("recommendation_failure_rate_30m_pct")
                .contains("recommendation_avg_duration_ms_30m")
                .contains("recommendation_traffic_without_success_1h")
                .contains("web_push_subscription_total")
                .contains("web_push_disabled_ratio_pct")
                .contains("web_push_disabled_or_gateway_failure_15m")
                .contains("notification_disabled_attempt_multiplier_1h");
    }

    @Test
    @DisplayName("운영 alert DB summary helper는 정해진 DB read model과 stable artifact를 사용한다")
    void scriptUsesStableDbSourcesAndArtifacts() throws IOException {
        String script = Files.readString(SCRIPT);

        assertThat(script)
                .contains("recommendation_run_logs")
                .contains("web_push_subscriptions")
                .contains("notification_attempt_logs")
                .contains("outcome in ('ERROR', 'NO_CANDIDATES')")
                .contains("channel = 'web_push'")
                .contains("outcome in ('disabled', 'failed', 'exception', 'fanout_failed', 'gateway_false')")
                .contains("latest-recommendation-run-summary.txt")
                .contains("latest-web-push-summary.txt")
                .contains("latest-operational-alert-db-summaries.json")
                .contains("RECOMMENDATION_RUN_SUMMARY")
                .contains("WEB_PUSH_SUMMARY")
                .contains("operational_alert_source_available")
                .contains("source_missing")
                .contains("to_regclass('public.recommendation_run_logs')")
                .contains("to_regclass('public.web_push_subscriptions')")
                .contains("to_regclass('public.notification_attempt_logs')");
    }
}
