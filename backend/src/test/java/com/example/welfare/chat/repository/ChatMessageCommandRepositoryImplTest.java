package com.example.welfare.chat.repository;

import com.example.welfare.chat.entity.ChatSession;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ChatMessageCommandRepositoryImplTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;

    @InjectMocks
    private ChatMessageCommandRepositoryImpl chatMessageCommandRepository;

    @Test
    @DisplayName("chat message command repository는 세션이 없으면 예외를 던진다")
    void requireSessionThrowsWhenMissing() {
        given(chatSessionRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatMessageCommandRepository.requireSession(10L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_SESSION_NOT_FOUND);
    }

    @Test
    @DisplayName("chat message command repository는 사용자 메시지 저장과 제목 갱신을 처리한다")
    void appendUserMessageUpdatesTitleAndSaves() {
        ChatSession session = ChatSession.builder().id(10L).userKey("user-key-1").build();
        given(chatSessionRepository.findById(10L)).willReturn(Optional.of(session));

        chatMessageCommandRepository.appendUserMessage(10L, "질문", "새 제목");

        assertThat(session.getTitle()).isEqualTo("새 제목");
        then(chatMessageRepository).should().save(any());
    }

    @Test
    @DisplayName("chat message command repository는 assistant 메시지 저장과 lastMessageAt 갱신을 처리한다")
    void appendAssistantMessageUpdatesLastMessageAt() {
        ChatSession session = ChatSession.builder().id(10L).userKey("user-key-1").build();
        LocalDateTime now = LocalDateTime.now();
        given(chatSessionRepository.findById(10L)).willReturn(Optional.of(session));

        chatMessageCommandRepository.appendAssistantMessage(10L, "답변", "[1,2]", "[{\"serviceId\":1}]", now);

        assertThat(session.getLastMessageAt()).isEqualTo(now);
        then(chatMessageRepository).should().save(any());
    }
}
