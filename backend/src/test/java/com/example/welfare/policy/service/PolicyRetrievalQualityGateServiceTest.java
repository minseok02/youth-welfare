package com.example.welfare.policy.service;

import com.example.welfare.policy.config.PolicyRetrievalQualityGateProperties;
import com.example.welfare.policy.dto.PolicyRetrievalEvaluationResponse;
import com.example.welfare.policy.dto.PolicyRetrievalQualityGateResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class PolicyRetrievalQualityGateServiceTest {

    private final PolicyRetrievalEvaluationService policyRetrievalEvaluationService =
            mock(PolicyRetrievalEvaluationService.class);

    @Test
    @DisplayName("quality gate는 기준을 모두 충족하면 pass를 반환한다")
    void evaluateGatePassesWhenThresholdsMet() {
        given(policyRetrievalEvaluationService.evaluateBaseline())
                .willReturn(evaluation(1.0, 1.0, 1.0, 0));

        PolicyRetrievalQualityGateService service = new PolicyRetrievalQualityGateService(
                policyRetrievalEvaluationService,
                new PolicyRetrievalQualityGateProperties(0.9, 0.9, 1.0, 0)
        );

        PolicyRetrievalQualityGateResponse response = service.evaluateGate();

        assertThat(response.passed()).isTrue();
        assertThat(response.failureReasons()).isEmpty();
    }

    @Test
    @DisplayName("quality gate는 기준 미달 항목을 모두 failure reason에 담는다")
    void evaluateGateFailsWhenThresholdsMissed() {
        given(policyRetrievalEvaluationService.evaluateBaseline())
                .willReturn(evaluation(0.7, 0.8, 0.5, 2));

        PolicyRetrievalQualityGateService service = new PolicyRetrievalQualityGateService(
                policyRetrievalEvaluationService,
                new PolicyRetrievalQualityGateProperties(0.9, 0.9, 1.0, 0)
        );

        PolicyRetrievalQualityGateResponse response = service.evaluateGate();

        assertThat(response.passed()).isFalse();
        assertThat(response.failureReasons()).containsExactly(
                "top1HitRate below threshold",
                "top3HitRate below threshold",
                "branchSuggestionHitRate below threshold",
                "emptyResultCount above threshold"
        );
    }

    private PolicyRetrievalEvaluationResponse evaluation(double top1HitRate,
                                                         double top3HitRate,
                                                         double branchSuggestionHitRate,
                                                         int emptyResultCount) {
        return new PolicyRetrievalEvaluationResponse(
                "retrieval-baseline-v2",
                11,
                9,
                2,
                0,
                0,
                0,
                0,
                0,
                emptyResultCount,
                top1HitRate,
                top3HitRate,
                branchSuggestionHitRate,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                List.of()
        );
    }
}
