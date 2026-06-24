package com.example.welfare.admin.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AdminRecommendationRunSummaryResponse(
        LocalDateTime generatedAt,
        int windowDays,
        long totalRuns,
        long successRuns,
        long errorRuns,
        long noCandidateRuns,
        long personalRuns,
        long savedCount,
        BigDecimal averageDurationMs,
        BigDecimal averageSavedCount,
        LocalDateTime latestRunAt,
        List<OutcomeBreakdown> outcomeBreakdowns,
        List<RecentRun> recentRuns
) {

    public record OutcomeBreakdown(
            String outcome,
            long runCount,
            long savedCount,
            BigDecimal averageDurationMs,
            LocalDateTime latestRunAt
    ) {
    }

    public record RecentRun(
            long id,
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
            long durationMs,
            LocalDateTime createdAt
    ) {
    }
}
