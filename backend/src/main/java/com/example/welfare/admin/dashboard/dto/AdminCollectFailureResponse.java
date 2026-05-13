package com.example.welfare.admin.dashboard.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminCollectFailureResponse(
        LocalDateTime generatedAt,
        int windowDays,
        long failedJobsInWindow,
        long partialSuccessJobsInWindow,
        List<JobBreakdown> jobBreakdowns,
        List<JobStreak> currentJobStreaks,
        List<ErrorCodeBreakdown> errorCodeBreakdowns,
        List<FailureSample> recentSamples,
        List<CircuitStatus> circuitStatuses
) {

    public record JobBreakdown(
            String jobName,
            long failedCount,
            long partialSuccessCount,
            LocalDateTime latestStartedAt
    ) {
    }

    public record ErrorCodeBreakdown(
            String errorCode,
            long failedCount
    ) {
    }

    public record JobStreak(
            String jobName,
            String streakStatus,
            long streakCount,
            LocalDateTime latestStartedAt
    ) {
    }

    public record FailureSample(
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

    public record CircuitStatus(
            String circuitKey,
            boolean open,
            long remainingMs,
            LocalDateTime openUntil
    ) {
    }
}
