package com.example.welfare.chat.repository;

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
class ChatSessionReadRepositoryImplTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @InjectMocks
    private ChatSessionReadRepositoryImpl chatSessionReadRepository;

    @Test
    @DisplayName("chat session read repository는 최근 세션 조회를 위임한다")
    void findRecentSessionsDelegates() {
        ChatSession session = ChatSession.builder().id(10L).userKey("user-key-1").build();
        given(chatSessionRepository.findByUserKeyOrderByLastMessageAtDesc(any(), any(Pageable.class)))
                .willReturn(List.of(session));

        assertThat(chatSessionReadRepository.findRecentSessions("user-key-1", 20)).containsExactly(session);
        then(chatSessionRepository).should().findByUserKeyOrderByLastMessageAtDesc(any(), any(Pageable.class));
    }

    @Test
    @DisplayName("chat session read repository는 소유 세션 조회를 위임한다")
    void findOwnedSessionDelegates() {
        ChatSession session = ChatSession.builder().id(10L).userKey("user-key-1").build();
        given(chatSessionRepository.findByIdAndUserKey(10L, "user-key-1")).willReturn(Optional.of(session));

        assertThat(chatSessionReadRepository.findOwnedSession(10L, "user-key-1")).contains(session);
    }
}
