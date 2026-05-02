package com.example.welfare.integration;

import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.global.util.JwtUtil;
import com.example.welfare.user.entity.AuthUser;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserPiiSyncQueue;
import com.example.welfare.user.entity.UserPiiSyncQueueStatus;
import com.example.welfare.user.entity.UserProfile;
import com.example.welfare.user.repository.AuthUserRepository;
import com.example.welfare.user.repository.UserPiiReadWriteRepository;
import com.example.welfare.user.repository.UserPiiSyncQueueRepository;
import com.example.welfare.user.repository.UserProfileRepository;
import com.example.welfare.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
class UserCoreDualWriteIntegrationTest {

    private static final String TEST_EMAIL_PREFIX = "it_core_";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthUserRepository authUserRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private UserPiiReadWriteRepository userPiiReadWriteRepository;

    @Autowired
    private UserPiiSyncQueueRepository userPiiSyncQueueRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AesEncryptUtil aesEncryptUtil;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
                    authUserRepository.findByUserKey(userKey).ifPresent(authUserRepository::delete);
                    userProfileRepository.findByUserKey(userKey).ifPresent(userProfileRepository::delete);
                    userPiiReadWriteRepository.deleteByUserKey(userKey);
                    userPiiSyncQueueRepository.deleteByUserKey(userKey);
                },
                null
        );
    }

    @Test
    @DisplayName("회원가입은 legacy users와 core split tables에 동시에 반영된다")
    void signupDualWritesCoreTables() throws Exception {
        String email = TEST_EMAIL_PREFIX + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123",
                                  "name": "Dual Write User",
                                  "birthDate": "1998-01-10",
                                  "sido": "서울특별시",
                                  "sgg": "강남구",
                                  "incomeLevel": 5,
                                  "employmentStatus": "EMPLOYED",
                                  "householdType": "SINGLE"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        User user = userRepository.findByEmail(email).orElseThrow();
        String userKey = userRepository.findUserKeyById(user.getId()).orElseThrow();

        AuthUser authUser = authUserRepository.findByUserKey(userKey).orElseThrow();
        UserProfile userProfile = userProfileRepository.findByUserKey(userKey).orElseThrow();
        var userPii = userPiiReadWriteRepository.findByUserKey(userKey).orElseThrow();
        UserPiiSyncQueue syncQueue = userPiiSyncQueueRepository.findByUserKey(userKey).orElseThrow();

        assertThat(authUser.getEmailLookupHash()).isEqualTo(sha256Hex(email.toLowerCase()));
        assertThat(authUser.getPasswordHash()).isEqualTo(user.getPasswordHash());
        assertThat(userProfile.getSido()).isEqualTo("서울특별시");
        assertThat(userProfile.getSgg()).isEqualTo("강남구");
        assertThat(userProfile.getIncomeLevel()).isEqualTo((byte) 5);
        assertThat(userProfile.getAgeBand()).isEqualTo("25_29");
        assertThat(userProfile.isHasName()).isTrue();
        assertThat(userProfile.isHasBirthDate()).isTrue();
        assertThat(userProfile.isHasPhone()).isFalse();
        assertThat(aesEncryptUtil.decrypt(userPii.emailEnc())).isEqualTo(email);
        assertThat(aesEncryptUtil.decrypt(userPii.nameEnc())).isEqualTo("Dual Write User");
        assertThat(aesEncryptUtil.decrypt(userPii.birthDateEnc())).isEqualTo("1998-01-10");
        assertThat(syncQueue.getStatus()).isEqualTo(UserPiiSyncQueueStatus.SYNCED);
        assertThat(syncQueue.getAttemptCount()).isEqualTo(1);
        assertThat(syncQueue.getLastSyncedAt()).isNotNull();
    }

    @Test
    @DisplayName("프로필 수정은 core split tables에도 동일하게 반영된다")
    void updateProfileDualWritesCoreTables() throws Exception {
        String email = TEST_EMAIL_PREFIX + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123",
                                  "name": "Before Update",
                                  "birthDate": "1998-01-10"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk());

        User user = userRepository.findByEmail(email).orElseThrow();
        String userKey = userRepository.findUserKeyById(user.getId()).orElseThrow();
        String accessToken = jwtUtil.generateAccessToken(userKey, user.getId());

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "After Update",
                                  "birthDate": "1996-05-20",
                                  "phone": "01012345678",
                                  "sido": "부산광역시",
                                  "sgg": "해운대구",
                                  "regionCode": "26000",
                                  "incomeLevel": 7,
                                  "householdType": "MULTI_PERSON",
                                  "employmentStatus": "STUDENT",
                                  "notificationYn": true,
                                  "notificationPeriod": "weekly",
                                  "notificationMinScore": 0.7,
                                  "displayCount": 12
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        UserProfile userProfile = userProfileRepository.findByUserKey(userKey).orElseThrow();
        var userPii = userPiiReadWriteRepository.findByUserKey(userKey).orElseThrow();
        UserPiiSyncQueue syncQueue = userPiiSyncQueueRepository.findByUserKey(userKey).orElseThrow();

        assertThat(userProfile.getSido()).isEqualTo("부산광역시");
        assertThat(userProfile.getSgg()).isEqualTo("해운대구");
        assertThat(userProfile.getRegionCode()).isEqualTo("26000");
        assertThat(userProfile.getIncomeLevel()).isEqualTo((byte) 7);
        assertThat(userProfile.getHouseholdType()).isEqualTo("MULTI_PERSON");
        assertThat(userProfile.getEmploymentStatus()).isEqualTo("STUDENT");
        assertThat(userProfile.isNotificationYn()).isTrue();
        assertThat(userProfile.getNotificationPeriod()).isEqualTo(User.NotificationPeriod.WEEKLY);
        assertThat(userProfile.getNotificationMinScore()).isEqualTo(0.7);
        assertThat(userProfile.getDisplayCount()).isEqualTo(12);
        assertThat(userProfile.getAgeBand()).isEqualTo("25_29");
        assertThat(userProfile.isHasPhone()).isTrue();
        assertThat(aesEncryptUtil.decrypt(userPii.nameEnc())).isEqualTo("After Update");
        assertThat(aesEncryptUtil.decrypt(userPii.birthDateEnc())).isEqualTo("1996-05-20");
        assertThat(aesEncryptUtil.decrypt(userPii.phoneEnc())).isEqualTo("01012345678");
        assertThat(syncQueue.getStatus()).isEqualTo(UserPiiSyncQueueStatus.SYNCED);
        assertThat(syncQueue.getAttemptCount()).isGreaterThanOrEqualTo(2);
        assertThat(syncQueue.getPhoneEnc()).isEqualTo(userPii.phoneEnc());
    }

    @Test
    @DisplayName("프로필 조회는 user_profiles와 user_pii 값을 우선해서 반환한다")
    void getProfileReadsFromSplitTables() throws Exception {
        String email = TEST_EMAIL_PREFIX + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123",
                                  "name": "Legacy Name",
                                  "birthDate": "1999-01-10",
                                  "sido": "서울특별시",
                                  "sgg": "강남구",
                                  "incomeLevel": 6,
                                  "employmentStatus": "EMPLOYED",
                                  "householdType": "SINGLE"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk());

        User user = userRepository.findByEmail(email).orElseThrow();
        String userKey = userRepository.findUserKeyById(user.getId()).orElseThrow();
        String accessToken = jwtUtil.generateAccessToken(userKey, user.getId());

        jdbcTemplate.update("""
                update user_profiles
                set sido = ?, sgg = ?, region_code = ?, income_level = ?, household_type = ?, employment_status = ?,
                    notification_yn = ?, notification_period = ?, notification_min_score = ?, display_count = ?
                where user_key = ?
                """,
                "제주특별자치도", "제주시", "50000", 2, "ONE_PERSON", "JOB_SEEKER",
                true, "DAILY", 0.9, 7, userKey);
        userPiiReadWriteRepository.upsertUserPii(
                userKey,
                aesEncryptUtil.encrypt("split-read@example.com"),
                aesEncryptUtil.encrypt("Split Name"),
                aesEncryptUtil.encrypt("2001-03-15"),
                aesEncryptUtil.encrypt("01099998888")
        );

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("split-read@example.com"))
                .andExpect(jsonPath("$.data.name").value("Split Name"))
                .andExpect(jsonPath("$.data.birthDate").value("2001-03-15"))
                .andExpect(jsonPath("$.data.phone").value("01099998888"))
                .andExpect(jsonPath("$.data.sido").value("제주특별자치도"))
                .andExpect(jsonPath("$.data.sgg").value("제주시"))
                .andExpect(jsonPath("$.data.regionCode").value("50000"))
                .andExpect(jsonPath("$.data.incomeLevel").value(2))
                .andExpect(jsonPath("$.data.householdType").value("ONE_PERSON"))
                .andExpect(jsonPath("$.data.employmentStatus").value("JOB_SEEKER"))
                .andExpect(jsonPath("$.data.notificationYn").value(true))
                .andExpect(jsonPath("$.data.notificationPeriod").value("DAILY"))
                .andExpect(jsonPath("$.data.notificationMinScore").value(0.9))
                .andExpect(jsonPath("$.data.displayCount").value(7));
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
