package com.example.welfare.chat.service;

import com.example.welfare.chat.entity.ChatMessage;
import com.example.welfare.chat.entity.ChatMessageRole;
import com.example.welfare.chat.entity.ChatSession;
import com.example.welfare.chat.repository.ChatMessageRepository;
import com.example.welfare.chat.repository.ChatSessionRepository;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private UserRepository userRepository;

    private ChatMessageService chatMessageService;

    @BeforeEach
    void setUp() {
        chatMessageService = new ChatMessageService(
                chatSessionRepository,
                chatMessageRepository,
                userRepository,
                new ObjectMapper());
    }

    @Test
    @DisplayName("메시지 조회는 JSON 참조 정책 ID를 리스트로 변환한다")
    void getMessagesParsesReferencedServiceIds() {
        User user = createUser(1L);
        ChatSession session = ChatSession.builder()
                .id(10L)
                .user(user)
                .title("주거 상담")
                .build();
        ChatMessage userMessage = ChatMessage.builder()
                .id(100L)
                .session(session)
                .role(ChatMessageRole.USER)
                .content("월세 지원 있어?")
                .build();
        ChatMessage assistantMessage = ChatMessage.builder()
                .id(101L)
                .session(session)
                .role(ChatMessageRole.ASSISTANT)
                .content("청년월세지원이 있습니다.")
                .referencedServiceIds("[1829,2451]")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(chatSessionRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(10L))
                .thenReturn(List.of(userMessage, assistantMessage));

        var responses = chatMessageService.getMessages(1L, 10L);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getReferencedServiceIds()).isEmpty();
        assertThat(responses.get(1).getReferencedServiceIds()).containsExactly(1829L, 2451L);
    }

    @Test
    @DisplayName("다른 사용자의 세션 메시지 조회 요청은 챗 세션 없음 오류를 반환한다")
    void getMessagesThrowsWhenSessionNotOwned() {
        User user = createUser(1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(chatSessionRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatMessageService.getMessages(1L, 99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_SESSION_NOT_FOUND);
    }

    private User createUser(Long userId) {
        return User.builder()
                .id(userId)
                .email("chat@example.com")
                .passwordHash("hash")
                .build();
    }
}
