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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
class ChatRepositoryIntegrationTest {

    private static final String TEST_EMAIL_PREFIX = "it_chat_";

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
    @DisplayName("챗 세션과 메시지는 사용자별 최신순 조회와 세션별 메시지 조회가 가능하다")
    void chatSessionAndMessageRepositoriesWork() {
        User user = userRepository.save(User.builder()
                .email(TEST_EMAIL_PREFIX + UUID.randomUUID() + "@example.com")
                .passwordHash("pw")
                .name("Chat Integration")
                .birthDate(LocalDate.of(2000, 1, 1))
                .build());
        String userKey = userRepository.findUserKeyById(user.getId()).orElseThrow();

        ChatSession olderSession = chatSessionRepository.save(ChatSession.builder()
                .userKey(userKey)
                .title("이전 세션")
                .lastMessageAt(LocalDateTime.of(2026, 4, 25, 9, 0))
                .build());

        ChatSession latestSession = chatSessionRepository.save(ChatSession.builder()
                .userKey(userKey)
                .title("최신 세션")
                .lastMessageAt(LocalDateTime.of(2026, 4, 25, 10, 0))
                .build());

        ChatMessage firstMessage = chatMessageRepository.save(ChatMessage.builder()
                .session(latestSession)
                .role(ChatMessageRole.USER)
                .content("서울에서 받을 수 있는 주거 지원이 뭐야?")
                .referencedServiceIds("[]")
                .build());

        ChatMessage secondMessage = chatMessageRepository.save(ChatMessage.builder()
                .session(latestSession)
                .role(ChatMessageRole.ASSISTANT)
                .content("청년월세 한시 특별지원과 전세임대 정책을 먼저 보세요.")
                .referencedServiceIds("[1829,2451]")
                .build());

        jdbcTemplate.update(
                "UPDATE chat_messages SET created_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2026, 4, 25, 10, 0, 1)),
                Timestamp.valueOf(LocalDateTime.of(2026, 4, 25, 10, 0, 1)),
                firstMessage.getId()
        );
        jdbcTemplate.update(
                "UPDATE chat_messages SET created_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2026, 4, 25, 10, 0, 2)),
                Timestamp.valueOf(LocalDateTime.of(2026, 4, 25, 10, 0, 2)),
                secondMessage.getId()
        );

        List<ChatSession> sessions = chatSessionRepository.findByUserKeyOrderByLastMessageAtDesc(
                userKey, PageRequest.of(0, 10));
        List<ChatMessage> messagesAsc = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(latestSession.getId());
        List<ChatMessage> messagesDesc = chatMessageRepository.findBySessionIdOrderByCreatedAtDesc(
                latestSession.getId(), PageRequest.of(0, 1));

        assertThat(chatSessionRepository.findByIdAndUserKey(latestSession.getId(), userKey)).isPresent();
        assertThat(sessions).extracting(ChatSession::getTitle)
                .containsExactly("최신 세션", "이전 세션");
        assertThat(messagesAsc).extracting(ChatMessage::getRole)
                .containsExactly(ChatMessageRole.USER, ChatMessageRole.ASSISTANT);
        assertThat(messagesAsc.get(0).getReferencedServiceIds()).isEqualTo("[]");
        assertThat(messagesAsc.get(1).getReferencedServiceIds())
                .contains("1829")
                .contains("2451");
        assertThat(messagesDesc).singleElement()
                .extracting(ChatMessage::getRole)
                .isEqualTo(ChatMessageRole.ASSISTANT);
        assertThat(chatMessageRepository.countBySessionId(latestSession.getId())).isEqualTo(2);
        assertThat(chatMessageRepository.countBySessionId(olderSession.getId())).isZero();
    }

    @Test
    @DisplayName("사용자 삭제 시 챗 세션과 메시지는 DB cascade로 함께 삭제된다")
    void deletingUserRemovesChatSessionsAndMessages() {
        User user = userRepository.save(User.builder()
                .email(TEST_EMAIL_PREFIX + UUID.randomUUID() + "@example.com")
                .passwordHash("pw")
                .name("Cascade Chat")
                .build());
        String userKey = userRepository.findUserKeyById(user.getId()).orElseThrow();

        ChatSession session = chatSessionRepository.save(ChatSession.builder()
                .userKey(userKey)
                .title("삭제 세션")
                .build());

        chatMessageRepository.save(ChatMessage.builder()
                .session(session)
                .role(ChatMessageRole.USER)
                .content("삭제 테스트")
                .build());

        long sessionId = session.getId();

        chatSessionRepository.deleteAll(chatSessionRepository.findAllByUserKey(userKey));
        userRepository.delete(user);
        userRepository.flush();

        assertThat(chatSessionRepository.findById(sessionId)).isEmpty();
        assertThat(chatMessageRepository.countBySessionId(sessionId)).isZero();
    }
}
