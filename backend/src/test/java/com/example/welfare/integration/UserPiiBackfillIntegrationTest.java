package com.example.welfare.integration;

import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.AuthUserRepository;
import com.example.welfare.user.repository.UserPiiReadWriteRepository;
import com.example.welfare.user.repository.UserPiiSyncQueueRepository;
import com.example.welfare.user.repository.UserProfileRepository;
import com.example.welfare.user.repository.UserRepository;
import com.example.welfare.user.service.UserCoreSyncService;
import com.example.welfare.user.service.UserPiiBackfillService;
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
class UserPiiBackfillIntegrationTest {

    private static final String TEST_EMAIL_PREFIX = "it_pii_backfill_";

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
    private UserPiiBackfillService userPiiBackfillService;

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
    @DisplayName("앱 레벨 PII 백필은 비어 있는 email/name/birth_date 암호문을 채운다")
    void backfillMissingEncryptedFields() {
        String email = TEST_EMAIL_PREFIX + UUID.randomUUID() + "@example.com";

        User user = userRepository.save(User.builder()
                .email(email)
                .passwordHash("hash")
                .name("Backfill User")
                .birthDate(LocalDate.of(1998, 1, 10))
                .build());
        userCoreSyncService.syncFromUser(user);

        String userKey = userRepository.findUserKeyById(user.getId()).orElseThrow();
        var userPii = userPiiReadWriteRepository.findByUserKey(userKey).orElseThrow();
        userPiiReadWriteRepository.upsertUserPii(userKey, null, null, null, userPii.phoneEnc());

        userPiiBackfillService.backfillMissingEncryptedFields();

        var reloaded = userPiiReadWriteRepository.findByUserKey(userKey).orElseThrow();
        assertThat(aesEncryptUtil.decrypt(reloaded.emailEnc())).isEqualTo(email);
        assertThat(aesEncryptUtil.decrypt(reloaded.nameEnc())).isEqualTo("Backfill User");
        assertThat(aesEncryptUtil.decrypt(reloaded.birthDateEnc())).isEqualTo("1998-01-10");
    }
}
