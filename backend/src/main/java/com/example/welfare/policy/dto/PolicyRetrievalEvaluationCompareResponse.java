package com.example.welfare.policy.dto;

public record PolicyRetrievalEvaluationCompareResponse(
        RetrievalTuning baselineTuning,
        RetrievalTuning candidateTuning,
        PolicyRetrievalEvaluationResponse baseline,
        PolicyRetrievalEvaluationResponse candidate,
        Delta delta
) {
    public record RetrievalTuning(
            int minResultCount,
            int semanticBlendLimit,
            int semanticOnlyLimit,
            int maxPreferredTermsInSearchKeyword
    ) {
    }

    public record Delta(
            int top1HitCountDelta,
            int top3HitCountDelta,
            int fallbackCountDelta,
            int semanticContributionCountDelta,
            double top1HitRateDelta,
            double top3HitRateDelta,
            double fallbackRateDelta,
            double semanticContributionRateDelta,
            double averageResultCountDelta,
            double averageSemanticOnlyResultCountDelta,
            double averageBranchReductionCountDelta
    ) {
    }
}
