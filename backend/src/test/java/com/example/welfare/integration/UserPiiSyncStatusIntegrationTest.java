package com.example.welfare.integration;

import com.example.welfare.global.util.RedisKeyHash;
import com.example.welfare.user.dto.response.UserPiiSyncStatusResponse;
import com.example.welfare.user.entity.UserPiiSyncQueue;
import com.example.welfare.user.entity.UserPiiSyncQueueStatus;
import com.example.welfare.user.repository.UserPiiSyncQueueRepository;
import com.example.welfare.user.service.UserPiiSyncStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
class UserPiiSyncStatusIntegrationTest {

    private static final String TEST_USER_KEY_PREFIX = "itpiis_";

    @Autowired
    private UserPiiSyncQueueRepository userPiiSyncQueueRepository;

    @Autowired
    private UserPiiSyncStatusService userPiiSyncStatusService;

    @BeforeEach
    void setup() {
        cleanup();
    }

    @AfterEach
    void cleanup() {
        userPiiSyncQueueRepository.findAll().stream()
                .filter(row -> row.getUserKey() != null && row.getUserKey().startsWith(TEST_USER_KEY_PREFIX))
                .forEach(row -> userPiiSyncQueueRepository.deleteByUserKey(row.getUserKey()));
    }

    @Test
    @DisplayName("queue status는 DB 기준 count와 oldest pending/failed, failed sample을 집계한다")
    void getStatusSummarizesQueueRows() {
        UserPiiSyncStatusResponse baseline = userPiiSyncStatusService.getStatus(20);

        LocalDateTime baseTime = LocalDateTime.of(1900, 1, 1, 0, 0, 0);
        String pendingKey = saveQueue(UserPiiSyncQueue.builder()
                .userKey(newUserKey())
                .status(UserPiiSyncQueueStatus.PENDING)
                .emailEnc("pending-email")
                .lastEnqueuedAt(baseTime)
                .build());
        String failedHighAttemptKey = saveQueue(UserPiiSyncQueue.builder()
                .userKey(newUserKey())
                .status(UserPiiSyncQueueStatus.FAILED)
                .attemptCount(999)
                .lastAttemptAt(baseTime.plusMinutes(5))
                .lastError("app_pii timeout")
                .build());
        saveQueue(UserPiiSyncQueue.builder()
                .userKey(newUserKey())
                .status(UserPiiSyncQueueStatus.FAILED)
                .attemptCount(1)
                .lastAttemptAt(baseTime.plusMinutes(10))
                .lastError("transient retry")
                .build());
        saveQueue(UserPiiSyncQueue.builder()
                .userKey(newUserKey())
                .status(UserPiiSyncQueueStatus.SYNCED)
                .attemptCount(1)
                .lastSyncedAt(LocalDateTime.of(2099, 1, 1, 0, 0, 0))
                .build());

        UserPiiSyncStatusResponse response = userPiiSyncStatusService.getStatus(1);

        assertThat(response.pendingCount()).isEqualTo(baseline.pendingCount() + 1);
        assertThat(response.failedCount()).isEqualTo(baseline.failedCount() + 2);
        assertThat(response.syncedCount()).isEqualTo(baseline.syncedCount() + 1);
        assertThat(response.oldestPendingUserKeyHash()).isEqualTo(RedisKeyHash.sha256Hex(pendingKey));
        assertThat(response.oldestPendingEnqueuedAt()).isEqualTo(baseTime);
        assertThat(response.oldestFailedUserKeyHash()).isEqualTo(RedisKeyHash.sha256Hex(failedHighAttemptKey));
        assertThat(response.oldestFailedAttemptAt()).isEqualTo(baseTime.plusMinutes(5));
        assertThat(response.latestSyncedAt()).isEqualTo(LocalDateTime.of(2099, 1, 1, 0, 0, 0));
        assertThat(response.failedSamples()).hasSize(1);
        assertThat(response.failedSamples().get(0).userKeyHash()).isEqualTo(RedisKeyHash.sha256Hex(failedHighAttemptKey));
        assertThat(response.failedSamples().get(0).attemptCount()).isEqualTo(999);
        assertThat(response.failedSamples().get(0).lastError()).isEqualTo("app_pii timeout");
    }

    private String saveQueue(UserPiiSyncQueue queue) {
        return userPiiSyncQueueRepository.save(queue).getUserKey();
    }

    private String newUserKey() {
        return TEST_USER_KEY_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }
}
