package com.example.welfare.collect.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CollectDetailExecutionContractTest {

    private static final Path CONTRACT_DOC = Path.of("../docs/collect/collect-detail-execution-contract.md");
    private static final Path VERIFY_SCRIPT = Path.of("../deploy/smoke/verify-collect-detail-execution-contract.sh");

    @Test
    @DisplayName("scheduled collect executionOrder는 list source만 고정 순서로 포함한다")
    void scheduledExecutionOrderContainsOnlyListSources() {
        assertThat(CollectSource.executionOrder())
                .containsExactly(
                        CollectSource.YOUTH,
                        CollectSource.BOKJIRO_CENTRAL,
                        CollectSource.BOKJIRO_LOCAL,
                        CollectSource.GOV24
                )
                .allMatch(CollectSource::isListSource);
    }

    @Test
    @DisplayName("detail execution contract 문서는 phase 순서와 호출량 경계를 명시한다")
    void detailExecutionContractDocumentContainsOrderAndBudgets() throws IOException {
        String doc = Files.readString(CONTRACT_DOC);

        assertThat(doc)
                .contains("COLLECT_DETAIL_PHASE_LIST_SNAPSHOT")
                .contains("COLLECT_DETAIL_PHASE_FORCED_DETAIL")
                .contains("COLLECT_DETAIL_PHASE_ROTATION_DETAIL")
                .contains("COLLECT_DETAIL_DUPLICATE_LANE_ALLOWED")
                .contains("COLLECT_DETAIL_CALL_BUDGETS")
                .contains("`YOUTH`")
                .contains("`BOKJIRO_CENTRAL`")
                .contains("`BOKJIRO_LOCAL`")
                .contains("`GOV24`")
                .contains("모든 list source 실행이 끝난 뒤")
                .contains("forced detail phase가 모두 끝난 뒤")
                .contains("같은 detail lane이 한 run 결과에 두 번 보이는 것은 허용된 상태")
                .contains("collect.list.diff.force-detail.max-candidates-per-run=50")
                .contains("collect.list.rotation.bokjiro-detail-max-calls-per-run=100")
                .contains("collect.list.rotation.gov24-detail-max-calls-per-run=50")
                .contains("collect.list.rotation.gov24-support-conditions-max-calls-per-run=50")
                .contains("collect.list.rotation.bokjiro-refresh-max-calls-per-run=50")
                .contains("collect.youth.detail.max-calls-per-run=50")
                .contains("api_sync_logs.requested_count");
    }

    @Test
    @DisplayName("detail execution contract 검증 스크립트는 같은 contract id와 budget key를 확인한다")
    void detailExecutionVerificationScriptChecksSameContract() throws IOException {
        String script = Files.readString(VERIFY_SCRIPT);

        assertThat(script)
                .contains("COLLECT_DETAIL_PHASE_LIST_SNAPSHOT")
                .contains("COLLECT_DETAIL_PHASE_FORCED_DETAIL")
                .contains("COLLECT_DETAIL_PHASE_ROTATION_DETAIL")
                .contains("COLLECT_DETAIL_DUPLICATE_LANE_ALLOWED")
                .contains("COLLECT_DETAIL_CALL_BUDGETS")
                .contains("collect.list.diff.force-detail.max-candidates-per-run=50")
                .contains("collect.list.rotation.bokjiro-detail-max-calls-per-run=100")
                .contains("collect.youth.detail.max-calls-per-run=50")
                .contains("collect detail execution contract passed");
    }
}
