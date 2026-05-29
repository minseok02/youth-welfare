package com.example.welfare.integration;

import com.example.welfare.chat.entity.ChatMessage;
import com.example.welfare.chat.entity.ChatMessageRole;
import com.example.welfare.chat.entity.ChatSession;
import com.example.welfare.chat.repository.ChatMessageRepository;
import com.example.welfare.chat.repository.ChatSessionCleanupCommandRepository;
import com.example.welfare.chat.repository.ChatSessionRepository;
import com.example.welfare.global.util.JwtUtil;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
class UserWithdrawChatCleanupIntegrationTest {

    private static final String TEST_EMAIL_PREFIX = "it_user_withdraw_";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ChatSessionCleanupCommandRepository chatSessionCleanupCommandRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
                        chatSessionCleanupCommandRepository.deleteByUserKey(userKey);
                    }
                    userRepository.delete(user);
                });
    }

    @Test
    @DisplayName("회원탈퇴는 사용자 비활성화와 챗 세션 삭제를 함께 처리한다")
    void withdrawDeletesChatSessionsAndMasksUser() throws Exception {
        User user = userRepository.save(User.builder()
                .email(TEST_EMAIL_PREFIX + UUID.randomUUID() + "@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .name("Withdraw Integration")
                .build());
        String userKey = userRepository.findUserKeyById(user.getId()).orElseThrow();
        String accessToken = jwtUtil.generateAccessToken(userKey, user.getId());

        ChatSession session = chatSessionRepository.save(ChatSession.builder()
                .userKey(userKey)
                .title("탈퇴 전 세션")
                .build());
        chatMessageRepository.save(ChatMessage.builder()
                .session(session)
                .role(ChatMessageRole.USER)
                .content("탈퇴하면 대화가 지워지는지 확인")
                .build());

        mockMvc.perform(delete("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken)
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
        assertThat(withdrawnUser.getEmail()).isEqualTo("withdrawn_" + user.getId());
        assertThat(chatSessionRepository.findById(session.getId())).isEmpty();
        assertEquals(0L, chatMessageRepository.countBySessionId(session.getId()));
    }
}
