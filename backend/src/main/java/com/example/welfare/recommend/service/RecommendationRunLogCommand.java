package com.example.welfare.recommend.service;

public record RecommendationRunLogCommand(
        String userKey,
        boolean personal,
        String outcome,
        String clusterId,
        int retrievedCount,
        int ruleScoredCount,
        int postFilterCount,
        int rerankedCount,
        int savedCount,
        String aiStatusCountsJson,
        long durationMs
) {
}
