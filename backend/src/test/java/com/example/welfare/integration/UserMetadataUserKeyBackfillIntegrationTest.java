package com.example.welfare.integration;

import com.example.welfare.user.dto.response.UserMetadataUserKeyBackfillResponse;
import com.example.welfare.user.entity.PriorityOption;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserAttribute;
import com.example.welfare.user.entity.UserPriority;
import com.example.welfare.user.repository.AuthUserRepository;
import com.example.welfare.user.repository.PriorityOptionRepository;
import com.example.welfare.user.repository.UserAttributeRepository;
import com.example.welfare.user.repository.UserPiiReadWriteRepository;
import com.example.welfare.user.repository.UserPiiSyncQueueRepository;
import com.example.welfare.user.repository.UserPriorityRepository;
import com.example.welfare.user.repository.UserProfileRepository;
import com.example.welfare.user.repository.UserRepository;
import com.example.welfare.user.service.UserCoreSyncService;
import com.example.welfare.user.service.UserMetadataUserKeyBackfillService;
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
class UserMetadataUserKeyBackfillIntegrationTest {

    private static final String TEST_EMAIL_PREFIX = "it_metadata_user_key_";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAttributeRepository userAttributeRepository;

    @Autowired
    private UserPriorityRepository userPriorityRepository;

    @Autowired
    private PriorityOptionRepository priorityOptionRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private UserPiiReadWriteRepository userPiiReadWriteRepository;

    @Autowired
    private AuthUserRepository authUserRepository;

    @Autowired
    private UserPiiSyncQueueRepository userPiiSyncQueueRepository;

    @Autowired
    private UserCoreSyncService userCoreSyncService;

    @Autowired
    private UserMetadataUserKeyBackfillService userMetadataUserKeyBackfillService;

    @BeforeEach
    void setup() {
        cleanup();
    }

    @AfterEach
    void cleanup() {
        IntegrationCleanupSupport.cleanupUsers(
                userRepository,
                user -> user.getEmail() != null && user.getEmail().startsWith(TEST_EMAIL_PREFIX),
                userKey -> {
                    userAttributeRepository.deleteAll(userAttributeRepository.findByUserKey(userKey));
                    userPriorityRepository.deleteAll(userPriorityRepository.findByUserKeyOrderByPriorityRank(userKey));
                    authUserRepository.findByUserKey(userKey).ifPresent(authUserRepository::delete);
                    userProfileRepository.findByUserKey(userKey).ifPresent(userProfileRepository::delete);
                    userPiiReadWriteRepository.deleteByUserKey(userKey);
                    userPiiSyncQueueRepository.deleteByUserKey(userKey);
                },
                null
        );
    }

    @Test
    @DisplayName("metadata user_key 백필은 user_attributes와 user_priorities의 누락 user_key를 채운다")
    void backfillMissingUserKeys() {
        String email = TEST_EMAIL_PREFIX + UUID.randomUUID() + "@example.com";

        User user = userRepository.save(User.builder()
                .email(email)
                .passwordHash("hash")
                .name("Metadata UserKey")
                .birthDate(LocalDate.of(1998, 1, 10))
                .build());
        userCoreSyncService.syncFromUser(user);

        String userKey = userRepository.findUserKeyById(user.getId()).orElseThrow();
        PriorityOption housing = priorityOptionRepository.findByCode("HOUSING").orElseThrow();

        UserAttribute attribute = userAttributeRepository.save(UserAttribute.builder()
                .userId(user.getId())
                .userKey(null)
                .attrType(UserAttribute.AttrType.INTEREST_FIELD.name())
                .attrValue("주거")
                .build());
        UserPriority priority = userPriorityRepository.save(UserPriority.builder()
                .userId(user.getId())
                .userKey(null)
                .priorityOption(housing)
                .priorityRank(1)
                .weight(2.0)
                .build());

        UserMetadataUserKeyBackfillResponse response = userMetadataUserKeyBackfillService.backfillMissingUserKeys();

        assertThat(response.processedCount()).isGreaterThanOrEqualTo(2);
        assertThat(response.attributeUpdatedCount()).isGreaterThanOrEqualTo(1);
        assertThat(response.priorityUpdatedCount()).isGreaterThanOrEqualTo(1);

        UserAttribute reloadedAttribute = userAttributeRepository.findById(attribute.getId()).orElseThrow();
        UserPriority reloadedPriority = userPriorityRepository.findById(priority.getId()).orElseThrow();
        assertThat(reloadedAttribute.getUserKey()).isEqualTo(userKey);
        assertThat(reloadedPriority.getUserKey()).isEqualTo(userKey);
        assertThat(userAttributeRepository.findReadModelsByUserKey(userKey))
                .extracting(readModel -> readModel.getAttrValue())
                .contains("주거");
        assertThat(userPriorityRepository.findReadModelsByUserKey(userKey))
                .extracting(readModel -> readModel.getCode())
                .contains("HOUSING");
    }
}
