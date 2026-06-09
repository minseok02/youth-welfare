package com.example.welfare.integration;

import com.example.welfare.global.util.JwtUtil;
import com.example.welfare.global.util.RedisKeyHash;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
class UserWithdrawAccessTokenBaselineIntegrationTest {

    private static final String TEST_EMAIL_PREFIX = "it_user_withdraw_token_";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setup() {
        cleanup();
    }

    @AfterEach
    void cleanup() {
        userRepository.findAll().stream()
                .filter(user -> {
                    String email = user.getEmail();
                    return email != null && (email.startsWith(TEST_EMAIL_PREFIX) || email.startsWith("withdrawn_"));
                })
                .forEach(user -> {
                    String userKey = userRepository.findUserKeyById(user.getId()).orElse(null);
                    if (userKey != null) {
                        redisTemplate.delete(refreshKey(userKey));
                        redisTemplate.delete("refresh:" + userKey);
                    }
                    redisTemplate.delete("refresh:" + user.getId());
                    userRepository.delete(user);
                });
    }

    @Test
    @DisplayName("회원탈퇴에 사용한 access token은 즉시 revoke되고 refresh token도 함께 정리된다")
    void withdrawRevokesPresentedAccessTokenAndClearsRefreshToken() throws Exception {
        User user = userRepository.save(User.builder()
                .email(TEST_EMAIL_PREFIX + UUID.randomUUID() + "@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .name("Withdraw Token Baseline")
                .build());

        String userKey = userRepository.findUserKeyById(user.getId()).orElseThrow();
        String oldAccessToken = jwtUtil.generateAccessToken(userKey, user.getId());
        String refreshToken = jwtUtil.generateRefreshToken(userKey, user.getId());
        redisTemplate.opsForValue().set(refreshKey(userKey), refreshToken);

        mockMvc.perform(delete("/api/users/me")
                        .header("Authorization", "Bearer " + oldAccessToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        User withdrawnUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(withdrawnUser.isActive()).isFalse();
        assertThat(redisTemplate.opsForValue().get(refreshKey(userKey))).isNull();
        assertThat(redisTemplate.opsForValue().get("refresh:" + userKey)).isNull();

        mockMvc.perform(get("/api/users/me/bookmarks")
                        .header("Authorization", "Bearer " + oldAccessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("A006"));

        mockMvc.perform(post("/api/recommendations/refresh")
                        .header("Authorization", "Bearer " + oldAccessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("A006"));

        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Refresh-Token", refreshToken))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("U003"));
    }

    private String refreshKey(String userKey) {
        return "refresh:v2:" + RedisKeyHash.sha256Hex(userKey);
    }
}
