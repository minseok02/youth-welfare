package com.example.welfare.admin.dashboard.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class AdminDashboardReadRows {

    private AdminDashboardReadRows() {
    }

    public record CollectSummaryRow(
            long runningJobs,
            long successJobsLast24h,
            long partialSuccessJobsLast24h,
            long failedJobsLast24h
    ) {
    }

    public record CollectFailureSummaryRow(
            long totalFailedJobs,
            long totalPartialSuccessJobs
    ) {
    }

    public record CollectTrendRow(
            long successJobs,
            long partialSuccessJobs,
            long failedJobs
    ) {
    }

    public record CollectJobSnapshotRow(
            String jobName,
            String status,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            int requestedCount,
            int savedCount,
            int skippedCount,
            int filteredCount,
            int failedCount
    ) {
    }

    public record CollectFailureSnapshotRow(
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

    public record CollectFailureJobBreakdownRow(
            String jobName,
            long failedCount,
            long partialSuccessCount,
            LocalDateTime latestStartedAt
    ) {
    }

    public record CollectFailureErrorCodeBreakdownRow(
            String errorCode,
            long failedCount
    ) {
    }

    public record CollectFailureSampleRow(
            String jobName,
            String status,
            String errorCode,
            String errorMessage,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            int requestedCount,
            int savedCount,
            int failedCount
    ) {
    }

    public record CollectJobRunRow(
            String jobName,
            String status,
            LocalDateTime startedAt
    ) {
    }

    public record CollectJobStreakRow(
            String jobName,
            String streakStatus,
            long streakCount,
            LocalDateTime latestStartedAt
    ) {
    }

    public record RecommendationSummaryRow(
            long totalLogs,
            long sentLast24h,
            long sentInWindow,
            long clickedInWindow,
            long fallbackInWindow,
            LocalDateTime latestClickedAt
    ) {
    }

    public record RecommendationWeightSnapshotRow(
            String weightKey,
            BigDecimal ruleWeight,
            BigDecimal aiWeight,
            long logCount
    ) {
    }

    public record RecommendationTrendRow(
            long sentCount,
            long clickedCount,
            long fallbackCount
    ) {
    }

    public record RecommendationTrafficMixRow(
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

    public record RecommendationConcentrationRow(
            long latestBatchRows,
            long latestBatchUsers,
            long latestBatchDistinctServices,
            Long top1LeaderServiceId,
            String top1LeaderTitle,
            String top1LeaderSource,
            String top1LeaderCategory,
            long top1LeaderUsers,
            BigDecimal top1LeaderSharePct,
            long top1LeaderExampleUsers,
            long top1LeaderBoundedLocalUsers,
            long top1LeaderLocalRealNonExampleSeedUsers,
            long top1LeaderRealUserUsers,
            long top1LeaderRealNonExampleUsers,
            String concentrationReadiness,
            String realUserCohortGate,
            String signalQuality
    ) {
    }

    public record RecommendationRecentWindowRow(
            int recentWindowHours,
            long targetServiceId,
            long recentLatestBatchUsers,
            long recentExampleUsers,
            long recentRealUserUsers,
            long recentLocalRealNonExampleSeedUsers,
            Long recentTop1LeaderServiceId,
            String recentTop1LeaderTitle,
            long recentTop1LeaderUsers,
            long recentTop1LeaderRealUserUsers,
            BigDecimal recentTop1LeaderSharePct,
            long recentTargetTop1Users,
            long recentTargetTop1RealUserUsers
    ) {
    }

    public record RecommendationReviewGateStalenessRow(
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

    public record RecommendationReviewGatePromotionApprovalRecordRow(
            String approvalKey,
            String approvalStatus,
            String approvalScope,
            String approvalNote,
            String approvedByUserKey,
            LocalDateTime approvedAt
    ) {
    }

    public record RecommendationRepeatedServiceRow(
            Long serviceId,
            String title,
            String sourceType,
            String category,
            long rowCount,
            long distinctUsers,
            long exampleUsers,
            long boundedLocalUsers,
            long localRealNonExampleSeedUsers,
            long realUserUsers,
            long realNonExampleUsers
    ) {
    }

    public record RecommendationTop1ServiceRow(
            Long serviceId,
            String title,
            String sourceType,
            String category,
            long usersAsTop1,
            long exampleUsers,
            long boundedLocalUsers,
            long localRealNonExampleSeedUsers,
            long realUserUsers,
            long realNonExampleUsers
    ) {
    }

    public record UserProfileStandardCodeCoverageRow(
            long totalUsers,
            long usersWithProfileRow,
            long usersWithoutProfileRow,
            long usersWithAnyStandardCode,
            long usersWithAllStandardCodes,
            long usersMissingAllStandardCodes,
            long usersHouseTenureCodeFilled,
            long usersHousingTypeCodeFilled,
            long usersBasicLivingRecipientTypeCodeFilled,
            long usersDisabilityGradeCodeFilled,
            long profilesWithAnyStandardCode,
            long profilesWithAllStandardCodes,
            long profilesMissingAllStandardCodes,
            long profileOnlyGapRows,
            long userOnlyGapRows,
            long safeReconcileCandidateRows,
            long conflictingValueGapRows
    ) {
    }

    public record RecommendationSourceBreakdownRow(
            String sourceType,
            long sentCount,
            long clickedCount,
            long fallbackCount
    ) {
    }

    public record RecommendationCategoryBreakdownRow(
            String category,
            long sentCount,
            long clickedCount,
            long fallbackCount
    ) {
    }

    public record RecommendationWeightBreakdownRow(
            String weightKey,
            BigDecimal ruleWeight,
            BigDecimal aiWeight,
            long sentCount,
            long clickedCount,
            long fallbackCount
    ) {
    }

    public record RecommendationRunSummaryRow(
            long totalRuns,
            long successRuns,
            long errorRuns,
            long noCandidateRuns,
            long personalRuns,
            long savedCount,
            BigDecimal averageDurationMs,
            BigDecimal averageSavedCount,
            LocalDateTime latestRunAt
    ) {
    }

    public record RecommendationRunOutcomeBreakdownRow(
            String outcome,
            long runCount,
            long savedCount,
            BigDecimal averageDurationMs,
            LocalDateTime latestRunAt
    ) {
    }

    public record RecommendationRunSampleRow(
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

    public record RecommendationSampleRow(
            Long logId,
            Long serviceId,
            String title,
            String sourceType,
            String category,
            String userCohort,
            BigDecimal finalScore,
            boolean fallback,
            boolean clicked,
            LocalDateTime sentAt,
            LocalDateTime clickedAt
    ) {
    }

    public record RecommendationRepeatExposureGroupRow(
            String userKey,
            Long serviceId,
            String title,
            String sourceType,
            String category,
            String userCohort,
            long exposureCount,
            long clickedCount,
            long fallbackCount,
            LocalDateTime firstSentAt,
            LocalDateTime latestSentAt,
            LocalDateTime latestClickedAt
    ) {
    }

    public record RecommendationFacetRow(
            String facetKey,
            String bucketLabel,
            long rowCount,
            long distinctServices
    ) {
    }

    public record NotificationSummaryRow(
            long sentLast24h,
            long failedLast24h,
            long sentInWindow,
            long failedInWindow,
            long unreadAlerts,
            long staleUnread7d,
            long staleUnread14d,
            long retryableFailedNotifications,
            long terminalFailedNotifications
    ) {
    }

    public record NotificationAttemptSummaryRow(
            long totalAttempts,
            long successAttempts,
            long failedAttempts,
            long disabledAttempts,
            BigDecimal averageDurationMs,
            LocalDateTime latestAttemptAt
    ) {
    }

    public record NotificationAttemptBreakdownRow(
            String channel,
            String kind,
            String outcome,
            long attemptCount,
            BigDecimal averageDurationMs,
            LocalDateTime latestAttemptAt
    ) {
    }

    public record NotificationAttemptSampleRow(
            long id,
            String channel,
            String kind,
            String outcome,
            int itemCount,
            String endpointHost,
            String errorType,
            long durationMs,
            LocalDateTime createdAt
    ) {
    }

    public record SearchSummaryRow(
            long searchesLast24h,
            long searchesInWindow,
            long zeroResultSearchesInWindow,
            long uniqueFingerprintsInWindow,
            BigDecimal averageResultCountInWindow
    ) {
    }

    public record SearchTrendRow(
            long searches,
            long zeroResultSearches
    ) {
    }

    public record SearchKeywordSnapshotRow(
            String keyword,
            long searchCount
    ) {
    }

    public record SearchRegionSnapshotRow(
            String sido,
            String sgg,
            long searchCount
    ) {
    }

    public record SearchFilterPatternSnapshotRow(
            String statusFilter,
            String category,
            String sourceType,
            Boolean onlineApply,
            boolean includeClosed,
            String sortKey,
            long searchCount
    ) {
    }

    public record SearchFailureSampleRow(
            String keyword,
            String sido,
            String sgg,
            String statusFilter,
            String category,
            String sourceType,
            Boolean onlineApply,
            boolean includeClosed,
            String sortKey,
            LocalDateTime searchedAt
    ) {
    }

    public record SearchRetryGroupRow(
            String actorType,
            String actorKey,
            String keyword,
            String sido,
            String sgg,
            String statusFilter,
            String category,
            String sourceType,
            Boolean onlineApply,
            boolean includeClosed,
            String sortKey,
            long retryCount,
            LocalDateTime firstSearchedAt,
            LocalDateTime latestSearchedAt
    ) {
    }

    public record RecoveredSearchGroupRow(
            String actorType,
            String actorKey,
            String keyword,
            String sido,
            String sgg,
            String statusFilter,
            String category,
            String sourceType,
            Boolean onlineApply,
            boolean includeClosed,
            String sortKey,
            long zeroResultCount,
            long recoveredResultCount,
            LocalDateTime latestRecoveredAt
    ) {
    }
}
