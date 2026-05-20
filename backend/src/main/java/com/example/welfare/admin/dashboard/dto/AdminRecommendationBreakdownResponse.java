package com.example.welfare.admin.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AdminRecommendationBreakdownResponse(
        LocalDateTime generatedAt,
        int windowDays,
        long sentLogsInWindow,
        long clickedLogsInWindow,
        long fallbackLogsInWindow,
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
        String reviewGatePolicyPromotionStatus,
        String reviewGatePolicyPromotionReason,
        String reviewGatePolicyPromotionActionStatus,
        String reviewGatePolicyPromotionActionReason,
        String reviewGatePolicyPromotionReadinessStatus,
        String reviewGatePolicyPromotionReadinessReason,
        String reviewGatePolicyPromotionExecutionStatus,
        String reviewGatePolicyPromotionExecutionReason,
        String reviewGatePolicyPromotionApprovalCriteriaStatus,
        String reviewGatePolicyPromotionApprovalCriteriaReason,
        String reviewGatePolicyPromotionApprovalStatus,
        String reviewGatePolicyPromotionApprovalReason,
        String reviewGatePolicyPromotionApprovalDecisionStatus,
        String reviewGatePolicyPromotionApprovalDecisionReason,
        String reviewGatePolicyPromotionApprovalRecordStatus,
        String reviewGatePolicyPromotionApprovalRecordReason,
        List<RepeatedServiceSnapshot> topRepeatedServices,
        List<Top1ServiceSnapshot> top1Services,
        List<SourceBreakdown> sourceBreakdowns,
        List<CategoryBreakdown> categoryBreakdowns,
        List<WeightBreakdown> weightBreakdowns,
        List<FacetGroup> youthOfficialFacetGroups,
        List<FacetGroup> gov24FacetGroups,
        List<RecommendationSample> recentFallbackSamples,
        List<RecommendationSample> recentClickedSamples,
        List<RepeatExposureGroup> repeatExposureGroups
) {

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

    public record RepeatedServiceSnapshot(
            Long serviceId,
            String title,
            String sourceType,
            String category,
            long rowCount,
            long distinctUsers,
            ServiceCohortMixSnapshot userMix
    ) {
    }

    public record Top1ServiceSnapshot(
            Long serviceId,
            String title,
            String sourceType,
            String category,
            long usersAsTop1,
            ServiceCohortMixSnapshot userMix
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

    public record SourceBreakdown(
            String sourceType,
            long sentCount,
            long clickedCount,
            long fallbackCount,
            BigDecimal clickThroughRate,
            BigDecimal fallbackRate
    ) {
    }

    public record CategoryBreakdown(
            String category,
            long sentCount,
            long clickedCount,
            long fallbackCount,
            BigDecimal clickThroughRate,
            BigDecimal fallbackRate
    ) {
    }

    public record WeightBreakdown(
            String weightKey,
            BigDecimal ruleWeight,
            BigDecimal aiWeight,
            long sentCount,
            long clickedCount,
            long fallbackCount,
            BigDecimal clickThroughRate,
            BigDecimal fallbackRate
    ) {
    }

    public record FacetGroup(
            String facetKey,
            String label,
            List<FacetBucket> buckets
    ) {
    }

    public record FacetBucket(
            String label,
            long rowCount,
            long distinctServices
    ) {
    }

    public record RecommendationSample(
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

    public record RepeatExposureGroup(
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
}
