package com.example.welfare.admin.dashboard.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminCollectFailureResponse(
        LocalDateTime generatedAt,
        int windowDays,
        long totalFailedJobs,
        long totalPartialSuccessJobs,
        List<JobBreakdown> jobBreakdowns,
        List<ErrorCodeBreakdown> errorCodeBreakdowns,
        List<FailureSample> recentSamples
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
}
