package com.example.welfare.policy.dto;

import java.util.List;

public record PolicyRetrievalEvaluationResponse(
        String datasetKey,
        int scenarioCount,
        int retrievalScenarioCount,
        int branchScenarioCount,
        int top1HitCount,
        int top3HitCount,
        int branchSuggestionHitCount,
        int fallbackCount,
        int semanticContributionCount,
        int emptyResultCount,
        double top1HitRate,
        double top3HitRate,
        double branchSuggestionHitRate,
        double fallbackRate,
        double semanticContributionRate,
        double averageResultCount,
        double averageFtsResultCount,
        double averageSemanticResultCount,
        double averageSemanticOnlyResultCount,
        double averageTop3CategoryConcentration,
        double averageBranchReductionCount,
        List<ScenarioResult> scenarios
) {
    public record ScenarioResult(
            String scenarioKey,
            String question,
            String branchKey,
            String expectedCategory,
            String expectedBranchKey,
            String fallbackStrategy,
            boolean top1Hit,
            boolean top3Hit,
            boolean branchSuggestionHit,
            boolean semanticContribution,
            boolean fallbackUsed,
            int ftsResultCount,
            int semanticResultCount,
            int semanticOnlyResultCount,
            int resultCount,
            int unbranchedResultCount,
            int branchReductionCount,
            double top3CategoryConcentration,
            List<Long> finalServiceIds,
            List<String> branchSuggestionKeys
    ) {
    }
}
