package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.repository.AdminDashboardReadRows;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

final class AdminDashboardQueryPolicy {

    static final int FAILED_SAMPLE_LIMIT = 5;
    static final int COLLECT_FAILURE_PATTERN_LIMIT = 5;
    static final int SEARCH_FAILURE_PATTERN_LIMIT = 5;
    static final int RECOMMENDATION_BREAKDOWN_LIMIT = 5;
    static final int DEFAULT_SUMMARY_WINDOW_DAYS = 7;
    static final List<Integer> DEFAULT_TREND_WINDOWS_DAYS = List.of(1, 7, 30);
    static final int MAX_WINDOW_DAYS = 365;
    static final int RECENT_REVIEW_WINDOW_HOURS = 24;
    static final long HISTORICAL_TARGET_TOP1_SERVICE_ID = 2622L;
    static final int MAX_COLLECT_FAILURE_PATTERN_LIMIT = 20;
    static final int MAX_SEARCH_FAILURE_PATTERN_LIMIT = 20;
    static final int MAX_RECOMMENDATION_BREAKDOWN_LIMIT = 20;

    private AdminDashboardQueryPolicy() {
    }

    static int resolveSummaryWindowDays(Integer requestedSummaryWindowDays) {
        if (requestedSummaryWindowDays == null) {
            return DEFAULT_SUMMARY_WINDOW_DAYS;
        }

        if (requestedSummaryWindowDays <= 0 || requestedSummaryWindowDays > MAX_WINDOW_DAYS) {
            return DEFAULT_SUMMARY_WINDOW_DAYS;
        }

        return requestedSummaryWindowDays;
    }

    static int resolveSearchFailurePatternLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return SEARCH_FAILURE_PATTERN_LIMIT;
        }

        if (requestedLimit <= 0 || requestedLimit > MAX_SEARCH_FAILURE_PATTERN_LIMIT) {
            return SEARCH_FAILURE_PATTERN_LIMIT;
        }

        return requestedLimit;
    }

    static int resolveRecommendationBreakdownLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return RECOMMENDATION_BREAKDOWN_LIMIT;
        }

        if (requestedLimit <= 0 || requestedLimit > MAX_RECOMMENDATION_BREAKDOWN_LIMIT) {
            return RECOMMENDATION_BREAKDOWN_LIMIT;
        }

        return requestedLimit;
    }

    static int resolveCollectFailurePatternLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return COLLECT_FAILURE_PATTERN_LIMIT;
        }

        if (requestedLimit <= 0 || requestedLimit > MAX_COLLECT_FAILURE_PATTERN_LIMIT) {
            return COLLECT_FAILURE_PATTERN_LIMIT;
        }

        return requestedLimit;
    }

    static List<Integer> resolveTrendWindows(List<Integer> requestedTrendWindows) {
        if (requestedTrendWindows == null || requestedTrendWindows.isEmpty()) {
            return DEFAULT_TREND_WINDOWS_DAYS;
        }

        List<Integer> normalized = requestedTrendWindows.stream()
                .filter(Objects::nonNull)
                .filter(windowDays -> windowDays > 0 && windowDays <= MAX_WINDOW_DAYS)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf
                ));

        return normalized.isEmpty() ? DEFAULT_TREND_WINDOWS_DAYS : normalized;
    }

    static BigDecimal ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    static String resolveRealUserTrafficGate(
            AdminDashboardReadRows.RecommendationSummaryRow summaryRow,
            AdminDashboardReadRows.RecommendationTrafficMixRow trafficMixRow
    ) {
        if (summaryRow.sentInWindow() <= 0) {
            return "DEFERRED_EMPTY_COHORT";
        }
        if (trafficMixRow.realUserLogsInWindow() <= 0) {
            return "DEFERRED_NO_REAL_USER_TRAFFIC";
        }
        if (trafficMixRow.realUserUsersInWindow() < 3) {
            return "DEFERRED_REAL_USER_SAMPLE_THIN";
        }
        if (trafficMixRow.realUserClickedUsersInWindow() < 3) {
            return "DEFERRED_REAL_USER_CLICK_SAMPLE_THIN";
        }
        return "READY_REAL_USER_TRAFFIC";
    }

    static String resolveTop1LeaderSignalSummary(AdminDashboardReadRows.RecommendationConcentrationRow concentrationRow) {
        if (concentrationRow.top1LeaderUsers() <= 0) {
            return "EMPTY_TOP1_LEADER";
        }
        if (concentrationRow.top1LeaderRealUserUsers() <= 0 && concentrationRow.top1LeaderLocalRealNonExampleSeedUsers() <= 0
                && concentrationRow.top1LeaderBoundedLocalUsers() <= 0) {
            return "EXAMPLE_SMOKE_ONLY_LEADER";
        }
        if (concentrationRow.top1LeaderRealUserUsers() <= 0 && concentrationRow.top1LeaderLocalRealNonExampleSeedUsers() <= 0
                && concentrationRow.top1LeaderBoundedLocalUsers() > 0) {
            return "BOUNDED_LOCAL_WITH_EXAMPLE_LEADER";
        }
        if (concentrationRow.top1LeaderRealUserUsers() <= 0 && concentrationRow.top1LeaderLocalRealNonExampleSeedUsers() > 0) {
            return "LOCAL_SEED_WITHOUT_REAL_USER_LEADER";
        }
        if (concentrationRow.top1LeaderRealUserUsers() < 3) {
            return "REAL_USER_SIGNAL_THIN_LEADER";
        }
        if (concentrationRow.top1LeaderRealUserUsers() < concentrationRow.top1LeaderUsers()) {
            return "MIXED_REAL_USER_LEADER";
        }
        return "REAL_USER_ONLY_LEADER";
    }

    static String resolveRecommendationReviewGate(
            String realUserTrafficGate,
            AdminDashboardReadRows.RecommendationConcentrationRow concentrationRow
    ) {
        if (!"READY_REAL_USER_TRAFFIC".equals(realUserTrafficGate)) {
            return realUserTrafficGate;
        }
        if (!"READY_REAL_USER_COHORT".equals(concentrationRow.realUserCohortGate())) {
            return concentrationRow.realUserCohortGate();
        }

        String top1LeaderSignalSummary = resolveTop1LeaderSignalSummary(concentrationRow);
        if ("EXAMPLE_SMOKE_ONLY_LEADER".equals(top1LeaderSignalSummary)
                || "BOUNDED_LOCAL_WITH_EXAMPLE_LEADER".equals(top1LeaderSignalSummary)
                || "LOCAL_SEED_WITHOUT_REAL_USER_LEADER".equals(top1LeaderSignalSummary)) {
            return "DEFERRED_NON_REAL_LEADER_SIGNAL";
        }
        if ("REAL_USER_SIGNAL_THIN_LEADER".equals(top1LeaderSignalSummary)) {
            return "DEFERRED_REAL_USER_LEADER_SIGNAL_THIN";
        }
        if ("CONCENTRATED_TOP1".equals(concentrationRow.concentrationReadiness())) {
            return "READY_CONCENTRATED_TOP1_REVIEW";
        }
        if ("NO_PRIORITY_DOMINANT".equals(concentrationRow.concentrationReadiness())) {
            return "READY_NO_PRIORITY_DOMINANT_REVIEW";
        }
        if ("BALANCED_ENOUGH_FOR_LOGIC_REVIEW".equals(concentrationRow.concentrationReadiness())) {
            return "READY_BALANCED_LOGIC_REVIEW";
        }
        return concentrationRow.concentrationReadiness();
    }

    static String resolveRecentWindowRecommendationReviewReading(
            AdminDashboardReadRows.RecommendationRecentWindowRow recentWindowRow
    ) {
        if (recentWindowRow.recentLatestBatchUsers() <= 0) {
            return "DEFERRED_EMPTY_RECENT_WINDOW";
        }
        if (recentWindowRow.recentRealUserUsers() <= 0) {
            return "DEFERRED_NO_REAL_USER_RECENT_WINDOW";
        }
        if (recentWindowRow.recentTop1LeaderServiceId() != null
                && recentWindowRow.recentTop1LeaderServiceId() == HISTORICAL_TARGET_TOP1_SERVICE_ID) {
            return "RECENT_WINDOW_STILL_TARGET_DOMINANT";
        }
        if (recentWindowRow.recentTargetTop1Users() == 0
                && recentWindowRow.recentTargetTop1RealUserUsers() == 0
                && recentWindowRow.recentTop1LeaderRealUserUsers() > 0) {
            return "RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE";
        }
        return "RECENT_WINDOW_INCONCLUSIVE";
    }

    static boolean resolveHistoricalExampleDominanceDetected(
            String recommendationReviewGate,
            String recentWindowRecommendationReviewReading
    ) {
        return "DEFERRED_NON_REAL_LEADER_SIGNAL".equals(recommendationReviewGate)
                && "RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE".equals(recentWindowRecommendationReviewReading);
    }

    static String resolveReviewGatePolicyCandidateStatus(
            String recommendationReviewGate,
            String recentWindowRecommendationReviewReading,
            boolean historicalExampleDominanceDetected,
            AdminDashboardReadRows.RecommendationReviewGateStalenessRow stalenessRow
    ) {
        if (!"DEFERRED_NON_REAL_LEADER_SIGNAL".equals(recommendationReviewGate)) {
            return "NOT_A_CANDIDATE_PRIMARY_GATE_NOT_NON_REAL_BLOCKED";
        }
        if (!historicalExampleDominanceDetected) {
            return "NOT_A_CANDIDATE_NO_HISTORICAL_EXAMPLE_DOMINANCE";
        }
        if (!"ALL_TIME_LATEST_PER_USER".equals(stalenessRow.primaryReferenceMode())) {
            return "NOT_A_CANDIDATE_PRIMARY_REFERENCE_NOT_ALL_TIME_LATEST";
        }
        if (stalenessRow.exampleTargetTop1Last24h() > 0) {
            return "NOT_A_CANDIDATE_TARGET_STILL_PRESENT_IN_RECENT_EXAMPLE_WINDOW";
        }
        if (stalenessRow.realUserLatestUsersLast24h() <= 0) {
            return "NOT_A_CANDIDATE_NO_REAL_USER_RECENT_LATEST_USERS";
        }
        if (!"RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE".equals(recentWindowRecommendationReviewReading)) {
            return "NOT_A_CANDIDATE_RECENT_WINDOW_NOT_CLEAR";
        }
        return "RECENT_WINDOW_POLICY_CANDIDATE";
    }

    static String resolveReviewGatePolicyCandidateReason(
            String recommendationReviewGate,
            String recentWindowRecommendationReviewReading,
            boolean historicalExampleDominanceDetected,
            AdminDashboardReadRows.RecommendationReviewGateStalenessRow stalenessRow
    ) {
        String status = resolveReviewGatePolicyCandidateStatus(
                recommendationReviewGate,
                recentWindowRecommendationReviewReading,
                historicalExampleDominanceDetected,
                stalenessRow
        );

        return switch (status) {
            case "RECENT_WINDOW_POLICY_CANDIDATE" ->
                    "PRIMARY_GATE_BLOCKED_BY_STALE_ALL_TIME_EXAMPLE_REFERENCE_BUT_RECENT_WINDOW_CLEAR";
            case "NOT_A_CANDIDATE_PRIMARY_GATE_NOT_NON_REAL_BLOCKED" ->
                    "PRIMARY_REVIEW_GATE_IS_NOT_DEFERRED_NON_REAL_LEADER_SIGNAL";
            case "NOT_A_CANDIDATE_NO_HISTORICAL_EXAMPLE_DOMINANCE" ->
                    "HISTORICAL_EXAMPLE_DOMINANCE_NOT_DETECTED";
            case "NOT_A_CANDIDATE_PRIMARY_REFERENCE_NOT_ALL_TIME_LATEST" ->
                    "PRIMARY_REFERENCE_MODE_IS_NOT_ALL_TIME_LATEST_PER_USER";
            case "NOT_A_CANDIDATE_TARGET_STILL_PRESENT_IN_RECENT_EXAMPLE_WINDOW" ->
                    "TARGET_SERVICE_STILL_APPEARS_IN_RECENT_EXAMPLE_TOP1_WINDOW";
            case "NOT_A_CANDIDATE_NO_REAL_USER_RECENT_LATEST_USERS" ->
                    "REAL_USER_RECENT_LATEST_USERS_ARE_EMPTY";
            case "NOT_A_CANDIDATE_RECENT_WINDOW_NOT_CLEAR" ->
                    "RECENT_WINDOW_REVIEW_READING_HAS_NOT_CLEARED_HISTORICAL_2622_DOMINANCE";
            default -> status;
        };
    }

    static String resolveReviewGatePolicyPromotionStatus(
            String reviewGatePolicyCandidateStatus,
            AdminDashboardReadRows.RecommendationReviewGateStalenessRow stalenessRow
    ) {
        if (!"RECENT_WINDOW_POLICY_CANDIDATE".equals(reviewGatePolicyCandidateStatus)) {
            return "KEEP_PRIMARY_BASELINE";
        }
        if ("ALL_TIME_LATEST_PER_USER".equals(stalenessRow.primaryReferenceMode())) {
            return "REQUIRES_EXPLICIT_POLICY_CHANGE_REVIEW";
        }
        return "PROMOTION_READY";
    }

    static String resolveReviewGatePolicyPromotionReason(
            String reviewGatePolicyCandidateStatus,
            AdminDashboardReadRows.RecommendationReviewGateStalenessRow stalenessRow
    ) {
        String status = resolveReviewGatePolicyPromotionStatus(
                reviewGatePolicyCandidateStatus,
                stalenessRow
        );

        return switch (status) {
            case "REQUIRES_EXPLICIT_POLICY_CHANGE_REVIEW" ->
                    "RECENT_WINDOW_IS_A_CANDIDATE_BUT_PRIMARY_BASELINE_IS_STILL_ALL_TIME_LATEST";
            case "PROMOTION_READY" ->
                    "RECENT_WINDOW_CANDIDATE_CAN_BE_PROMOTED_WITHOUT_ALL_TIME_PRIMARY_REFERENCE";
            case "KEEP_PRIMARY_BASELINE" ->
                    "RECENT_WINDOW_CANDIDATE_HAS_NOT_CLEARED_PRIMARY_BASELINE_REQUIREMENTS";
            default -> status;
        };
    }

    static String resolveReviewGatePolicyPromotionActionStatus(
            String reviewGatePolicyPromotionStatus
    ) {
        return switch (reviewGatePolicyPromotionStatus) {
            case "PROMOTION_READY" -> "RUN_BOUNDED_PROMOTION_REVIEW";
            case "REQUIRES_EXPLICIT_POLICY_CHANGE_REVIEW", "KEEP_PRIMARY_BASELINE" -> "KEEP_PRIMARY_BASELINE";
            default -> "KEEP_PRIMARY_BASELINE";
        };
    }

    static String resolveReviewGatePolicyPromotionActionReason(
            String reviewGatePolicyPromotionStatus
    ) {
        return switch (reviewGatePolicyPromotionStatus) {
            case "PROMOTION_READY" -> "PROMOTION_PREREQUISITES_MET_FOR_BOUNDED_REVIEW";
            case "REQUIRES_EXPLICIT_POLICY_CHANGE_REVIEW" ->
                    "PROMOTION_STILL_REQUIRES_EXPLICIT_POLICY_REVIEW";
            case "KEEP_PRIMARY_BASELINE" ->
                    "RECENT_WINDOW_POLICY_PROMOTION_CONDITIONS_NOT_MET";
            default -> reviewGatePolicyPromotionStatus;
        };
    }

    static String resolveReviewGatePolicyPromotionReadinessStatus(
            String realUserTrafficGate,
            AdminDashboardReadRows.RecommendationConcentrationRow concentrationRow,
            String reviewGatePolicyCandidateStatus,
            String reviewGatePolicyPromotionStatus
    ) {
        if (!"READY_REAL_USER_TRAFFIC".equals(realUserTrafficGate)) {
            return "NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW";
        }
        if (!"READY_REAL_USER_COHORT".equals(concentrationRow.realUserCohortGate())) {
            return "NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW";
        }
        if (!"RECENT_WINDOW_POLICY_CANDIDATE".equals(reviewGatePolicyCandidateStatus)) {
            return "NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW";
        }
        if ("REQUIRES_EXPLICIT_POLICY_CHANGE_REVIEW".equals(reviewGatePolicyPromotionStatus)
                || "PROMOTION_READY".equals(reviewGatePolicyPromotionStatus)) {
            return "READY_FOR_BOUNDED_PROMOTION_REVIEW";
        }
        return "NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW";
    }

    static String resolveReviewGatePolicyPromotionReadinessReason(
            String realUserTrafficGate,
            AdminDashboardReadRows.RecommendationConcentrationRow concentrationRow,
            String reviewGatePolicyCandidateStatus,
            String reviewGatePolicyPromotionStatus
    ) {
        String status = resolveReviewGatePolicyPromotionReadinessStatus(
                realUserTrafficGate,
                concentrationRow,
                reviewGatePolicyCandidateStatus,
                reviewGatePolicyPromotionStatus
        );

        if ("READY_FOR_BOUNDED_PROMOTION_REVIEW".equals(status)) {
            if ("PROMOTION_READY".equals(reviewGatePolicyPromotionStatus)) {
                return "BOUNDED_PROMOTION_REVIEW_PREREQUISITES_MET";
            }
            return "EXPLICIT_POLICY_REVIEW_PENDING_WITH_BOUNDED_REVIEW_PREREQUISITES_MET";
        }
        if (!"READY_REAL_USER_TRAFFIC".equals(realUserTrafficGate)) {
            return "REAL_USER_TRAFFIC_GATE_NOT_READY";
        }
        if (!"READY_REAL_USER_COHORT".equals(concentrationRow.realUserCohortGate())) {
            return "REAL_USER_COHORT_GATE_NOT_READY";
        }
        if (!"RECENT_WINDOW_POLICY_CANDIDATE".equals(reviewGatePolicyCandidateStatus)) {
            return "RECENT_WINDOW_POLICY_CANDIDATE_NOT_CONFIRMED";
        }
        return "BOUNDED_PROMOTION_REVIEW_PREREQUISITES_NOT_MET";
    }

    static String resolveReviewGatePolicyPromotionExecutionStatus(
            String reviewGatePolicyPromotionReadinessStatus,
            String reviewGatePolicyPromotionStatus
    ) {
        if (!"READY_FOR_BOUNDED_PROMOTION_REVIEW".equals(reviewGatePolicyPromotionReadinessStatus)) {
            return "DO_NOT_RUN_BOUNDED_PROMOTION_REVIEW";
        }
        if ("REQUIRES_EXPLICIT_POLICY_CHANGE_REVIEW".equals(reviewGatePolicyPromotionStatus)) {
            return "AWAIT_EXPLICIT_POLICY_REVIEW_DECISION";
        }
        if ("PROMOTION_READY".equals(reviewGatePolicyPromotionStatus)) {
            return "RUN_BOUNDED_PROMOTION_REVIEW";
        }
        return "DO_NOT_RUN_BOUNDED_PROMOTION_REVIEW";
    }

    static String resolveReviewGatePolicyPromotionExecutionReason(
            String reviewGatePolicyPromotionReadinessStatus,
            String reviewGatePolicyPromotionReadinessReason,
            String reviewGatePolicyPromotionStatus
    ) {
        String status = resolveReviewGatePolicyPromotionExecutionStatus(
                reviewGatePolicyPromotionReadinessStatus,
                reviewGatePolicyPromotionStatus
        );

        return switch (status) {
            case "AWAIT_EXPLICIT_POLICY_REVIEW_DECISION" ->
                    "READINESS_MET_BUT_EXPLICIT_POLICY_REVIEW_DECISION_IS_STILL_PENDING";
            case "RUN_BOUNDED_PROMOTION_REVIEW" ->
                    "READINESS_MET_AND_PROMOTION_IS_READY_FOR_BOUNDED_REVIEW";
            case "DO_NOT_RUN_BOUNDED_PROMOTION_REVIEW" ->
                    reviewGatePolicyPromotionReadinessReason;
            default -> status;
        };
    }

    static String resolveReviewGatePolicyPromotionApprovalStatus(
            String reviewGatePolicyPromotionExecutionStatus
    ) {
        return switch (reviewGatePolicyPromotionExecutionStatus) {
            case "AWAIT_EXPLICIT_POLICY_REVIEW_DECISION" -> "PENDING_EXPLICIT_PROMOTION_APPROVAL";
            case "RUN_BOUNDED_PROMOTION_REVIEW" -> "BOUNDED_PROMOTION_REVIEW_APPROVED";
            case "DO_NOT_RUN_BOUNDED_PROMOTION_REVIEW" -> "PROMOTION_APPROVAL_NOT_APPLICABLE";
            default -> "PROMOTION_APPROVAL_NOT_APPLICABLE";
        };
    }

    static String resolveReviewGatePolicyPromotionApprovalReason(
            String reviewGatePolicyPromotionExecutionStatus,
            String reviewGatePolicyPromotionExecutionReason
    ) {
        String status = resolveReviewGatePolicyPromotionApprovalStatus(
                reviewGatePolicyPromotionExecutionStatus
        );

        return switch (status) {
            case "PENDING_EXPLICIT_PROMOTION_APPROVAL" ->
                    "EXECUTION_READY_BUT_EXPLICIT_PROMOTION_APPROVAL_NOT_RECORDED";
            case "BOUNDED_PROMOTION_REVIEW_APPROVED" ->
                    "EXECUTION_STATUS_ALREADY_ALLOWS_BOUNDED_PROMOTION_REVIEW";
            case "PROMOTION_APPROVAL_NOT_APPLICABLE" ->
                    reviewGatePolicyPromotionExecutionReason;
            default -> status;
        };
    }

    static String resolveReviewGatePolicyPromotionApprovalCriteriaStatus(
            String reviewGatePolicyPromotionReadinessStatus,
            String reviewGatePolicyCandidateStatus,
            String reviewGatePolicyPromotionStatus,
            String reviewGatePolicyPromotionExecutionStatus
    ) {
        if (!"READY_FOR_BOUNDED_PROMOTION_REVIEW".equals(reviewGatePolicyPromotionReadinessStatus)) {
            return "NOT_READY_FOR_EXPLICIT_PROMOTION_APPROVAL";
        }
        if (!"RECENT_WINDOW_POLICY_CANDIDATE".equals(reviewGatePolicyCandidateStatus)) {
            return "NOT_READY_FOR_EXPLICIT_PROMOTION_APPROVAL";
        }
        if (!"REQUIRES_EXPLICIT_POLICY_CHANGE_REVIEW".equals(reviewGatePolicyPromotionStatus)
                && !"PROMOTION_READY".equals(reviewGatePolicyPromotionStatus)) {
            return "NOT_READY_FOR_EXPLICIT_PROMOTION_APPROVAL";
        }
        if (!"AWAIT_EXPLICIT_POLICY_REVIEW_DECISION".equals(reviewGatePolicyPromotionExecutionStatus)
                && !"RUN_BOUNDED_PROMOTION_REVIEW".equals(reviewGatePolicyPromotionExecutionStatus)) {
            return "NOT_READY_FOR_EXPLICIT_PROMOTION_APPROVAL";
        }
        return "READY_FOR_EXPLICIT_PROMOTION_APPROVAL";
    }

    static String resolveReviewGatePolicyPromotionApprovalCriteriaReason(
            String reviewGatePolicyPromotionReadinessStatus,
            String reviewGatePolicyPromotionReadinessReason,
            String reviewGatePolicyCandidateStatus,
            String reviewGatePolicyPromotionStatus,
            String reviewGatePolicyPromotionExecutionStatus
    ) {
        String status = resolveReviewGatePolicyPromotionApprovalCriteriaStatus(
                reviewGatePolicyPromotionReadinessStatus,
                reviewGatePolicyCandidateStatus,
                reviewGatePolicyPromotionStatus,
                reviewGatePolicyPromotionExecutionStatus
        );
        if ("READY_FOR_EXPLICIT_PROMOTION_APPROVAL".equals(status)) {
            if ("PROMOTION_READY".equals(reviewGatePolicyPromotionStatus)) {
                return "PROMOTION_READY_AND_EXECUTION_LAYER_ALIGNED";
            }
            return "PRIMARY_STALENESS_AND_RECENT_WINDOW_SIGNAL_CONFIRMED";
        }
        if (!"READY_FOR_BOUNDED_PROMOTION_REVIEW".equals(reviewGatePolicyPromotionReadinessStatus)) {
            return reviewGatePolicyPromotionReadinessReason;
        }
        if (!"RECENT_WINDOW_POLICY_CANDIDATE".equals(reviewGatePolicyCandidateStatus)) {
            return "RECENT_WINDOW_POLICY_CANDIDATE_NOT_CONFIRMED";
        }
        if (!"REQUIRES_EXPLICIT_POLICY_CHANGE_REVIEW".equals(reviewGatePolicyPromotionStatus)
                && !"PROMOTION_READY".equals(reviewGatePolicyPromotionStatus)) {
            return "PROMOTION_REVIEW_STATE_NOT_ACTIVE";
        }
        return "PROMOTION_EXECUTION_LAYER_NOT_READY_FOR_EXPLICIT_APPROVAL";
    }

    static String resolveReviewGatePolicyPromotionApprovalDecisionStatus(
            String reviewGatePolicyPromotionApprovalCriteriaStatus,
            String reviewGatePolicyPromotionApprovalStatus
    ) {
        if (!"READY_FOR_EXPLICIT_PROMOTION_APPROVAL".equals(reviewGatePolicyPromotionApprovalCriteriaStatus)) {
            return "APPROVAL_DECISION_NOT_READY";
        }
        return switch (reviewGatePolicyPromotionApprovalStatus) {
            case "PENDING_EXPLICIT_PROMOTION_APPROVAL" -> "AWAIT_EXPLICIT_PROMOTION_APPROVAL_DECISION";
            case "BOUNDED_PROMOTION_REVIEW_APPROVED" -> "APPROVED_FOR_BOUNDED_PROMOTION_REVIEW";
            case "PROMOTION_APPROVAL_NOT_APPLICABLE" -> "APPROVAL_DECISION_NOT_APPLICABLE";
            default -> "APPROVAL_DECISION_NOT_APPLICABLE";
        };
    }

    static String resolveReviewGatePolicyPromotionApprovalDecisionReason(
            String reviewGatePolicyPromotionApprovalCriteriaStatus,
            String reviewGatePolicyPromotionApprovalCriteriaReason,
            String reviewGatePolicyPromotionApprovalStatus,
            String reviewGatePolicyPromotionApprovalReason
    ) {
        String status = resolveReviewGatePolicyPromotionApprovalDecisionStatus(
                reviewGatePolicyPromotionApprovalCriteriaStatus,
                reviewGatePolicyPromotionApprovalStatus
        );

        return switch (status) {
            case "AWAIT_EXPLICIT_PROMOTION_APPROVAL_DECISION" ->
                    "APPROVAL_CRITERIA_MET_BUT_EXPLICIT_APPROVAL_NOT_RECORDED";
            case "APPROVED_FOR_BOUNDED_PROMOTION_REVIEW" ->
                    "EXPLICIT_PROMOTION_APPROVAL_RECORDED";
            case "APPROVAL_DECISION_NOT_READY" ->
                    reviewGatePolicyPromotionApprovalCriteriaReason;
            case "APPROVAL_DECISION_NOT_APPLICABLE" ->
                    reviewGatePolicyPromotionApprovalReason;
            default -> status;
        };
    }

    static String resolveReviewGatePolicyPromotionApprovalRecordStatus(
            String reviewGatePolicyPromotionApprovalDecisionStatus
    ) {
        return switch (reviewGatePolicyPromotionApprovalDecisionStatus) {
            case "APPROVAL_DECISION_NOT_READY" -> "APPROVAL_RECORD_NOT_READY";
            case "AWAIT_EXPLICIT_PROMOTION_APPROVAL_DECISION" -> "PENDING_EXPLICIT_PROMOTION_APPROVAL_RECORD";
            case "APPROVED_FOR_BOUNDED_PROMOTION_REVIEW" -> "EXPLICIT_PROMOTION_APPROVAL_RECORDED";
            case "APPROVAL_DECISION_NOT_APPLICABLE" -> "APPROVAL_RECORD_NOT_APPLICABLE";
            default -> "APPROVAL_RECORD_NOT_APPLICABLE";
        };
    }

    static String resolveReviewGatePolicyPromotionApprovalRecordReason(
            String reviewGatePolicyPromotionApprovalDecisionStatus,
            String reviewGatePolicyPromotionApprovalDecisionReason
    ) {
        String status = resolveReviewGatePolicyPromotionApprovalRecordStatus(
                reviewGatePolicyPromotionApprovalDecisionStatus
        );

        return switch (status) {
            case "PENDING_EXPLICIT_PROMOTION_APPROVAL_RECORD" ->
                    "APPROVAL_DECISION_PENDING_AND_RECORD_NOT_WRITTEN";
            case "EXPLICIT_PROMOTION_APPROVAL_RECORDED" ->
                    "APPROVAL_RECORD_ALLOWS_BOUNDED_PROMOTION_REVIEW";
            case "APPROVAL_RECORD_NOT_READY", "APPROVAL_RECORD_NOT_APPLICABLE" ->
                    reviewGatePolicyPromotionApprovalDecisionReason;
            default -> status;
        };
    }

    static String resolveReviewGatePolicyPromotionReviewRunStatus(
            String reviewGatePolicyPromotionApprovalRecordStatus
    ) {
        return switch (reviewGatePolicyPromotionApprovalRecordStatus) {
            case "APPROVAL_RECORD_NOT_READY" -> "BOUNDED_PROMOTION_REVIEW_RUN_NOT_READY";
            case "PENDING_EXPLICIT_PROMOTION_APPROVAL_RECORD" -> "PENDING_BOUNDED_PROMOTION_REVIEW_RUN";
            case "EXPLICIT_PROMOTION_APPROVAL_RECORDED" -> "AWAIT_BOUNDED_PROMOTION_REVIEW_RUN";
            case "APPROVAL_RECORD_NOT_APPLICABLE" -> "BOUNDED_PROMOTION_REVIEW_RUN_NOT_APPLICABLE";
            default -> "BOUNDED_PROMOTION_REVIEW_RUN_NOT_APPLICABLE";
        };
    }

    static String resolveReviewGatePolicyPromotionReviewRunCriteriaStatus(
            String reviewGatePolicyPromotionApprovalCriteriaStatus,
            String reviewGatePolicyPromotionApprovalRecordStatus
    ) {
        if (!"READY_FOR_EXPLICIT_PROMOTION_APPROVAL".equals(reviewGatePolicyPromotionApprovalCriteriaStatus)) {
            return "NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN";
        }
        return switch (reviewGatePolicyPromotionApprovalRecordStatus) {
            case "PENDING_EXPLICIT_PROMOTION_APPROVAL_RECORD", "EXPLICIT_PROMOTION_APPROVAL_RECORDED" ->
                    "READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN";
            case "APPROVAL_RECORD_NOT_READY", "APPROVAL_RECORD_NOT_APPLICABLE" ->
                    "NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN";
            default -> "NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN";
        };
    }

    static String resolveReviewGatePolicyPromotionReviewRunCriteriaReason(
            String reviewGatePolicyPromotionApprovalCriteriaStatus,
            String reviewGatePolicyPromotionApprovalCriteriaReason,
            String reviewGatePolicyPromotionApprovalRecordStatus,
            String reviewGatePolicyPromotionApprovalRecordReason
    ) {
        String status = resolveReviewGatePolicyPromotionReviewRunCriteriaStatus(
                reviewGatePolicyPromotionApprovalCriteriaStatus,
                reviewGatePolicyPromotionApprovalRecordStatus
        );

        if ("READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN".equals(status)) {
            if ("EXPLICIT_PROMOTION_APPROVAL_RECORDED".equals(reviewGatePolicyPromotionApprovalRecordStatus)) {
                return "EXPLICIT_APPROVAL_RECORD_SUPPORTS_BOUNDED_REVIEW_RUN";
            }
            return "BOUNDED_REVIEW_RUN_PREREQUISITES_MET_BUT_APPROVAL_RECORD_PENDING";
        }
        if (!"READY_FOR_EXPLICIT_PROMOTION_APPROVAL".equals(reviewGatePolicyPromotionApprovalCriteriaStatus)) {
            return reviewGatePolicyPromotionApprovalCriteriaReason;
        }
        return reviewGatePolicyPromotionApprovalRecordReason;
    }

    static String resolveReviewGatePolicyPromotionReviewRunDecisionStatus(
            String reviewGatePolicyPromotionReviewRunCriteriaStatus,
            String reviewGatePolicyPromotionReviewRunStatus
    ) {
        if (!"READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN".equals(reviewGatePolicyPromotionReviewRunCriteriaStatus)) {
            return "BOUNDED_PROMOTION_REVIEW_RUN_DECISION_NOT_READY";
        }
        return switch (reviewGatePolicyPromotionReviewRunStatus) {
            case "PENDING_BOUNDED_PROMOTION_REVIEW_RUN" -> "AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_DECISION";
            case "AWAIT_BOUNDED_PROMOTION_REVIEW_RUN" -> "BOUNDED_PROMOTION_REVIEW_RUN_APPROVED";
            case "BOUNDED_PROMOTION_REVIEW_RUN_NOT_READY" ->
                    "BOUNDED_PROMOTION_REVIEW_RUN_DECISION_NOT_READY";
            case "BOUNDED_PROMOTION_REVIEW_RUN_NOT_APPLICABLE" ->
                    "BOUNDED_PROMOTION_REVIEW_RUN_DECISION_NOT_APPLICABLE";
            default -> "BOUNDED_PROMOTION_REVIEW_RUN_DECISION_NOT_APPLICABLE";
        };
    }

    static String resolveReviewGatePolicyPromotionReviewRunDecisionReason(
            String reviewGatePolicyPromotionReviewRunCriteriaStatus,
            String reviewGatePolicyPromotionReviewRunCriteriaReason,
            String reviewGatePolicyPromotionReviewRunStatus,
            String reviewGatePolicyPromotionReviewRunReason
    ) {
        String status = resolveReviewGatePolicyPromotionReviewRunDecisionStatus(
                reviewGatePolicyPromotionReviewRunCriteriaStatus,
                reviewGatePolicyPromotionReviewRunStatus
        );

        return switch (status) {
            case "AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_DECISION" ->
                    "REVIEW_RUN_CRITERIA_MET_BUT_APPROVAL_RECORD_NOT_WRITTEN";
            case "BOUNDED_PROMOTION_REVIEW_RUN_APPROVED" ->
                    "APPROVAL_RECORD_SUPPORTS_BOUNDED_REVIEW_RUN_EXECUTION";
            case "BOUNDED_PROMOTION_REVIEW_RUN_DECISION_NOT_READY" ->
                    reviewGatePolicyPromotionReviewRunCriteriaReason;
            case "BOUNDED_PROMOTION_REVIEW_RUN_DECISION_NOT_APPLICABLE" ->
                    reviewGatePolicyPromotionReviewRunReason;
            default -> status;
        };
    }

    static String resolveReviewGatePolicyPromotionReviewRunApprovalStatus(
            String reviewGatePolicyPromotionReviewRunDecisionStatus
    ) {
        return switch (reviewGatePolicyPromotionReviewRunDecisionStatus) {
            case "BOUNDED_PROMOTION_REVIEW_RUN_DECISION_NOT_READY" ->
                    "BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_NOT_READY";
            case "AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_DECISION" ->
                    "PENDING_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL";
            case "BOUNDED_PROMOTION_REVIEW_RUN_APPROVED" ->
                    "APPROVED_FOR_BOUNDED_PROMOTION_REVIEW_RUN";
            case "BOUNDED_PROMOTION_REVIEW_RUN_DECISION_NOT_APPLICABLE" ->
                    "BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_NOT_APPLICABLE";
            default -> "BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_NOT_APPLICABLE";
        };
    }

    static String resolveReviewGatePolicyPromotionReviewRunApprovalCriteriaStatus(
            String reviewGatePolicyPromotionReviewRunDecisionStatus
    ) {
        return switch (reviewGatePolicyPromotionReviewRunDecisionStatus) {
            case "BOUNDED_PROMOTION_REVIEW_RUN_DECISION_NOT_READY" ->
                    "NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL";
            case "AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_DECISION",
                 "BOUNDED_PROMOTION_REVIEW_RUN_APPROVED" ->
                    "READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL";
            case "BOUNDED_PROMOTION_REVIEW_RUN_DECISION_NOT_APPLICABLE" ->
                    "BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_NOT_APPLICABLE";
            default -> "BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_NOT_APPLICABLE";
        };
    }

    static String resolveReviewGatePolicyPromotionReviewRunApprovalCriteriaReason(
            String reviewGatePolicyPromotionReviewRunDecisionStatus,
            String reviewGatePolicyPromotionReviewRunDecisionReason
    ) {
        String status = resolveReviewGatePolicyPromotionReviewRunApprovalCriteriaStatus(
                reviewGatePolicyPromotionReviewRunDecisionStatus
        );

        return switch (status) {
            case "READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL" -> {
                if ("BOUNDED_PROMOTION_REVIEW_RUN_APPROVED".equals(reviewGatePolicyPromotionReviewRunDecisionStatus)) {
                    yield "REVIEW_RUN_APPROVAL_SUPPORTED_BY_EXECUTION_READY_DECISION";
                }
                yield "REVIEW_RUN_APPROVAL_PREREQUISITES_MET_BUT_APPROVAL_RECORD_PENDING";
            }
            case "NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL",
                 "BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_NOT_APPLICABLE" ->
                    reviewGatePolicyPromotionReviewRunDecisionReason;
            default -> status;
        };
    }

    static String resolveReviewGatePolicyPromotionReviewRunApprovalReason(
            String reviewGatePolicyPromotionReviewRunDecisionStatus,
            String reviewGatePolicyPromotionReviewRunDecisionReason
    ) {
        String status = resolveReviewGatePolicyPromotionReviewRunApprovalStatus(
                reviewGatePolicyPromotionReviewRunDecisionStatus
        );

        return switch (status) {
            case "PENDING_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL" ->
                    "REVIEW_RUN_DECISION_PENDING_BECAUSE_APPROVAL_RECORD_NOT_WRITTEN";
            case "APPROVED_FOR_BOUNDED_PROMOTION_REVIEW_RUN" ->
                    "REVIEW_RUN_DECISION_SUPPORTS_EXECUTION";
            case "BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_NOT_READY",
                 "BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_NOT_APPLICABLE" ->
                    reviewGatePolicyPromotionReviewRunDecisionReason;
            default -> status;
        };
    }

    static String resolveReviewGatePolicyPromotionReviewRunReason(
            String reviewGatePolicyPromotionApprovalRecordStatus,
            String reviewGatePolicyPromotionApprovalRecordReason
    ) {
        String status = resolveReviewGatePolicyPromotionReviewRunStatus(
                reviewGatePolicyPromotionApprovalRecordStatus
        );

        return switch (status) {
            case "PENDING_BOUNDED_PROMOTION_REVIEW_RUN" ->
                    "EXPLICIT_APPROVAL_RECORD_NOT_WRITTEN_FOR_BOUNDED_REVIEW_RUN";
            case "AWAIT_BOUNDED_PROMOTION_REVIEW_RUN" ->
                    "APPROVAL_RECORDED_BUT_BOUNDED_REVIEW_RUN_NOT_EXECUTED";
            case "BOUNDED_PROMOTION_REVIEW_RUN_NOT_READY", "BOUNDED_PROMOTION_REVIEW_RUN_NOT_APPLICABLE" ->
                    reviewGatePolicyPromotionApprovalRecordReason;
            default -> status;
        };
    }
}
