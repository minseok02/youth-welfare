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
        UserPiiSyncSection userPiiSync
) {

    public record CollectSection(
            long runningJobs,
            long successJobsLast24h,
            long partialSuccessJobsLast24h,
            long failedJobsLast24h,
            List<CollectJobSnapshot> latestJobs
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
}
