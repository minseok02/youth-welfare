package com.example.welfare.integration;

import com.example.welfare.user.dto.response.UserPiiSyncStatusResponse;
import com.example.welfare.user.entity.UserPiiSyncQueue;
import com.example.welfare.user.entity.UserPiiSyncQueueStatus;
import com.example.welfare.user.repository.UserPiiSyncQueueRepository;
import com.example.welfare.user.service.UserPiiSyncStatusService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
class UserPiiSyncStatusIntegrationTest {

    @Autowired
    private UserPiiSyncQueueRepository userPiiSyncQueueRepository;

    @Autowired
    private UserPiiSyncStatusService userPiiSyncStatusService;

    private final List<String> createdUserKeys = new ArrayList<>();

    @AfterEach
    void cleanup() {
        createdUserKeys.forEach(userPiiSyncQueueRepository::deleteByUserKey);
        createdUserKeys.clear();
    }

    @Test
    @DisplayName("queue status는 DB 기준 count와 oldest pending/failed, failed sample을 집계한다")
    void getStatusSummarizesQueueRows() {
        LocalDateTime baseTime = LocalDateTime.of(2026, 4, 28, 21, 0, 0);
        String pendingKey = saveQueue(UserPiiSyncQueue.builder()
                .userKey(newUserKey())
                .status(UserPiiSyncQueueStatus.PENDING)
                .emailEnc("pending-email")
                .lastEnqueuedAt(baseTime.minusMinutes(30))
                .build());
        String failedHighAttemptKey = saveQueue(UserPiiSyncQueue.builder()
                .userKey(newUserKey())
                .status(UserPiiSyncQueueStatus.FAILED)
                .attemptCount(4)
                .lastAttemptAt(baseTime.minusMinutes(25))
                .lastError("app_pii timeout")
                .build());
        saveQueue(UserPiiSyncQueue.builder()
                .userKey(newUserKey())
                .status(UserPiiSyncQueueStatus.FAILED)
                .attemptCount(1)
                .lastAttemptAt(baseTime.minusMinutes(5))
                .lastError("transient retry")
                .build());
        saveQueue(UserPiiSyncQueue.builder()
                .userKey(newUserKey())
                .status(UserPiiSyncQueueStatus.SYNCED)
                .attemptCount(1)
                .lastSyncedAt(baseTime.minusMinutes(1))
                .build());

        UserPiiSyncStatusResponse response = userPiiSyncStatusService.getStatus(1);

        assertThat(response.pendingCount()).isEqualTo(1);
        assertThat(response.failedCount()).isEqualTo(2);
        assertThat(response.syncedCount()).isEqualTo(1);
        assertThat(response.oldestPendingUserKey()).isEqualTo(pendingKey);
        assertThat(response.oldestPendingEnqueuedAt()).isEqualTo(baseTime.minusMinutes(30));
        assertThat(response.oldestFailedUserKey()).isEqualTo(failedHighAttemptKey);
        assertThat(response.oldestFailedAttemptAt()).isEqualTo(baseTime.minusMinutes(25));
        assertThat(response.latestSyncedAt()).isEqualTo(baseTime.minusMinutes(1));
        assertThat(response.failedSamples()).hasSize(1);
        assertThat(response.failedSamples().get(0).userKey()).isEqualTo(failedHighAttemptKey);
        assertThat(response.failedSamples().get(0).attemptCount()).isEqualTo(4);
        assertThat(response.failedSamples().get(0).lastError()).isEqualTo("app_pii timeout");
    }

    private String saveQueue(UserPiiSyncQueue queue) {
        createdUserKeys.add(queue.getUserKey());
        return userPiiSyncQueueRepository.save(queue).getUserKey();
    }

    private String newUserKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
