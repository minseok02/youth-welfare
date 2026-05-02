package com.example.welfare.integration;

import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.user.dto.response.UserPiiSyncReplayResponse;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserPiiSyncQueue;
import com.example.welfare.user.entity.UserPiiSyncQueueStatus;
import com.example.welfare.user.repository.AuthUserRepository;
import com.example.welfare.user.repository.UserPiiReadWriteRepository;
import com.example.welfare.user.repository.UserPiiSyncQueueRepository;
import com.example.welfare.user.repository.UserProfileRepository;
import com.example.welfare.user.repository.UserRepository;
import com.example.welfare.user.service.UserCoreSyncService;
import com.example.welfare.user.service.UserPiiSyncReplayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
class UserPiiSyncReplayIntegrationTest {

    private static final String TEST_EMAIL_PREFIX = "it_pii_sync_replay_";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserPiiReadWriteRepository userPiiReadWriteRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private AuthUserRepository authUserRepository;

    @Autowired
    private UserPiiSyncQueueRepository userPiiSyncQueueRepository;

    @Autowired
    private UserCoreSyncService userCoreSyncService;

    @Autowired
    private UserPiiSyncReplayService userPiiSyncReplayService;

    @Autowired
    private AesEncryptUtil aesEncryptUtil;

    @BeforeEach
    void setup() {
        cleanup();
    }

    @AfterEach
    void cleanup() {
        userRepository.findAll().stream()
                .filter(user -> user.getEmail() != null && user.getEmail().startsWith(TEST_EMAIL_PREFIX))
                .forEach(user -> {
                    String userKey = userRepository.findUserKeyById(user.getId()).orElse(null);
                    if (userKey != null) {
                        authUserRepository.findByUserKey(userKey).ifPresent(authUserRepository::delete);
                        userProfileRepository.findByUserKey(userKey).ifPresent(userProfileRepository::delete);
                        userPiiReadWriteRepository.deleteByUserKey(userKey);
                        userPiiSyncQueueRepository.deleteByUserKey(userKey);
                    }
                    userRepository.delete(user);
                });
    }

    @Test
    @DisplayName("manual replay는 failed queue row를 다시 app_pii에 반영해 synced 상태로 되돌린다")
    void replayRestoresFailedQueueRow() {
        String email = TEST_EMAIL_PREFIX + UUID.randomUUID() + "@example.com";

        User user = userRepository.save(User.builder()
                .email(email)
                .passwordHash("hash")
                .name("Replay User")
                .birthDate(LocalDate.of(1998, 1, 10))
                .build());
        userCoreSyncService.syncFromUser(user);

        String userKey = userRepository.findUserKeyById(user.getId()).orElseThrow();
        UserPiiSyncQueue queue = userPiiSyncQueueRepository.findByUserKey(userKey).orElseThrow();
        userPiiReadWriteRepository.deleteByUserKey(userKey);
        queue.markFailed("forced integration failure");
        userPiiSyncQueueRepository.save(queue);

        UserPiiSyncReplayResponse response = userPiiSyncReplayService.replay(userKey, 10);

        assertThat(response.attemptedCount()).isEqualTo(1);
        assertThat(response.syncedCount()).isEqualTo(1);
        assertThat(response.failedCount()).isZero();
        assertThat(response.missingCount()).isZero();

        var reloaded = userPiiReadWriteRepository.findByUserKey(userKey).orElseThrow();
        assertThat(aesEncryptUtil.decrypt(reloaded.emailEnc())).isEqualTo(email);
        assertThat(aesEncryptUtil.decrypt(reloaded.nameEnc())).isEqualTo("Replay User");
        assertThat(aesEncryptUtil.decrypt(reloaded.birthDateEnc())).isEqualTo("1998-01-10");

        UserPiiSyncQueue reloadedQueue = userPiiSyncQueueRepository.findByUserKey(userKey).orElseThrow();
        assertThat(reloadedQueue.getStatus()).isEqualTo(UserPiiSyncQueueStatus.SYNCED);
        assertThat(reloadedQueue.getLastError()).isNull();
        assertThat(reloadedQueue.getLastSyncedAt()).isNotNull();
    }
}
