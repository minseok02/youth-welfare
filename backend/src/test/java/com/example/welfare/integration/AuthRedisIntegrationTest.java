package com.example.welfare.integration;

import com.example.welfare.chat.entity.ChatMessage;
import com.example.welfare.chat.entity.ChatMessageRole;
import com.example.welfare.chat.entity.ChatSession;
import com.example.welfare.chat.repository.ChatMessageRepository;
import com.example.welfare.chat.repository.ChatSessionRepository;
import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.notification.gateway.EmailClient;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserPii;
import com.example.welfare.user.repository.UserPiiRepository;
import com.example.welfare.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
class AuthRedisIntegrationTest {

    private static final String TEST_EMAIL_PREFIX = "it_auth_";
    private static final Pattern RESET_TOKEN_PATTERN = Pattern.compile("token=([A-Za-z0-9\\-_%]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserPiiRepository userPiiRepository;

    @Autowired
    private AesEncryptUtil aesEncryptUtil;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @MockBean
    private EmailClient emailClient;

    @AfterEach
    void cleanup() {
        userRepository.findAll().stream()
                .filter(user -> user.getEmail() != null && user.getEmail().startsWith(TEST_EMAIL_PREFIX))
                .forEach(user -> {
                    redisTemplate.delete("refresh:" + user.getId());
                    userRepository.delete(user);
                });
        var keys = redisTemplate.keys("password-reset:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("이메일 중복확인은 가입 전후 상태를 반영한다")
    void checkEmailAvailabilityReflectsSignupState() throws Exception {
        String email = TEST_EMAIL_PREFIX + UUID.randomUUID() + "@example.com";

        mockMvc.perform(get("/api/auth/check-email")
                        .param("email", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.available").value(true));

        String signupBody = """
                {
                  "email": "%s",
                  "password": "password123",
                  "name": "Integration User",
                  "birthDate": "%s"
                }
                """.formatted(email, LocalDate.of(1998, 1, 10));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content(signupBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/auth/check-email")
                        .param("email", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    @DisplayName("로그인과 이메일 중복확인은 이메일 대소문자 차이를 무시한다")
    void loginAndEmailAvailabilityIgnoreEmailCase() throws Exception {
        String email = TEST_EMAIL_PREFIX + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123",
                                  "name": "Case User",
                                  "birthDate": "%s"
                                }
                                """.formatted(email, LocalDate.of(1998, 1, 10))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/auth/check-email")
                        .param("email", email.toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.available").value(false));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123"
                                }
                                """.formatted(email.toUpperCase())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("회원가입-로그인-재발급-로그아웃 흐름은 MySQL과 Redis에 상태를 반영한다")
    void signupLoginRefreshLogoutFlow() throws Exception {
        given(emailClient.send(anyString(), anyString(), anyString())).willReturn(true);

        String email = TEST_EMAIL_PREFIX + UUID.randomUUID() + "@example.com";
        String signupBody = """
                {
                  "email": "%s",
                  "password": "password123",
                  "name": "Integration User",
                  "birthDate": "%s",
                  "sido": "서울특별시",
                  "sgg": "강남구",
                  "incomeLevel": 5,
                  "employmentStatus": "EMPLOYED",
                  "householdType": "SINGLE"
                }
                """.formatted(email, LocalDate.of(1998, 1, 10));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content(signupBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        User user = userRepository.findByEmail(email).orElseThrow();
        ChatSession chatSession = chatSessionRepository.save(ChatSession.builder()
                .user(user)
                .title("로그아웃 전 세션")
                .build());
        chatMessageRepository.save(ChatMessage.builder()
                .session(chatSession)
                .role(ChatMessageRole.USER)
                .content("로그아웃해도 대화가 남는지 확인")
                .build());

        String loginBody = """
                {
                  "email": "%s",
                  "password": "password123"
                }
                """.formatted(email);

        var loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        String storedRefreshToken = redisTemplate.opsForValue().get("refresh:" + user.getId());
        assertNotNull(storedRefreshToken);

        var refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Refresh-Token", storedRefreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        String rotatedRefreshToken = redisTemplate.opsForValue().get("refresh:" + user.getId());
        assertNotNull(rotatedRefreshToken);

        mockMvc.perform(post("/api/auth/logout")
                        .header("X-Refresh-Token", rotatedRefreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertNull(redisTemplate.opsForValue().get("refresh:" + user.getId()));
        assertThat(chatSessionRepository.findById(chatSession.getId())).isEmpty();
        assertEquals(0L, chatMessageRepository.countBySessionId(chatSession.getId()));
        assertNotNull(loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        assertNotNull(refreshResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("비밀번호 재설정 요청과 확인은 Redis 토큰과 새 비밀번호 로그인까지 연결된다")
    void passwordResetRequestAndConfirmFlow() throws Exception {
        String email = TEST_EMAIL_PREFIX + UUID.randomUUID() + "@example.com";
        String piiEmail = "reset-target+" + UUID.randomUUID() + "@example.com";
        String signupBody = """
                {
                  "email": "%s",
                  "password": "password123",
                  "name": "Reset User",
                  "birthDate": "%s"
                }
                """.formatted(email, LocalDate.of(1998, 1, 10));
        given(emailClient.send(anyString(), anyString(), anyString())).willReturn(true);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content(signupBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        User user = userRepository.findByEmail(email).orElseThrow();
        String userKey = userRepository.findUserKeyById(user.getId()).orElseThrow();
        UserPii userPii = userPiiRepository.findByUserKey(userKey).orElseThrow();
        userPii.sync(
                aesEncryptUtil.encrypt(piiEmail),
                userPii.getNameEnc(),
                userPii.getBirthDateEnc(),
                userPii.getPhoneEnc()
        );
        userPiiRepository.save(userPii);

        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "%s"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        var bodyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        then(emailClient).should().send(eq(piiEmail), eq("[청년복지] 비밀번호 재설정 안내"), bodyCaptor.capture());
        String token = extractResetToken(bodyCaptor.getValue());

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType("application/json")
                        .content("""
                                {
                                  "token": "%s",
                                  "newPassword": "new-password123"
                                }
                                """.formatted(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123"
                                }
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("A004"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "new-password123"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    private String extractResetToken(String body) {
        Matcher matcher = RESET_TOKEN_PATTERN.matcher(body);
        assertThat(matcher.find()).isTrue();
        return java.net.URLDecoder.decode(matcher.group(1), java.nio.charset.StandardCharsets.UTF_8);
    }
}
