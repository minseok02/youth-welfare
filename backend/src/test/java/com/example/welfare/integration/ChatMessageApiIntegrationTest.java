package com.example.welfare.integration;

import com.example.welfare.chat.entity.ChatMessage;
import com.example.welfare.chat.entity.ChatMessageRole;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
class ChatMessageApiIntegrationTest {

    private static final String TEST_EMAIL_PREFIX = "it_chat_message_api_";

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
                .forEach(userRepository::delete);
    }

    @Test
    @DisplayName("메시지 목록 조회는 생성 시각 오름차순과 참조 정책 ID를 반환한다")
    void getMessagesReturnsAscendingMessages() throws Exception {
        User user = createUser();
        String accessToken = jwtUtil.generateAccessToken(user.getId());

        ChatSession session = chatSessionRepository.save(ChatSession.builder()
                .user(user)
                .title("주거 상담")
                .build());

        ChatMessage userMessage = chatMessageRepository.save(ChatMessage.builder()
                .session(session)
                .role(ChatMessageRole.USER)
                .content("서울 월세 지원 있어?")
                .build());
        ChatMessage assistantMessage = chatMessageRepository.save(ChatMessage.builder()
                .session(session)
                .role(ChatMessageRole.ASSISTANT)
                .content("청년월세지원과 전세임대를 먼저 보세요.")
                .referencedServiceIds("[1829,2451]")
                .build());

        jdbcTemplate.update("UPDATE chat_messages SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2099, 4, 26, 9, 0, 0)), userMessage.getId());
        jdbcTemplate.update("UPDATE chat_messages SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2099, 4, 26, 9, 0, 5)), assistantMessage.getId());

        mockMvc.perform(get("/api/chat/sessions/{sessionId}/messages", session.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].role").value("USER"))
                .andExpect(jsonPath("$.data[0].content").value("서울 월세 지원 있어?"))
                .andExpect(jsonPath("$.data[0].referencedServiceIds").isArray())
                .andExpect(jsonPath("$.data[0].referencedServiceIds").isEmpty())
                .andExpect(jsonPath("$.data[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data[1].referencedServiceIds[0]").value(1829))
                .andExpect(jsonPath("$.data[1].referencedServiceIds[1]").value(2451))
                .andExpect(jsonPath("$.data[1].createdAt").value("2099-04-26T09:00:05"));
    }

    @Test
    @DisplayName("다른 사용자의 세션 메시지 조회는 404를 반환한다")
    void getMessagesReturnsNotFoundForOtherUsersSession() throws Exception {
        User owner = createUser();
        User other = createUser();
        String ownerToken = jwtUtil.generateAccessToken(owner.getId());

        ChatSession otherSession = chatSessionRepository.save(ChatSession.builder()
                .user(other)
                .title("다른 사람 세션")
                .build());

        mockMvc.perform(get("/api/chat/sessions/{sessionId}/messages", otherSession.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CH001"));
    }

    private User createUser() {
        return userRepository.save(User.builder()
                .email(TEST_EMAIL_PREFIX + UUID.randomUUID() + "@example.com")
                .passwordHash("pw")
                .name("Chat Message API")
                .build());
    }
}
