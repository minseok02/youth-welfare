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

    public record RecommendationSampleRow(
            Long logId,
            Long serviceId,
            String title,
            String sourceType,
            String category,
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
            long exposureCount,
            long clickedCount,
            long fallbackCount,
            LocalDateTime firstSentAt,
            LocalDateTime latestSentAt,
            LocalDateTime latestClickedAt
    ) {
    }

    public record NotificationSummaryRow(
            long sentLast24h,
            long failedLast24h,
            long sentInWindow,
            long failedInWindow
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
