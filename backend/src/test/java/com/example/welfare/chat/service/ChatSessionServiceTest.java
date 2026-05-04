package com.example.welfare.chat.service;

import com.example.welfare.chat.dto.request.CreateChatSessionRequest;
import com.example.welfare.chat.entity.ChatSession;
import com.example.welfare.chat.repository.ChatSessionCommandRepository;
import com.example.welfare.chat.repository.ChatSessionReadRepository;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.service.UserReadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceTest {

    @Mock
    private ChatSessionReadRepository chatSessionReadRepository;
    @Mock
    private ChatSessionCommandRepository chatSessionCommandRepository;

    @Mock
    private UserReadService userReadService;

    private ChatSessionService chatSessionService;

    @BeforeEach
    void setUp() {
        chatSessionService = new ChatSessionService(chatSessionReadRepository, chatSessionCommandRepository, userReadService);
    }

    @Test
    @DisplayName("세션 생성은 공백 제목을 null로 정규화한다")
    void createSessionNormalizesBlankTitleToNull() {
        User user = User.builder()
                .id(1L)
                .userKey("user-key-1")
                .email("chat@example.com")
                .passwordHash("hash")
                .build();
        CreateChatSessionRequest request = new CreateChatSessionRequest();
        ReflectionTestUtils.setField(request, "title", "   ");

        when(userReadService.getActiveUserContext(1L))
                .thenReturn(new UserReadService.ActiveUserContext(user, "user-key-1"));
        when(chatSessionCommandRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            ReflectionTestUtils.setField(session, "id", 10L);
            return session;
        });

        var response = chatSessionService.createSession(1L, request);

        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(chatSessionCommandRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isNull();
        assertThat(response.getSessionId()).isEqualTo(10L);
        assertThat(response.getTitle()).isNull();
    }

    @Test
    @DisplayName("다른 사용자의 세션 삭제 요청은 챗 세션 없음 오류를 반환한다")
    void deleteSessionThrowsWhenSessionNotOwned() {
        User user = User.builder()
                .id(1L)
                .userKey("user-key-1")
                .email("chat@example.com")
                .passwordHash("hash")
                .build();

        when(userReadService.getActiveUserContext(1L))
                .thenReturn(new UserReadService.ActiveUserContext(user, "user-key-1"));
        when(chatSessionReadRepository.findOwnedSession(99L, "user-key-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatSessionService.deleteSession(1L, 99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_SESSION_NOT_FOUND);
    }
}
