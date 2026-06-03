package com.example.welfare.admin.dashboard.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AdminDashboardStandardCodeObservationServiceTest {

    private final AdminDashboardStandardCodeObservationService service =
            new AdminDashboardStandardCodeObservationService();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("latest observation summary를 파싱해 표준코드 효과 응답으로 변환한다")
    void parsesLatestObservationSummary() throws Exception {
        Path summary = tempDir.resolve("latest-recommendation-observation-summary.txt");
        Files.writeString(summary, """
                recommendation_observation_suite=passed
                precheck_status=SUPPLEMENTAL_REVIEW_ONLY
                decision_class=SUPPLEMENTAL_POLICY_REVIEW
                housing_standard_code_effect_status=ok
                housing_standard_code_effect_positive_rule_delta_rows=2
                housing_standard_code_effect_positive_final_delta_rows=4
                housing_standard_code_effect_max_rule_delta=24.00000
                housing_standard_code_effect_max_final_delta=0.13440
                housing_standard_code_effect_top_positive_rule_delta_rows=3259:sample
                welfare_standard_code_matrix_status=ok
                welfare_standard_code_matrix_scenario_count=4
                welfare_standard_code_matrix_positive_rule_scenarios=4
                welfare_standard_code_matrix_positive_final_scenarios=4
                welfare_standard_code_matrix_max_rule_delta_scenario=basic_living_and_housing_combo
                welfare_standard_code_matrix_max_rule_delta=54.00000
                welfare_standard_code_matrix_max_final_delta_scenario=basic_living_only
                welfare_standard_code_matrix_max_final_delta=0.23586
                welfare_standard_code_matrix_scenario_rule_delta_snapshot=basic_living_only:5:30.00000
                """);

        var response = service.fromSummaryPath(summary);

        assertThat(response.available()).isTrue();
        assertThat(response.precheckStatus()).isEqualTo("SUPPLEMENTAL_REVIEW_ONLY");
        assertThat(response.decisionClass()).isEqualTo("SUPPLEMENTAL_POLICY_REVIEW");
        assertThat(response.housingEffectStatus()).isEqualTo("ok");
        assertThat(response.housingPositiveRuleDeltaRows()).isEqualTo(2);
        assertThat(response.housingMaxRuleDelta()).isEqualTo(24.0);
        assertThat(response.welfareMatrixStatus()).isEqualTo("ok");
        assertThat(response.welfareScenarioCount()).isEqualTo(4);
        assertThat(response.welfareMaxRuleDeltaScenario()).isEqualTo("basic_living_and_housing_combo");
        assertThat(response.welfareMaxFinalDelta()).isEqualTo(0.23586);
    }

    @Test
    @DisplayName("summary가 없으면 unavailable 응답을 반환한다")
    void returnsUnavailableWhenSummaryMissing() {
        var response = service.fromSummaryPath(tempDir.resolve("missing-summary.txt"));

        assertThat(response.available()).isFalse();
        assertThat(response.housingEffectStatus()).isEqualTo("missing");
        assertThat(response.welfareMatrixStatus()).isEqualTo("missing");
    }
}
