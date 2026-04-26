package com.example.welfare.integration;

import com.example.welfare.chat.entity.ChatMessage;
import com.example.welfare.chat.entity.ChatMessageRole;
import com.example.welfare.chat.entity.ChatSession;
import com.example.welfare.chat.repository.ChatMessageRepository;
import com.example.welfare.chat.repository.ChatSessionRepository;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
class AuthRedisIntegrationTest {

    private static final String TEST_EMAIL_PREFIX = "it_auth_";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @AfterEach
    void cleanup() {
        userRepository.findAll().stream()
                .filter(user -> user.getEmail() != null && user.getEmail().startsWith(TEST_EMAIL_PREFIX))
                .forEach(user -> {
                    redisTemplate.delete("refresh:" + user.getId());
                    userRepository.delete(user);
                });
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
    @DisplayName("회원가입-로그인-재발급-로그아웃 흐름은 MySQL과 Redis에 상태를 반영한다")
    void signupLoginRefreshLogoutFlow() throws Exception {
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
}
