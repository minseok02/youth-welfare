package com.example.welfare.user.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record UserPiiSyncStatusResponse(
        long pendingCount,
        long failedCount,
        long syncedCount,
        String oldestPendingUserKey,
        LocalDateTime oldestPendingEnqueuedAt,
        String oldestFailedUserKey,
        LocalDateTime oldestFailedAttemptAt,
        LocalDateTime latestSyncedAt,
        List<UserPiiSyncFailedSampleResponse> failedSamples
) {
}
