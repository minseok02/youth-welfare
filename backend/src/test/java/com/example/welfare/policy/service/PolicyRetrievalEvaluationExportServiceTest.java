package com.example.welfare.policy.service;

import com.example.welfare.policy.dto.PolicyRetrievalEvaluationCompareResponse;
import com.example.welfare.policy.dto.PolicyRetrievalEvaluationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyRetrievalEvaluationExportServiceTest {

    private final PolicyRetrievalEvaluationExportService service = new PolicyRetrievalEvaluationExportService();

    @Test
    @DisplayName("retrieval evaluation csv export는 summary와 scenario rows를 모두 포함한다")
    void toCsvIncludesSummaryAndScenarioRows() {
        PolicyRetrievalEvaluationResponse response = new PolicyRetrievalEvaluationResponse(
                "retrieval-baseline-v2",
                2,
                1,
                1,
                1,
                1,
                1,
                0,
                1,
                0,
                1.0,
                1.0,
                1.0,
                0.0,
                1.0,
                2.0,
                1.0,
                1.0,
                1.0,
                1.0,
                0.0,
                List.of(
                        new PolicyRetrievalEvaluationResponse.ScenarioResult(
                                "finance-support",
                                "청년 생활비나 금융 지원이 있나요?",
                                null,
                                "금융·생활지원",
                                null,
                                "MERGED_RESULTS",
                                true,
                                true,
                                false,
                                true,
                                false,
                                1,
                                1,
                                1,
                                2,
                                2,
                                0,
                                1.0,
                                List.of(50L, 51L),
                                List.of()
                        ),
                        new PolicyRetrievalEvaluationResponse.ScenarioResult(
                                "branch-housing",
                                "주거 지원",
                                null,
                                null,
                                "housing-stability",
                                null,
                                false,
                                false,
                                true,
                                false,
                                false,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0.0,
                                List.of(),
                                List.of("housing-stability", "housing-cash")
                        )
                )
        );

        String csv = service.toCsv(response);

        assertThat(csv).contains("datasetKey,scenarioCount,retrievalScenarioCount");
        assertThat(csv).contains("\"retrieval-baseline-v2\",2,1,1,1,1,1,0,1,0");
        assertThat(csv).contains("scenarioKey,question,branchKey,expectedCategory,expectedBranchKey");
        assertThat(csv).contains("\"finance-support\",\"청년 생활비나 금융 지원이 있나요?\"");
        assertThat(csv).contains("\"50|51\"");
        assertThat(csv).contains("\"housing-stability|housing-cash\"");
    }

    @Test
    @DisplayName("retrieval compare csv export는 tuning, summary, scenario delta를 함께 포함한다")
    void toCsvIncludesCompareSections() {
        PolicyRetrievalEvaluationResponse baseline = new PolicyRetrievalEvaluationResponse(
                "retrieval-baseline-v2",
                1, 1, 0, 1, 1, 0, 1, 0, 0,
                1.0, 1.0, 0.0, 1.0, 0.0, 2.0, 1.0, 0.0, 0.0, 1.0, 1.0,
                List.of(new PolicyRetrievalEvaluationResponse.ScenarioResult(
                        "job-startup",
                        "청년 창업 자금 지원이 있나요?",
                        "job-startup",
                        "일자리",
                        null,
                        "CATEGORY_FALLBACK",
                        true,
                        true,
                        false,
                        false,
                        true,
                        0,
                        0,
                        0,
                        1,
                        2,
                        1,
                        1.0,
                        List.of(40L),
                        List.of()
                ))
        );
        PolicyRetrievalEvaluationResponse candidate = new PolicyRetrievalEvaluationResponse(
                "retrieval-compare-v2-min1-blend1-sem1-terms2",
                1, 1, 0, 1, 1, 0, 0, 1, 0,
                1.0, 1.0, 0.0, 0.0, 1.0, 1.0, 0.0, 1.0, 1.0, 1.0, 0.0,
                List.of(new PolicyRetrievalEvaluationResponse.ScenarioResult(
                        "job-startup",
                        "청년 창업 자금 지원이 있나요?",
                        "job-startup",
                        "일자리",
                        null,
                        "MERGED_RESULTS",
                        true,
                        true,
                        false,
                        true,
                        false,
                        0,
                        1,
                        1,
                        1,
                        1,
                        0,
                        1.0,
                        List.of(40L),
                        List.of()
                ))
        );
        PolicyRetrievalEvaluationCompareResponse response = new PolicyRetrievalEvaluationCompareResponse(
                new PolicyRetrievalEvaluationCompareResponse.RetrievalTuning(3, 2, 3, 3),
                new PolicyRetrievalEvaluationCompareResponse.RetrievalTuning(1, 1, 1, 2),
                baseline,
                candidate,
                new PolicyRetrievalEvaluationCompareResponse.Delta(
                        0, 0, -1, 1, 0.0, 0.0, -1.0, 1.0, -1.0, 1.0, -1.0
                )
        );

        String csv = service.toCsv(response);

        assertThat(csv).contains("profile,minResultCount,semanticBlendLimit,semanticOnlyLimit,maxPreferredTermsInSearchKeyword");
        assertThat(csv).contains("\"baseline\",3,2,3,3");
        assertThat(csv).contains("\"candidate\",1,1,1,2");
        assertThat(csv).contains("profile,datasetKey,top1HitCount,top3HitCount,fallbackCount");
        assertThat(csv).contains("\"delta\",,0,0,-1,1");
        assertThat(csv).contains("scenarioKey,question,branchKey,expectedCategory,baselineFallbackStrategy,candidateFallbackStrategy");
        assertThat(csv).contains("\"job-startup\",\"청년 창업 자금 지원이 있나요?\",\"job-startup\",\"일자리\",\"CATEGORY_FALLBACK\",\"MERGED_RESULTS\"");
    }
}
