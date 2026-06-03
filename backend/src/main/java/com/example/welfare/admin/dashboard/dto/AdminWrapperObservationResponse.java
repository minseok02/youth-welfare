package com.example.welfare.admin.dashboard.dto;

import java.time.LocalDateTime;

public record AdminWrapperObservationResponse(
        boolean activeBaselineAvailable,
        LocalDateTime activeBaselineGeneratedAt,
        String activeBaselineSummaryPath,
        String activeBaselineStatus,
        String activeBaselineOpsObservationStatus,
        String activeBaselineAttentionFeedStatus,
        int activeBaselineAttentionFeedItemCount,
        int activeBaselineAttentionFeedWarningItemCount,
        String activeBaselineAttentionFeedItemKeys,
        String activeBaselineAttentionFeedItemTitles,
        int activeBaselineUsersWithAnyStandardCode,
        int activeBaselineUsersMissingAllStandardCodes,
        String activeBaselineRecommendationObservationStatus,
        int activeBaselineRecommendationHousingPositiveRuleDeltaRows,
        int activeBaselineRecommendationWelfarePositiveRuleScenarios,
        double activeBaselineRecommendationWelfareMaxRuleDelta,
        boolean currentPriorityAvailable,
        LocalDateTime currentPriorityGeneratedAt,
        String currentPrioritySummaryPath,
        String currentPriorityStatus,
        boolean currentPriorityActiveBaselineReused,
        String currentPriorityAttentionFeedStatus,
        int currentPriorityAttentionFeedItemCount,
        int currentPriorityAttentionFeedWarningItemCount,
        String currentPriorityAttentionFeedItemKeys,
        String currentPriorityAttentionFeedItemTitles,
        int currentPriorityUsersWithAnyStandardCode,
        int currentPriorityUsersMissingAllStandardCodes,
        String currentPriorityRecommendationObservationStatus,
        int currentPriorityRecommendationHousingPositiveRuleDeltaRows,
        int currentPriorityRecommendationWelfareScenarioCount,
        int currentPriorityRecommendationWelfarePositiveRuleScenarios,
        double currentPriorityRecommendationWelfareMaxRuleDelta,
        boolean currentPriorityPreviousAvailable,
        LocalDateTime currentPriorityPreviousGeneratedAt,
        String currentPriorityPreviousSummaryPath,
        int currentPriorityPreviousUsersMissingAllStandardCodes,
        int currentPriorityUsersMissingAllStandardCodesDelta,
        String currentPriorityUsersMissingAllStandardCodesDeltaLabel,
        String currentPriorityPreviousRecommendationObservationStatus,
        boolean currentPriorityRecommendationObservationStatusChanged,
        String currentPriorityRecommendationObservationStatusTransitionLabel,
        SnapshotAlert promotedAlert
) {
    public record SnapshotAlert(
            String severity,
            String title,
            String message
    ) {
    }
}
