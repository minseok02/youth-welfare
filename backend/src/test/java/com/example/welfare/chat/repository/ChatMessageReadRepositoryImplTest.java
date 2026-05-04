package com.example.welfare.chat.repository;

import com.example.welfare.chat.entity.ChatMessage;
import com.example.welfare.chat.entity.ChatSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ChatMessageReadRepositoryImplTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;

    @InjectMocks
    private ChatMessageReadRepositoryImpl chatMessageReadRepository;

    @Test
    @DisplayName("chat message read repository는 소유 세션 조회를 위임한다")
    void findOwnedSessionDelegates() {
        ChatSession session = ChatSession.builder().id(10L).userKey("user-key-1").build();
        given(chatSessionRepository.findByIdAndUserKey(10L, "user-key-1")).willReturn(Optional.of(session));

        assertThat(chatMessageReadRepository.findOwnedSession(10L, "user-key-1")).contains(session);
    }

    @Test
    @DisplayName("chat message read repository는 메시지 목록 조회를 위임한다")
    void findMessagesDelegates() {
        ChatMessage message = ChatMessage.builder().id(1L).build();
        given(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(10L)).willReturn(List.of(message));

        assertThat(chatMessageReadRepository.findMessages(10L)).containsExactly(message);
    }

    @Test
    @DisplayName("chat message read repository는 최근 메시지 조회를 위임한다")
    void findRecentMessagesDelegates() {
        ChatMessage message = ChatMessage.builder().id(1L).build();
        given(chatMessageRepository.findBySessionIdOrderByCreatedAtDesc(any(Long.class), any(Pageable.class)))
                .willReturn(List.of(message));

        assertThat(chatMessageReadRepository.findRecentMessages(10L, 6)).containsExactly(message);
        then(chatMessageRepository).should().findBySessionIdOrderByCreatedAtDesc(any(Long.class), any(Pageable.class));
    }
}
