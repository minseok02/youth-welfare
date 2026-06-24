package com.example.welfare.admin.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AdminNotificationAttemptSummaryResponse(
        LocalDateTime generatedAt,
        int windowDays,
        long totalAttempts,
        long successAttempts,
        long failedAttempts,
        long disabledAttempts,
        BigDecimal averageDurationMs,
        LocalDateTime latestAttemptAt,
        List<Breakdown> breakdowns,
        List<RecentFailure> recentFailures
) {

    public record Breakdown(
            String channel,
            String kind,
            String outcome,
            long attemptCount,
            BigDecimal averageDurationMs,
            LocalDateTime latestAttemptAt
    ) {
    }

    public record RecentFailure(
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
}
