package com.example.welfare.user.service;

import com.example.welfare.user.dto.response.UserPiiSyncFailedSampleResponse;
import com.example.welfare.user.dto.response.UserPiiSyncStatusResponse;
import com.example.welfare.user.entity.UserPiiSyncQueue;
import com.example.welfare.user.entity.UserPiiSyncQueueStatus;
import com.example.welfare.user.repository.UserPiiSyncQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserPiiSyncStatusService {

    private static final int MIN_FAILED_SAMPLE_LIMIT = 1;
    private static final int MAX_FAILED_SAMPLE_LIMIT = 20;

    private final UserPiiSyncQueueRepository userPiiSyncQueueRepository;

    public UserPiiSyncStatusResponse getStatus(int rawFailedSampleLimit) {
        int failedSampleLimit = Math.max(MIN_FAILED_SAMPLE_LIMIT,
                Math.min(rawFailedSampleLimit, MAX_FAILED_SAMPLE_LIMIT));

        long pendingCount = userPiiSyncQueueRepository.countByStatus(UserPiiSyncQueueStatus.PENDING);
        long failedCount = userPiiSyncQueueRepository.countByStatus(UserPiiSyncQueueStatus.FAILED);
        long syncedCount = userPiiSyncQueueRepository.countByStatus(UserPiiSyncQueueStatus.SYNCED);

        UserPiiSyncQueue oldestPending = userPiiSyncQueueRepository
                .findFirstByStatusOrderByLastEnqueuedAtAscIdAsc(UserPiiSyncQueueStatus.PENDING)
                .orElse(null);
        UserPiiSyncQueue oldestFailed = userPiiSyncQueueRepository
                .findFirstByStatusOrderByLastAttemptAtAscIdAsc(UserPiiSyncQueueStatus.FAILED)
                .orElse(null);
        UserPiiSyncQueue latestSynced = userPiiSyncQueueRepository
                .findFirstByStatusOrderByLastSyncedAtDescIdDesc(UserPiiSyncQueueStatus.SYNCED)
                .orElse(null);

        List<UserPiiSyncFailedSampleResponse> failedSamples = userPiiSyncQueueRepository
                .findByStatusOrderByAttemptCountDescLastAttemptAtDescIdDesc(
                        UserPiiSyncQueueStatus.FAILED,
                        PageRequest.of(0, failedSampleLimit)
                ).stream()
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
