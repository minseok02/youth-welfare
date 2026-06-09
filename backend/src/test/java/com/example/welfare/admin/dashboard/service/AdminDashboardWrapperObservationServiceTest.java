package com.example.welfare.admin.dashboard.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AdminDashboardWrapperObservationServiceTest {

    private final AdminDashboardWrapperObservationService service =
            new AdminDashboardWrapperObservationService();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("active baseline/current priority summary를 파싱해 wrapper observation 응답으로 변환한다")
    void parsesWrapperObservationSummaries() throws Exception {
        Path activeBaseline = tempDir.resolve("latest-active-baseline-summary.txt");
        Path currentPriority = tempDir.resolve("latest-current-priority-summary.txt");
        Path currentPriorityRunDir = tempDir.resolve("20260603T041802Z");
        Path previousCurrentPriority = tempDir.resolve("20260603T041240Z");
        Files.createDirectories(currentPriorityRunDir);
        Files.createDirectories(previousCurrentPriority);
        Files.writeString(activeBaseline, """
                active_baseline_suite=passed
                ops_observation_status=passed
                ops_attention_feed_status=ok
                ops_attention_feed_item_count=1
                ops_attention_feed_warning_item_count=1
                ops_attention_feed_item_keys=standard-code-backlog
                ops_attention_feed_item_titles=표준코드 입력 backlog
                ops_user_profile_standard_code_users_with_any_standard_code=52
                ops_user_profile_standard_code_users_missing_all_standard_codes=793
                ops_recommendation_standard_code_housing_positive_rule_delta_rows=2
                ops_recommendation_standard_code_welfare_positive_rule_scenarios=4
                ops_recommendation_standard_code_welfare_max_rule_delta=54.00000
                """);
        Files.writeString(currentPriority, """
                current_priority_suite=passed
                active_baseline_reused=true
                active_baseline_attention_feed_status=ok
                active_baseline_attention_feed_item_count=1
                active_baseline_attention_feed_warning_item_count=1
                active_baseline_attention_feed_item_keys=standard-code-backlog
                active_baseline_attention_feed_item_titles=표준코드 입력 backlog
                active_baseline_user_profile_standard_code_users_with_any_standard_code=52
                active_baseline_user_profile_standard_code_users_missing_all_standard_codes=793
                recommendation_standard_code_observation_status=passed
                recommendation_standard_code_housing_positive_rule_delta_rows=2
                recommendation_standard_code_welfare_scenario_count=4
                recommendation_standard_code_welfare_positive_rule_scenarios=4
                recommendation_standard_code_welfare_max_rule_delta=54.00000
                """);
        Files.writeString(currentPriorityRunDir.resolve("current-priority-summary.txt"), """
                current_priority_suite=passed
                active_baseline_user_profile_standard_code_users_with_any_standard_code=52
                active_baseline_user_profile_standard_code_users_missing_all_standard_codes=793
                recommendation_standard_code_observation_status=passed
                recommendation_standard_code_housing_positive_rule_delta_rows=2
                recommendation_standard_code_welfare_scenario_count=4
                recommendation_standard_code_welfare_positive_rule_scenarios=4
                recommendation_standard_code_welfare_max_rule_delta=54.00000
                """);
        Files.writeString(previousCurrentPriority.resolve("current-priority-summary.txt"), """
                current_priority_suite=passed
                active_baseline_user_profile_standard_code_users_missing_all_standard_codes=796
                recommendation_standard_code_observation_status=skipped
                """);

        var response = service.fromSummaryPaths(activeBaseline, currentPriority);

        assertThat(response.activeBaselineAvailable()).isTrue();
        assertThat(response.activeBaselineStatus()).isEqualTo("passed");
        assertThat(response.activeBaselineAttentionFeedStatus()).isEqualTo("ok");
        assertThat(response.activeBaselineAttentionFeedItemCount()).isEqualTo(1);
        assertThat(response.activeBaselineAttentionFeedItemTitles()).isEqualTo("표준코드 입력 backlog");
        assertThat(response.activeBaselineUsersWithAnyStandardCode()).isEqualTo(52);
        assertThat(response.activeBaselineRecommendationWelfareMaxRuleDelta()).isEqualTo(54.0);
        assertThat(response.currentPriorityAvailable()).isTrue();
        assertThat(response.currentPriorityStatus()).isEqualTo("passed");
        assertThat(response.currentPriorityActiveBaselineReused()).isTrue();
        assertThat(response.currentPriorityAttentionFeedStatus()).isEqualTo("ok");
        assertThat(response.currentPriorityAttentionFeedItemCount()).isEqualTo(1);
        assertThat(response.currentPriorityAttentionFeedItemKeys()).isEqualTo("standard-code-backlog");
        assertThat(response.currentPriorityRecommendationObservationStatus()).isEqualTo("passed");
        assertThat(response.currentPriorityRecommendationWelfareScenarioCount()).isEqualTo(4);
        assertThat(response.currentPriorityPreviousAvailable()).isTrue();
        assertThat(response.currentPriorityPreviousUsersMissingAllStandardCodes()).isEqualTo(796);
        assertThat(response.currentPriorityUsersMissingAllStandardCodesDelta()).isEqualTo(-3);
        assertThat(response.currentPriorityUsersMissingAllStandardCodesDeltaLabel()).isEqualTo("3 감소");
        assertThat(response.currentPriorityPreviousRecommendationObservationStatus()).isEqualTo("skipped");
        assertThat(response.currentPriorityRecommendationObservationStatusChanged()).isTrue();
        assertThat(response.currentPriorityRecommendationObservationStatusTransitionLabel()).isEqualTo("skipped -> passed");
        assertThat(response.promotedAlert()).isNotNull();
        assertThat(response.promotedAlert().severity()).isEqualTo("success");
        assertThat(response.promotedAlert().title()).isEqualTo("개선 신호");
        assertThat(response.promotedAlert().message()).isEqualTo("표준코드 미입력 3 감소, priority 관측 skipped -> passed");
    }

    @ParameterizedTest
    @CsvSource({
            "801,796,failed,passed,warning,운영 주시 포인트,5 증가,passed -> failed",
            "793,796,passed,skipped,success,개선 신호,3 감소,skipped -> passed",
            "796,796,passed,passed,info,변화 없음,변화 없음,변화 없음"
    })
    @DisplayName("current priority 이전 상태 대비 promoted alert severity를 계산한다")
    void computesPromotedAlertSeverity(
            int currentMissingCount,
            int previousMissingCount,
            String currentObservationStatus,
            String previousObservationStatus,
            String expectedSeverity,
            String expectedTitle,
            String expectedDeltaLabel,
            String expectedTransitionLabel
    ) throws Exception {
        Path scenarioDir = tempDir.resolve("scenario-" + expectedSeverity);
        Files.createDirectories(scenarioDir);
        Path activeBaseline = scenarioDir.resolve("latest-active-baseline-summary.txt");
        Path currentPriority = scenarioDir.resolve("latest-current-priority-summary.txt");
        Path currentPriorityRunDir = scenarioDir.resolve("20260603T041802Z");
        Path previousCurrentPriority = scenarioDir.resolve("20260603T041240Z");
        Files.createDirectories(currentPriorityRunDir);
        Files.createDirectories(previousCurrentPriority);
        Files.writeString(activeBaseline, """
                active_baseline_suite=passed
                ops_observation_status=passed
                """);
        Files.writeString(currentPriority, """
                current_priority_suite=passed
                active_baseline_reused=true
                active_baseline_user_profile_standard_code_users_missing_all_standard_codes=%s
                recommendation_standard_code_observation_status=%s
                """.formatted(currentMissingCount, currentObservationStatus));
        Files.writeString(currentPriorityRunDir.resolve("current-priority-summary.txt"), """
                current_priority_suite=passed
                active_baseline_user_profile_standard_code_users_missing_all_standard_codes=%s
                recommendation_standard_code_observation_status=%s
                """.formatted(currentMissingCount, currentObservationStatus));
        Files.writeString(previousCurrentPriority.resolve("current-priority-summary.txt"), """
                current_priority_suite=passed
                active_baseline_user_profile_standard_code_users_missing_all_standard_codes=%s
                recommendation_standard_code_observation_status=%s
                """.formatted(previousMissingCount, previousObservationStatus));

        var response = service.fromSummaryPaths(activeBaseline, currentPriority);

        assertThat(response.currentPriorityUsersMissingAllStandardCodesDeltaLabel()).isEqualTo(expectedDeltaLabel);
        assertThat(response.currentPriorityRecommendationObservationStatusTransitionLabel()).isEqualTo(expectedTransitionLabel);
        assertThat(response.promotedAlert()).isNotNull();
        assertThat(response.promotedAlert().severity()).isEqualTo(expectedSeverity);
        assertThat(response.promotedAlert().title()).isEqualTo(expectedTitle);
    }

    @Test
    @DisplayName("summary가 없으면 unavailable 응답을 반환한다")
    void returnsUnavailableWhenSummariesMissing() {
        var response = service.fromSummaryPaths(
                tempDir.resolve("missing-active-baseline.txt"),
                tempDir.resolve("missing-current-priority.txt")
        );

        assertThat(response.activeBaselineAvailable()).isFalse();
        assertThat(response.currentPriorityAvailable()).isFalse();
        assertThat(response.activeBaselineUsersWithAnyStandardCode()).isZero();
        assertThat(response.currentPriorityRecommendationObservationStatus()).isBlank();
        assertThat(response.promotedAlert()).isNull();
    }

    @Test
    @DisplayName("이전 current priority summary의 missing count가 비어 있으면 0으로 비교하지 않는다")
    void ignoresBlankPreviousMissingCountForDelta() throws Exception {
        Path activeBaseline = tempDir.resolve("blank-previous-active-baseline.txt");
        Path currentPriority = tempDir.resolve("blank-previous-current-priority.txt");
        Path currentPriorityRunDir = tempDir.resolve("20260609T131404Z");
        Path previousCurrentPriority = tempDir.resolve("20260609T111911Z");
        Files.createDirectories(currentPriorityRunDir);
        Files.createDirectories(previousCurrentPriority);
        Files.writeString(activeBaseline, "active_baseline_suite=passed\n");
        Files.writeString(currentPriority, """
                current_priority_suite=passed
                active_baseline_user_profile_standard_code_users_missing_all_standard_codes=273
                recommendation_standard_code_observation_status=passed
                """);
        Files.writeString(currentPriorityRunDir.resolve("current-priority-summary.txt"), """
                current_priority_suite=passed
                active_baseline_user_profile_standard_code_users_missing_all_standard_codes=273
                recommendation_standard_code_observation_status=passed
                """);
        Files.writeString(previousCurrentPriority.resolve("current-priority-summary.txt"), """
                current_priority_suite=passed
                active_baseline_user_profile_standard_code_users_missing_all_standard_codes=
                recommendation_standard_code_observation_status=passed
                """);

        var response = service.fromSummaryPaths(activeBaseline, currentPriority);

        assertThat(response.currentPriorityPreviousAvailable()).isTrue();
        assertThat(response.currentPriorityPreviousUsersMissingAllStandardCodes()).isZero();
        assertThat(response.currentPriorityUsersMissingAllStandardCodesDelta()).isZero();
        assertThat(response.currentPriorityUsersMissingAllStandardCodesDeltaLabel()).isEqualTo("이전값 없음");
        assertThat(response.currentPriorityRecommendationObservationStatusTransitionLabel()).isEqualTo("변화 없음");
        assertThat(response.promotedAlert()).isNotNull();
        assertThat(response.promotedAlert().severity()).isEqualTo("info");
        assertThat(response.promotedAlert().message()).isEqualTo("표준코드 미입력 이전값 없음, priority 관측 변화 없음");
    }

    @Test
    @DisplayName("이전 current priority summary에 비교 키가 없으면 previousAvailable로 보지 않는다")
    void ignoresPreviousSummaryWithoutComparisonKeys() throws Exception {
        Path activeBaseline = tempDir.resolve("no-compare-active-baseline.txt");
        Path currentPriority = tempDir.resolve("no-compare-current-priority.txt");
        Path currentPriorityRunDir = tempDir.resolve("20260603T051000Z");
        Path previousCurrentPriority = tempDir.resolve("20260603T050500Z");
        Files.createDirectories(currentPriorityRunDir);
        Files.createDirectories(previousCurrentPriority);
        Files.writeString(activeBaseline, "active_baseline_suite=passed\n");
        Files.writeString(currentPriority, """
                current_priority_suite=passed
                active_baseline_user_profile_standard_code_users_missing_all_standard_codes=793
                recommendation_standard_code_observation_status=passed
                """);
        Files.writeString(currentPriorityRunDir.resolve("current-priority-summary.txt"), """
                current_priority_suite=passed
                active_baseline_user_profile_standard_code_users_missing_all_standard_codes=793
                recommendation_standard_code_observation_status=passed
                """);
        Files.writeString(previousCurrentPriority.resolve("current-priority-summary.txt"), """
                current_priority_suite=passed
                unrelated_key=value
                """);

        var response = service.fromSummaryPaths(activeBaseline, currentPriority);

        assertThat(response.currentPriorityPreviousAvailable()).isFalse();
        assertThat(response.currentPriorityUsersMissingAllStandardCodesDeltaLabel()).isEqualTo("이전값 없음");
        assertThat(response.currentPriorityRecommendationObservationStatusTransitionLabel()).isEqualTo("이전값 없음");
        assertThat(response.promotedAlert()).isNull();
    }
}
