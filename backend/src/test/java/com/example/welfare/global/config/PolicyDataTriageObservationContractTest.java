package com.example.welfare.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyDataTriageObservationContractTest {

    private static final Path SCRIPT = Path.of("../deploy/smoke/run-local-policy-data-triage-observation-suite.sh");
    private static final Path RUNBOOK = Path.of("../docs/policy/policy-data-triage-observation-runbook.md");
    private static final Path TRIAGE_RUNBOOK = Path.of("../docs/policy/policy-data-quality-triage-runbook.md");
    private static final Path CURRENT_STATE = Path.of("../docs/current-state.md");

    @Test
    @DisplayName("policy data triage wrapper는 정책 오류 제보 OPEN queue를 duplicate/link보다 먼저 본다")
    void wrapperPrioritizesOpenPolicyErrorReports() throws IOException {
        String script = Files.readString(SCRIPT);

        assertThat(script)
                .contains("policy_error_reports")
                .contains("policy_error_open_reports")
                .contains("policy_error_recent_open_reports_24h")
                .contains("policy_error_open_broken_link_reports")
                .contains("policy_error_open_region_reports")
                .contains("POLICY_ERROR_REPORT_PRIORITY")
                .contains("사용자가 남긴 정책 오류 제보 OPEN queue가 있습니다.")
                .contains("1. 열린 `정책 오류 제보` 운영 queue");

        assertThat(script.indexOf("if policy_error_open_reports > 0:"))
                .isLessThan(script.indexOf("elif policy_duplicate_open_exact_groups > 0"));
    }

    @Test
    @DisplayName("policy data triage 문서는 정책 오류 제보 우선순위와 summary field를 안내한다")
    void docsDescribePolicyErrorReportPriority() throws IOException {
        String runbook = Files.readString(RUNBOOK);
        String triageRunbook = Files.readString(TRIAGE_RUNBOOK);
        String currentState = Files.readString(CURRENT_STATE);

        assertThat(runbook)
                .contains("policy_error_open_reports")
                .contains("policy_error_recent_open_reports_24h")
                .contains("policy_error_open_broken_link_reports")
                .contains("POLICY_ERROR_REPORT_PRIORITY")
                .contains("사용자 정책 오류 제보 `OPEN` queue가 남아 있습니다.");

        assertThat(triageRunbook)
                .contains("policy_error_open_reports")
                .contains("POLICY_ERROR_REPORT_PRIORITY")
                .contains("duplicate/link보다 먼저 반환합니다");

        assertThat(currentState)
                .contains("정책 오류 제보 -> duplicate -> link review -> drift tail")
                .contains("policy_error_open_reports=0");
    }
}
