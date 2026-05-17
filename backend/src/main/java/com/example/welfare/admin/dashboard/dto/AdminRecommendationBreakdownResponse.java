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
        List<SourceBreakdown> sourceBreakdowns,
        List<CategoryBreakdown> categoryBreakdowns,
        List<WeightBreakdown> weightBreakdowns,
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
