package com.example.welfare.admin.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AdminDashboardResponse(
        LocalDateTime generatedAt,
        CollectSection collect,
        RecommendationSection recommendation,
        NotificationSection notification,
        SearchSection search,
        UserPiiSyncSection userPiiSync,
        TrendSection trend
) {

    public record CollectSection(
            long runningJobs,
            long successJobsLast24h,
            long partialSuccessJobsLast24h,
            long failedJobsLast24h,
            int windowDays,
            List<CollectJobSnapshot> latestJobs,
            List<CollectFailureSnapshot> latestFailuresInWindow
    ) {
    }

    public record CollectJobSnapshot(
            String jobName,
            String status,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            int requestedCount,
            int savedCount,
            int failedCount
    ) {
    }

    public record CollectFailureSnapshot(
            String jobName,
            String status,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String errorCode,
            String errorMessage,
            int requestedCount,
            int savedCount,
            int failedCount
    ) {
    }

    public record RecommendationSection(
            String activeWeightKey,
            BigDecimal activeRuleWeight,
            BigDecimal activeAiWeight,
            String nextWeightKey,
            Integer nextWeightMinLogCount,
            Long remainingLogsUntilNextWeight,
            boolean topWeightStage,
            long totalLogs,
            long sentLast24h,
            int windowDays,
            long sentInWindow,
            long clickedInWindow,
            long fallbackInWindow,
            LocalDateTime latestClickedAt,
            BigDecimal clickThroughRateInWindow,
            BigDecimal fallbackRateInWindow,
            List<RecommendationWeightSnapshot> weightBucketsInWindow
    ) {
    }

    public record RecommendationWeightSnapshot(
            String weightKey,
            BigDecimal ruleWeight,
            BigDecimal aiWeight,
            long logCount
    ) {
    }

    public record NotificationSection(
            long sentLast24h,
            long failedLast24h,
            int windowDays,
            long sentInWindow,
            long failedInWindow
    ) {
    }

    public record SearchSection(
            long searchesLast24h,
            int windowDays,
            long searchesInWindow,
            long zeroResultSearchesInWindow,
            long uniqueFingerprintsInWindow,
            BigDecimal averageResultCountInWindow,
            List<SearchKeywordSnapshot> topKeywordsInWindow,
            List<SearchKeywordSnapshot> zeroResultKeywordsInWindow
    ) {
    }

    public record SearchKeywordSnapshot(
            String keyword,
            long searchCount
    ) {
    }

    public record UserPiiSyncSection(
            long pendingCount,
            long failedCount,
            long syncedCount,
            LocalDateTime latestSyncedAt
    ) {
    }

    public record TrendSection(
            List<CollectTrendPoint> collect,
            List<RecommendationTrendPoint> recommendation,
            List<SearchTrendPoint> search
    ) {
    }

    public record CollectTrendPoint(
            int windowDays,
            long successJobs,
            long partialSuccessJobs,
            long failedJobs
    ) {
    }

    public record RecommendationTrendPoint(
            int windowDays,
            long sentCount,
            long clickedCount,
            long fallbackCount,
            BigDecimal clickThroughRate,
            BigDecimal fallbackRate
    ) {
    }

    public record SearchTrendPoint(
            int windowDays,
            long searches,
            long zeroResultSearches
    ) {
    }
}
