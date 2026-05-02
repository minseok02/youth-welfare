package com.example.welfare.integration;

import com.example.welfare.collect.service.CollectSource;
import com.example.welfare.collect.service.CollectService;
import com.example.welfare.global.util.JwtUtil;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.AuthUserRepository;
import com.example.welfare.user.repository.UserPiiReadWriteRepository;
import com.example.welfare.user.repository.UserPiiSyncQueueRepository;
import com.example.welfare.user.repository.UserProfileRepository;
import com.example.welfare.user.repository.UserRepository;
import com.example.welfare.user.service.AuthService;
import com.example.welfare.user.service.UserCoreSyncService;
import com.example.welfare.user.util.EmailLookupKeyGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
class AdminSecurityIntegrationTest {

    private static final String TEST_EMAIL_PREFIX = "it_admin_";
    private static final String ADMIN_EMAIL = "admin@youth-welfare.dev";
    private static final String TEST_PASSWORD = "password123";

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private UserCoreSyncService userCoreSyncService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @MockBean
    private CollectService collectService;

    @BeforeEach
    void setup() {
        cleanup();
    }

    @AfterEach
    void cleanup() {
        ReflectionTestUtils.setField(authService, "adminEmailsProperty", ADMIN_EMAIL);
        ReflectionTestUtils.invokeMethod(authService, "initAdminEmails");
        userRepository.findAll().stream()
                .filter(user -> ADMIN_EMAIL.equals(user.getEmail())
                        || (user.getEmail() != null && user.getEmail().startsWith(TEST_EMAIL_PREFIX)))
                .forEach(user -> {
                    String userKey = userRepository.findUserKeyById(user.getId()).orElse(null);
                    if (userKey != null) {
                        redisTemplate.delete("refresh:" + userKey);
                        redisTemplate.delete("access-cutoff:" + userKey);
                        userPiiSyncQueueRepository.deleteByUserKey(userKey);
                    }
                    redisTemplate.delete("refresh:" + user.getId());
                    userRepository.delete(user);
                });
    }

    @Test
    @DisplayName("관리자 예약 이메일은 공개 회원가입으로 생성할 수 없다")
    void reservedAdminEmailCannotSignup() throws Exception {
        String signupBody = """
                {
                  "email": "%s",
                  "password": "%s",
                  "name": "Admin Reserved",
                  "birthDate": "%s"
                }
                """.formatted(ADMIN_EMAIL, TEST_PASSWORD, LocalDate.of(1998, 1, 10));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content(signupBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("A007"));
    }

    @Test
    @DisplayName("일반 사용자는 관리자 API를 호출할 수 없고 관리자는 재발급 후에도 호출할 수 있다")
    void adminApiRequiresAdminRoleAcrossRefresh() throws Exception {
        doNothing().when(collectService).collect(CollectSource.YOUTH);

        User normalUser = createUser(TEST_EMAIL_PREFIX + UUID.randomUUID() + "@example.com");
        User adminUser = createUser(ADMIN_EMAIL);

        String userAccessToken = loginAndExtractAccessToken(normalUser.getEmail(), TEST_PASSWORD);
        mockMvc.perform(post("/api/admin/collect/youth")
                        .header("Authorization", "Bearer " + userAccessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C003"));

        String adminAccessToken = loginAndExtractAccessToken(adminUser.getEmail(), TEST_PASSWORD);
        assertTrue(jwtUtil.getRoles(adminAccessToken).contains("ROLE_ADMIN"));

        mockMvc.perform(post("/api/admin/collect/youth")
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        String adminUserKey = userRepository.findUserKeyById(adminUser.getId()).orElseThrow();
        String refreshToken = redisTemplate.opsForValue().get("refresh:" + adminUserKey);
        assertFalse(refreshToken == null || refreshToken.isBlank());

        String refreshedAccessToken = refreshAndExtractAccessToken(refreshToken);
        assertTrue(jwtUtil.getRoles(refreshedAccessToken).containsAll(List.of("ROLE_USER", "ROLE_ADMIN")));

        mockMvc.perform(post("/api/admin/collect/youth")
                        .header("Authorization", "Bearer " + refreshedAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        then(collectService).should(org.mockito.Mockito.times(2)).collect(CollectSource.YOUTH);
    }

    @Test
    @DisplayName("로그아웃에 사용한 access token은 즉시 관리자 API에서 차단된다")
    void logoutRevokesPresentedAccessTokenImmediately() throws Exception {
        doNothing().when(collectService).collect(CollectSource.YOUTH);
        User adminUser = createUser(ADMIN_EMAIL);

        String adminAccessToken = loginAndExtractAccessToken(adminUser.getEmail(), TEST_PASSWORD);
        String adminUserKey = userRepository.findUserKeyById(adminUser.getId()).orElseThrow();
        String refreshToken = redisTemplate.opsForValue().get("refresh:" + adminUserKey);
        assertFalse(refreshToken == null || refreshToken.isBlank());

        mockMvc.perform(post("/api/admin/collect/youth")
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + adminAccessToken)
                        .header("X-Refresh-Token", refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/admin/collect/youth")
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("A006"));
    }

    @Test
    @DisplayName("allowlist 제거 후 old admin access token은 유지되지만 refresh로 발급된 새 token부터 ROLE_ADMIN이 빠진다")
    void allowlistRemovalKeepsOldAccessTokenButRefreshDropsAdminRole() throws Exception {
        doNothing().when(collectService).collect(CollectSource.YOUTH);
        User adminUser = createUser(ADMIN_EMAIL);

        String adminAccessToken = loginAndExtractAccessToken(adminUser.getEmail(), TEST_PASSWORD);
        assertTrue(jwtUtil.getRoles(adminAccessToken).contains("ROLE_ADMIN"));

        String adminUserKey = userRepository.findUserKeyById(adminUser.getId()).orElseThrow();
        String refreshToken = redisTemplate.opsForValue().get("refresh:" + adminUserKey);
        assertFalse(refreshToken == null || refreshToken.isBlank());

        mockMvc.perform(post("/api/admin/collect/youth")
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ReflectionTestUtils.setField(authService, "adminEmailsProperty", "");
        ReflectionTestUtils.invokeMethod(authService, "initAdminEmails");

        mockMvc.perform(post("/api/admin/collect/youth")
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        String refreshedAccessToken = refreshAndExtractAccessToken(refreshToken);
        assertFalse(jwtUtil.getRoles(refreshedAccessToken).contains("ROLE_ADMIN"));

        mockMvc.perform(post("/api/admin/collect/youth")
                        .header("Authorization", "Bearer " + refreshedAccessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C003"));
    }

    @Test
    @DisplayName("forced logout API 이후 old admin access token은 차단되고 재로그인 access token은 통과한다")
    void forcedLogoutApiBlocksOldAdminAccessTokenButAllowsRelogin() throws Exception {
        doNothing().when(collectService).collect(CollectSource.YOUTH);
        User adminUser = createUser(ADMIN_EMAIL);

        String oldAdminAccessToken = loginAndExtractAccessToken(adminUser.getEmail(), TEST_PASSWORD);
        String adminUserKey = userRepository.findUserKeyById(adminUser.getId()).orElseThrow();
        String oldRefreshToken = redisTemplate.opsForValue().get("refresh:" + adminUserKey);
        assertFalse(oldRefreshToken == null || oldRefreshToken.isBlank());

        mockMvc.perform(post("/api/admin/users/forced-logout")
                        .contentType("application/json")
                        .content("""
                                {
                                  "userKey": "%s"
                                }
                                """.formatted(adminUserKey))
                        .header("Authorization", "Bearer " + oldAdminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userKey").value(adminUserKey))
                .andExpect(jsonPath("$.data.accepted").value(true));

        mockMvc.perform(post("/api/admin/collect/youth")
                        .header("Authorization", "Bearer " + oldAdminAccessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("A006"));

        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Refresh-Token", oldRefreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("A003"));

        Thread.sleep(10L);
        String reloginAccessToken = loginAndExtractAccessToken(adminUser.getEmail(), TEST_PASSWORD);

        mockMvc.perform(post("/api/admin/collect/youth")
                        .header("Authorization", "Bearer " + reloginAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("iatm 없는 legacy admin access token은 forced logout 보호 경계에서 401 A006으로 차단된다")
    void legacyAdminAccessTokenWithoutIatmFailsWithA006AfterForcedLogout() throws Exception {
        doNothing().when(collectService).collect(CollectSource.YOUTH);
        User adminUser = createUser(ADMIN_EMAIL);
        String adminUserKey = userRepository.findUserKeyById(adminUser.getId()).orElseThrow();

        String legacyAdminAccessToken = createLegacyAdminAccessToken(adminUserKey, adminUser.getId());

        mockMvc.perform(post("/api/admin/users/forced-logout")
                        .contentType("application/json")
                        .content("""
                                {
                                  "userKey": "%s"
                                }
                                """.formatted(adminUserKey))
                        .header("Authorization", "Bearer " + legacyAdminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userKey").value(adminUserKey))
                .andExpect(jsonPath("$.data.accepted").value(true));

        mockMvc.perform(post("/api/admin/collect/youth")
                        .header("Authorization", "Bearer " + legacyAdminAccessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("A006"));
    }

    private User createUser(String email) {
        userRepository.findByEmail(email).ifPresent(existing -> {
            deleteUserState(existing.getId(), userRepository.findUserKeyById(existing.getId()).orElse(null));
        });
        authUserRepository.findByEmailLookupHash(EmailLookupKeyGenerator.hash(email))
                .ifPresent(authUser -> deleteUserState(null, authUser.getUserKey()));
        User user = userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .name("Admin Security Test")
                .birthDate(LocalDate.of(1998, 1, 10))
                .build());
        userCoreSyncService.syncFromUser(user);
        return user;
    }

    private void deleteUserState(Long userId, String userKey) {
        transactionTemplate.executeWithoutResult(status -> {
            if (userKey != null) {
                redisTemplate.delete("refresh:" + userKey);
                userPiiSyncQueueRepository.deleteByUserKey(userKey);
                userPiiReadWriteRepository.deleteByUserKey(userKey);
                userProfileRepository.deleteByUserKey(userKey);
                authUserRepository.deleteByUserKey(userKey);
            }
            if (userId != null) {
                redisTemplate.delete("refresh:" + userId);
                userRepository.findById(userId).ifPresent(userRepository::delete);
            }
        });
    }

    private String loginAndExtractAccessToken(String email, String password) throws Exception {
        String loginBody = """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);

        String content = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(content);
        return root.path("data").path("accessToken").asText();
    }

    private String refreshAndExtractAccessToken(String refreshToken) throws Exception {
        String content = mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Refresh-Token", refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(content);
        return root.path("data").path("accessToken").asText();
    }

    private String createLegacyAdminAccessToken(String userKey, Long userId) {
        String secret = (String) ReflectionTestUtils.getField(jwtUtil, "secret");
        long accessExpiration = (long) ReflectionTestUtils.getField(jwtUtil, "accessExpiration");
        Date now = new Date();
        return Jwts.builder()
                .subject(userKey)
                .claim("uid", userId)
                .claim("roles", List.of("ROLE_USER", "ROLE_ADMIN"))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessExpiration))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();
    }
}
