package com.example.welfare.user.service;

import com.example.welfare.user.dto.response.UserPiiSyncFailedSampleResponse;
import com.example.welfare.user.dto.response.UserPiiSyncStatusResponse;
import com.example.welfare.user.entity.UserPiiSyncQueue;
import com.example.welfare.user.entity.UserPiiSyncQueueStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserPiiSyncStatusService {

    private static final int MIN_FAILED_SAMPLE_LIMIT = 1;
    private static final int MAX_FAILED_SAMPLE_LIMIT = 20;

    private final UserPiiSyncQueueService userPiiSyncQueueService;

    public UserPiiSyncStatusResponse getStatus(int rawFailedSampleLimit) {
        int failedSampleLimit = Math.max(MIN_FAILED_SAMPLE_LIMIT,
                Math.min(rawFailedSampleLimit, MAX_FAILED_SAMPLE_LIMIT));

        long pendingCount = userPiiSyncQueueService.countByStatus(UserPiiSyncQueueStatus.PENDING);
        long failedCount = userPiiSyncQueueService.countByStatus(UserPiiSyncQueueStatus.FAILED);
        long syncedCount = userPiiSyncQueueService.countByStatus(UserPiiSyncQueueStatus.SYNCED);

        UserPiiSyncQueue oldestPending = userPiiSyncQueueService.findOldestPending().orElse(null);
        UserPiiSyncQueue oldestFailed = userPiiSyncQueueService.findOldestFailed().orElse(null);
        UserPiiSyncQueue latestSynced = userPiiSyncQueueService.findLatestSynced().orElse(null);

        List<UserPiiSyncFailedSampleResponse> failedSamples = userPiiSyncQueueService.findFailedSamples(failedSampleLimit).stream()
                .map(UserPiiSyncFailedSampleResponse::from)
                .toList();

        return new UserPiiSyncStatusResponse(
                pendingCount,
                failedCount,
                syncedCount,
                oldestPending != null ? oldestPending.getUserKey() : null,
                oldestPending != null ? oldestPending.getLastEnqueuedAt() : null,
                oldestFailed != null ? oldestFailed.getUserKey() : null,
                oldestFailed != null ? oldestFailed.getLastAttemptAt() : null,
                latestSynced != null ? latestSynced.getLastSyncedAt() : null,
                failedSamples
        );
    }
}
