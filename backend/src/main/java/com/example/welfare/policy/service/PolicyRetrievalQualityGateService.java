package com.example.welfare.policy.service;

import com.example.welfare.policy.config.PolicyRetrievalQualityGateProperties;
import com.example.welfare.policy.dto.PolicyRetrievalEvaluationResponse;
import com.example.welfare.policy.dto.PolicyRetrievalQualityGateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PolicyRetrievalQualityGateService {

    private final PolicyRetrievalEvaluationService policyRetrievalEvaluationService;
    private final PolicyRetrievalQualityGateProperties properties;

    public PolicyRetrievalQualityGateResponse evaluateGate() {
        PolicyRetrievalEvaluationResponse evaluation = policyRetrievalEvaluationService.evaluateBaseline();
        List<String> failureReasons = new ArrayList<>();

        if (evaluation.top1HitRate() < properties.minTop1HitRate()) {
            failureReasons.add("top1HitRate below threshold");
        }
        if (evaluation.top3HitRate() < properties.minTop3HitRate()) {
            failureReasons.add("top3HitRate below threshold");
        }
        if (evaluation.branchSuggestionHitRate() < properties.minBranchSuggestionHitRate()) {
            failureReasons.add("branchSuggestionHitRate below threshold");
        }
        if (evaluation.emptyResultCount() > properties.maxEmptyResultCount()) {
            failureReasons.add("emptyResultCount above threshold");
        }

        return new PolicyRetrievalQualityGateResponse(
                failureReasons.isEmpty(),
                evaluation.datasetKey(),
                new PolicyRetrievalQualityGateResponse.Thresholds(
                        properties.minTop1HitRate(),
                        properties.minTop3HitRate(),
                        properties.minBranchSuggestionHitRate(),
                        properties.maxEmptyResultCount()
                ),
                new PolicyRetrievalQualityGateResponse.ActualMetrics(
                        evaluation.top1HitRate(),
                        evaluation.top3HitRate(),
                        evaluation.branchSuggestionHitRate(),
                        evaluation.emptyResultCount()
                ),
                List.copyOf(failureReasons)
        );
    }
}
