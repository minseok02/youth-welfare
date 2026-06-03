package com.example.welfare.collect.dto;

import java.time.LocalDateTime;

public record AsyncCollectStatusResponse(
        String sourceKey,
        String jobName,
        AsyncCollectState state,
        boolean active,
        String message,
        LocalDateTime requestedAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String errorCode,
        String errorMessage,
        LatestCollectLog latestLog
) {

    public enum AsyncCollectState {
        IDLE,
        QUEUED,
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    public record LatestCollectLog(
            Long id,
            String status,
            int requestedCount,
            int savedCount,
            int skippedCount,
            int filteredCount,
            int failedCount,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String metadataJson
    ) {
    }
}
