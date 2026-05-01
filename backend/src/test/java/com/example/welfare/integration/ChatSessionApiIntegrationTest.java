package com.example.welfare.integration;

import com.example.welfare.chat.entity.ChatSession;
import com.example.welfare.chat.repository.ChatMessageRepository;
import com.example.welfare.chat.repository.ChatSessionRepository;
import com.example.welfare.global.util.JwtUtil;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.LocalDateTime;
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
class ChatSessionApiIntegrationTest {

    private static final String TEST_EMAIL_PREFIX = "it_chat_api_";

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
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        userRepository.findAll().stream()
                .filter(user -> user.getEmail() != null && user.getEmail().startsWith(TEST_EMAIL_PREFIX))
                .forEach(user -> {
                    String userKey = userRepository.findUserKeyById(user.getId()).orElse(null);
                    if (userKey != null) {
                        chatSessionRepository.deleteAll(chatSessionRepository.findAllByUserKey(userKey));
                    }
                    userRepository.delete(user);
                });
    }

    @Test
    @DisplayName("세션 생성과 목록 조회는 로그인 사용자 기준으로 동작한다")
    void createAndListSessions() throws Exception {
        User user = createUser();
        String userKey = userRepository.findUserKeyById(user.getId()).orElseThrow();
        String accessToken = jwtUtil.generateAccessToken(userKey, user.getId());

        mockMvc.perform(post("/api/chat/sessions")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessionId").isNumber())
                .andExpect(jsonPath("$.data.title").doesNotExist());

        ChatSession older = chatSessionRepository.save(ChatSession.builder()
                .userKey(userKey)
                .title("이전 세션")
                .lastMessageAt(LocalDateTime.of(2099, 4, 25, 10, 0))
                .build());

        ChatSession latest = chatSessionRepository.save(ChatSession.builder()
                .userKey(userKey)
                .title("최신 세션")
                .lastMessageAt(LocalDateTime.of(2099, 4, 25, 11, 0))
                .build());

        jdbcTemplate.update("UPDATE chat_sessions SET last_message_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2099, 4, 25, 10, 0)), older.getId());
        jdbcTemplate.update("UPDATE chat_sessions SET last_message_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2099, 4, 25, 11, 0)), latest.getId());

        mockMvc.perform(get("/api/chat/sessions")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].title").value("최신 세션"))
                .andExpect(jsonPath("$.data[1].title").value("이전 세션"));
    }

    @Test
    @DisplayName("세션 삭제는 본인 세션만 삭제하고 다른 사용자의 세션은 404를 반환한다")
    void deleteSessionOnlyForOwner() throws Exception {
        User owner = createUser();
        User other = createUser();
        String ownerKey = userRepository.findUserKeyById(owner.getId()).orElseThrow();
        String otherKey = userRepository.findUserKeyById(other.getId()).orElseThrow();
        String ownerToken = jwtUtil.generateAccessToken(ownerKey, owner.getId());

        ChatSession ownerSession = chatSessionRepository.save(ChatSession.builder()
                .userKey(ownerKey)
                .title("내 세션")
                .build());
        ChatSession otherSession = chatSessionRepository.save(ChatSession.builder()
                .userKey(otherKey)
                .title("다른 사람 세션")
                .build());

        mockMvc.perform(delete("/api/chat/sessions/{sessionId}", ownerSession.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(chatSessionRepository.findById(ownerSession.getId())).isEmpty();

        mockMvc.perform(delete("/api/chat/sessions/{sessionId}", otherSession.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CH001"));

        assertThat(chatSessionRepository.findById(otherSession.getId())).isPresent();
        assertThat(chatMessageRepository.countBySessionId(ownerSession.getId())).isZero();
        assertThat(chatMessageRepository.countBySessionId(otherSession.getId())).isZero();
    }

    private User createUser() {
        return userRepository.save(User.builder()
                .email(TEST_EMAIL_PREFIX + UUID.randomUUID() + "@example.com")
                .passwordHash("pw")
                .name("Chat API")
                .build());
    }
}
