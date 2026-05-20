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
            RecommendationTrafficMixSnapshot trafficMixInWindow,
            String realUserTrafficGateInWindow,
            RecommendationConcentrationSnapshot latestBatchConcentration,
            String recommendationReviewGate,
            RecommendationRecentWindowSnapshot recentWindowLatestBatch,
            RecommendationReviewGateStalenessSnapshot reviewGateStaleness,
            String recentWindowRecommendationReviewReading,
            boolean historicalExampleDominanceDetected,
            String reviewGatePolicyCandidateStatus,
            String reviewGatePolicyCandidateReason,
            List<RecommendationWeightSnapshot> weightBucketsInWindow
    ) {
    }

    public record RecommendationTrafficMixSnapshot(
            long exampleLogsInWindow,
            long boundedLocalLogsInWindow,
            long localRealNonExampleSeedLogsInWindow,
            long realUserLogsInWindow,
            long realNonExampleLogsInWindow,
            long exampleUsersInWindow,
            long boundedLocalUsersInWindow,
            long localRealNonExampleSeedUsersInWindow,
            long realUserUsersInWindow,
            long realNonExampleUsersInWindow,
            long exampleClickedUsersInWindow,
            long boundedLocalClickedUsersInWindow,
            long localRealNonExampleSeedClickedUsersInWindow,
            long realUserClickedUsersInWindow,
            long realNonExampleClickedUsersInWindow
    ) {
    }

    public record RecommendationConcentrationSnapshot(
            long latestBatchRows,
            long latestBatchUsers,
            long latestBatchDistinctServices,
            Long top1LeaderServiceId,
            String top1LeaderTitle,
            String top1LeaderSource,
            String top1LeaderCategory,
            long top1LeaderUsers,
            BigDecimal top1LeaderSharePct,
            ServiceCohortMixSnapshot top1LeaderUserMix,
            String top1LeaderSignalSummary,
            String concentrationReadiness,
            String realUserCohortGate,
            String signalQuality
    ) {
    }

    public record RecommendationRecentWindowSnapshot(
            int recentWindowHours,
            long targetServiceId,
            long latestBatchUsers,
            long exampleUsers,
            long realUserUsers,
            long localRealNonExampleSeedUsers,
            Long top1LeaderServiceId,
            String top1LeaderTitle,
            long top1LeaderUsers,
            long top1LeaderRealUserUsers,
            BigDecimal top1LeaderSharePct,
            long targetTop1Users,
            long targetTop1RealUserUsers
    ) {
    }

    public record RecommendationReviewGateStalenessSnapshot(
            long targetServiceId,
            String primaryReferenceMode,
            int recentWindowHours,
            long exampleLatestUsers,
            long exampleLatestUsersLast24h,
            long exampleTargetTop1Users,
            long exampleTargetTop1Last24h,
            LocalDateTime exampleTargetOldestTop1At,
            LocalDateTime exampleTargetNewestTop1At,
            long realUserLatestUsers,
            long realUserLatestUsersLast24h,
            long realUserTargetTop1Users,
            long realUserTargetTop1Last24h
    ) {
    }

    public record ServiceCohortMixSnapshot(
            long exampleUsers,
            long boundedLocalUsers,
            long localRealNonExampleSeedUsers,
            long realUserUsers,
            long realNonExampleUsers
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
