package com.example.welfare.collect.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CollectExternalApiSmokeContractTest {

    private static final Path SCRIPT = Path.of("../deploy/smoke/run-local-collect-external-api-smoke.sh");
    private static final Path RUNBOOK = Path.of("../docs/collect/collect-external-api-smoke-runbook.md");
    private static final Path DOCS_INDEX = Path.of("../docs/collect/collect-docs-index.md");

    @Test
    @DisplayName("collect external API smoke는 수집 로그/payload/runtime/lock source를 함께 읽는다")
    void scriptReadsCollectExternalApiObservationSources() throws IOException {
        String script = Files.readString(SCRIPT);

        assertThat(script)
                .contains("api_sync_logs")
                .contains("raw_api_payloads")
                .contains("collect_runtime_statuses")
                .contains("collect_execution_locks")
                .contains("welfare_services")
                .contains("welfare_service_details")
                .contains("service_taxonomies")
                .contains("service_taxonomy_terms")
                .contains("service_taxonomy_summary_slots")
                .contains("service_facts")
                .contains("source_available")
                .contains("source_missing")
                .contains("api_sync_log_runs_")
                .contains("raw_payload_total")
                .contains("collect-storage-parity.tsv")
                .contains("collect-sidecar-parity.tsv")
                .contains("collect-detail-support-coverage.tsv")
                .contains("storage_mismatch_sources")
                .contains("sources_with_api_success_without_raw_payload")
                .contains("sources_with_api_success_without_welfare_rows")
                .contains("sidecar_mismatch_sources")
                .contains("sources_with_recent_success_without_taxonomy")
                .contains("sources_with_gov24_missing_required_summary_slots")
                .contains("detail_support_coverage_mismatch_lanes")
                .contains("lanes_with_recent_success_without_detail_raw")
                .contains("lanes_with_recent_success_without_support_facts")
                .contains("open_collect_circuit_keys")
                .contains("active_collect_lock_keys")
                .contains("decision_class");
    }

    @Test
    @DisplayName("collect external API smoke는 empty local history를 실패와 분리한다")
    void scriptSeparatesEmptyLocalHistoryFromFailures() throws IOException {
        String script = Files.readString(SCRIPT);

        assertThat(script)
                .contains("NO_COLLECT_HISTORY")
                .contains("SOURCE_UNAVAILABLE")
                .contains("ACTIVE_LOCK_REVIEW")
                .contains("ATTENTION_REQUIRED")
                .contains("PARTIAL_SUCCESS_REVIEW")
                .contains("STORAGE_PARITY_REVIEW")
                .contains("SIDECAR_PARITY_REVIEW")
                .contains("DETAIL_SUPPORT_COVERAGE_REVIEW")
                .contains("BASELINE_HEALTHY")
                .contains("api_runs_i == 0 and raw_rows_i == 0")
                .contains("api_failed_runs_i > 0")
                .contains("api_partial_runs_i > 0")
                .contains("success_runs > 0 and saved_count > 0 and raw_count == 0")
                .contains("success_runs > 0 and saved_count > 0 and welfare_count == 0")
                .contains("success_runs > 0 and saved_count > 0 and welfare_count > 0 and taxonomy_count == 0")
                .contains("gov24_missing_required_summary_slot_count > 0")
                .contains("has_recent_saved_success and expected_detail_raw and detail_raw_count == 0")
                .contains("has_recent_saved_success and expected_support_fact and support_fact_count == 0");
    }

    @Test
    @DisplayName("collect 문서는 external API smoke entrypoint와 판정 기준을 안내한다")
    void collectDocsReferenceExternalApiSmoke() throws IOException {
        String runbook = Files.readString(RUNBOOK);
        String docsIndex = Files.readString(DOCS_INDEX);

        assertThat(runbook)
                .contains("run-local-collect-external-api-smoke.sh")
                .contains("ENV_FILE=.env.production")
                .contains("SMOKE_DB_MODE=postgres")
                .contains("api_sync_logs")
                .contains("raw_api_payloads")
                .contains("welfare_service_details")
                .contains("service_taxonomies")
                .contains("service_taxonomy_summary_slots")
                .contains("NO_COLLECT_HISTORY")
                .contains("ACTIVE_LOCK_REVIEW")
                .contains("SOURCE_UNAVAILABLE")
                .contains("STORAGE_PARITY_REVIEW")
                .contains("SIDECAR_PARITY_REVIEW")
                .contains("DETAIL_SUPPORT_COVERAGE_REVIEW")
                .contains("storage_mismatch_sources")
                .contains("sidecar_mismatch_sources")
                .contains("detail_support_coverage_mismatch_lanes")
                .contains("gov24-details?maxCallsPerRun=1")
                .contains("requested=1 saved=1 skipped=282 failed=0");
        assertThat(docsIndex)
                .contains("collect-external-api-smoke-runbook.md")
                .contains("run-local-collect-external-api-smoke.sh");
    }
}
