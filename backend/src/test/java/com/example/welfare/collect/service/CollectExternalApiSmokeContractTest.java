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
                .contains("source_available")
                .contains("source_missing")
                .contains("api_sync_log_runs_")
                .contains("raw_payload_total")
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
                .contains("BASELINE_HEALTHY")
                .contains("api_runs_i == 0 and raw_rows_i == 0")
                .contains("api_failed_runs_i > 0")
                .contains("api_partial_runs_i > 0");
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
                .contains("NO_COLLECT_HISTORY")
                .contains("ACTIVE_LOCK_REVIEW")
                .contains("SOURCE_UNAVAILABLE")
                .contains("gov24-details?maxCallsPerRun=1")
                .contains("requested=1 saved=1 skipped=282 failed=0");
        assertThat(docsIndex)
                .contains("collect-external-api-smoke-runbook.md")
                .contains("run-local-collect-external-api-smoke.sh");
    }
}
