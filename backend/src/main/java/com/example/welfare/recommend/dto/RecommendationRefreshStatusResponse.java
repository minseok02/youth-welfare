package com.example.welfare.recommend.dto;

import java.time.LocalDateTime;

public record RecommendationRefreshStatusResponse(
        RecommendationRefreshState state,
        boolean active,
        boolean personal,
        String message,
        LocalDateTime requestedAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime latestRecommendedAt,
        int savedCount,
        String errorCode,
        Long generationDurationMs,
        Long pollAfterMs
) {

    public enum RecommendationRefreshState {
        IDLE,
        QUEUED,
        RUNNING,
        SUCCEEDED,
        FAILED,
        RATE_LIMITED
    }
}
