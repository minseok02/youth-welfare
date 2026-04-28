package com.example.welfare.user.dto.response;

public record UserPiiSyncReplayResponse(
        int attemptedCount,
        int syncedCount,
        int failedCount,
        int missingCount
) {
}
