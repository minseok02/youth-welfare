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
}
