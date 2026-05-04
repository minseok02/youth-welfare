package com.example.welfare.admin.dashboard.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

final class AdminDashboardQueryPolicy {

    static final int FAILED_SAMPLE_LIMIT = 5;
    static final int COLLECT_FAILURE_PATTERN_LIMIT = 5;
    static final int COLLECT_STREAK_RUN_LIMIT = 20;
    static final int SEARCH_FAILURE_PATTERN_LIMIT = 5;
    static final int RECOMMENDATION_BREAKDOWN_LIMIT = 5;
    static final int DEFAULT_SUMMARY_WINDOW_DAYS = 7;
    static final List<Integer> DEFAULT_TREND_WINDOWS_DAYS = List.of(1, 7, 30);
    static final int MAX_WINDOW_DAYS = 365;
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
}
