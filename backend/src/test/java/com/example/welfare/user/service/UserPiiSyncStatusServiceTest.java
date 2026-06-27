package com.example.welfare.user.service;

import com.example.welfare.global.util.RedisKeyHash;
import com.example.welfare.user.dto.response.UserPiiSyncStatusResponse;
import com.example.welfare.user.entity.UserPiiSyncQueue;
import com.example.welfare.user.entity.UserPiiSyncQueueStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserPiiSyncStatusServiceTest {

    @Mock
    private UserPiiSyncQueueService userPiiSyncQueueService;

    @Test
    @DisplayName("queue status는 count와 oldest/latest snapshot, failed sample을 함께 반환한다")
    void getStatusBuildsQueueSnapshot() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 28, 20, 30, 0);
        UserPiiSyncQueue pending = UserPiiSyncQueue.builder()
                .userKey("pending-user")
                .status(UserPiiSyncQueueStatus.PENDING)
                .lastEnqueuedAt(now.minusMinutes(20))
                .build();
        UserPiiSyncQueue failed = UserPiiSyncQueue.builder()
                .userKey("failed-user")
                .status(UserPiiSyncQueueStatus.FAILED)
                .attemptCount(3)
                .lastAttemptAt(now.minusMinutes(10))
                .lastError("app_pii timeout")
                .build();
        UserPiiSyncQueue synced = UserPiiSyncQueue.builder()
                .userKey("synced-user")
                .status(UserPiiSyncQueueStatus.SYNCED)
                .lastSyncedAt(now.minusMinutes(1))
                .build();

        given(userPiiSyncQueueService.countByStatus(UserPiiSyncQueueStatus.PENDING)).willReturn(2L);
        given(userPiiSyncQueueService.countByStatus(UserPiiSyncQueueStatus.FAILED)).willReturn(1L);
        given(userPiiSyncQueueService.countByStatus(UserPiiSyncQueueStatus.SYNCED)).willReturn(7L);
        given(userPiiSyncQueueService.findOldestPending()).willReturn(Optional.of(pending));
        given(userPiiSyncQueueService.findOldestFailed()).willReturn(Optional.of(failed));
        given(userPiiSyncQueueService.findLatestSynced()).willReturn(Optional.of(synced));
        given(userPiiSyncQueueService.findFailedSamples(5)).willReturn(List.of(failed));

        UserPiiSyncStatusService service = new UserPiiSyncStatusService(userPiiSyncQueueService);

        UserPiiSyncStatusResponse response = service.getStatus(5);

        assertThat(response.pendingCount()).isEqualTo(2);
        assertThat(response.failedCount()).isEqualTo(1);
        assertThat(response.syncedCount()).isEqualTo(7);
        assertThat(response.oldestPendingUserKeyHash()).isEqualTo(RedisKeyHash.sha256Hex("pending-user"));
        assertThat(response.oldestPendingEnqueuedAt()).isEqualTo(now.minusMinutes(20));
        assertThat(response.oldestFailedUserKeyHash()).isEqualTo(RedisKeyHash.sha256Hex("failed-user"));
        assertThat(response.oldestFailedAttemptAt()).isEqualTo(now.minusMinutes(10));
        assertThat(response.latestSyncedAt()).isEqualTo(now.minusMinutes(1));
        assertThat(response.failedSamples()).hasSize(1);
        assertThat(response.failedSamples().get(0).userKeyHash()).isEqualTo(RedisKeyHash.sha256Hex("failed-user"));
        assertThat(response.failedSamples().get(0).attemptCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("failed sample limit은 과도한 조회를 막기 위해 1 이상 20 이하로 고정한다")
    void getStatusClampsFailedSampleLimit() {
        given(userPiiSyncQueueService.countByStatus(org.mockito.ArgumentMatchers.any(UserPiiSyncQueueStatus.class))).willReturn(0L);
        given(userPiiSyncQueueService.findOldestPending()).willReturn(Optional.empty());
        given(userPiiSyncQueueService.findOldestFailed()).willReturn(Optional.empty());
        given(userPiiSyncQueueService.findLatestSynced()).willReturn(Optional.empty());
        given(userPiiSyncQueueService.findFailedSamples(1)).willReturn(List.of());
        given(userPiiSyncQueueService.findFailedSamples(20)).willReturn(List.of());

        UserPiiSyncStatusService service = new UserPiiSyncStatusService(userPiiSyncQueueService);
        service.getStatus(0);
        service.getStatus(999);
    }
}
