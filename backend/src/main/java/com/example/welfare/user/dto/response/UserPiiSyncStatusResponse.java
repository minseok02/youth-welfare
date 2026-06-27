package com.example.welfare.user.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record UserPiiSyncStatusResponse(
        long pendingCount,
        long failedCount,
        long syncedCount,
        String oldestPendingUserKeyHash,
        LocalDateTime oldestPendingEnqueuedAt,
        String oldestFailedUserKeyHash,
        LocalDateTime oldestFailedAttemptAt,
        LocalDateTime latestSyncedAt,
        List<UserPiiSyncFailedSampleResponse> failedSamples
) {
}
