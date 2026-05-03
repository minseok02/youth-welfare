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
            List<CollectJobSnapshot> latestJobs,
            List<CollectFailureSnapshot> latestFailuresLast7d
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
            long totalLogs,
            long sentLast24h,
            long sentLast7d,
            long clickedLast7d,
            long fallbackLast7d,
            LocalDateTime latestClickedAt,
            BigDecimal clickThroughRateLast7d,
            BigDecimal fallbackRateLast7d,
            List<RecommendationWeightSnapshot> weightBucketsLast7d
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
            long sentLast7d,
            long failedLast7d
    ) {
    }

    public record SearchSection(
            long searchesLast24h,
            long searchesLast7d,
            long zeroResultSearchesLast7d,
            long uniqueFingerprintsLast7d,
            BigDecimal averageResultCountLast7d,
            List<SearchKeywordSnapshot> topKeywordsLast7d
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
