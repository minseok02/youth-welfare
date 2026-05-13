package com.example.welfare.policy.dto;

import java.util.List;

public record PolicyRetrievalQualityGateResponse(
        boolean passed,
        String datasetKey,
        Thresholds thresholds,
        ActualMetrics actualMetrics,
        List<String> failureReasons
) {
    public record Thresholds(
            double minTop1HitRate,
            double minTop3HitRate,
            double minBranchSuggestionHitRate,
            int maxEmptyResultCount
    ) {
    }

    public record ActualMetrics(
            double top1HitRate,
            double top3HitRate,
            double branchSuggestionHitRate,
            int emptyResultCount
    ) {
    }
}
