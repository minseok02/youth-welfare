package com.example.welfare.chat.service;

import com.example.welfare.chat.entity.ChatSession;
import com.example.welfare.chat.repository.ChatSessionReadRepository;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.service.ActiveUserReadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSessionQueryServiceTest {

    @Mock
    private ChatSessionReadRepository chatSessionReadRepository;
    @Mock
    private ActiveUserReadService activeUserReadService;

    private ChatSessionQueryService chatSessionQueryService;

    @BeforeEach
    void setUp() {
        chatSessionQueryService = new ChatSessionQueryService(chatSessionReadRepository, activeUserReadService);
    }

    @Test
    @DisplayName("세션 목록 조회는 active user 기준 최근 세션을 응답으로 변환한다")
    void getSessionsReturnsRecentSessions() {
        User user = User.builder().id(1L).userKey("user-key-1").email("chat@example.com").passwordHash("hash").build();
        ChatSession session = ChatSession.builder().id(10L).userKey("user-key-1").title("주거 상담").build();

        when(activeUserReadService.getActiveUserContext(1L))
                .thenReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        when(chatSessionReadRepository.findRecentSessions("user-key-1", 20)).thenReturn(List.of(session));

        var responses = chatSessionQueryService.getSessions(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getSessionId()).isEqualTo(10L);
        assertThat(responses.get(0).getTitle()).isEqualTo("주거 상담");
    }
}
