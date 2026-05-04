package com.example.welfare.chat.repository;

import com.example.welfare.chat.entity.ChatSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ChatSessionCommandRepositoryImplTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @InjectMocks
    private ChatSessionCommandRepositoryImpl chatSessionCommandRepository;

    @Test
    @DisplayName("chat session command repository는 세션 저장을 위임한다")
    void saveDelegates() {
        ChatSession session = ChatSession.builder().userKey("user-key-1").build();

        chatSessionCommandRepository.save(session);

        then(chatSessionRepository).should().save(session);
    }

    @Test
    @DisplayName("chat session command repository는 세션 삭제를 위임한다")
    void deleteDelegates() {
        ChatSession session = ChatSession.builder().id(10L).userKey("user-key-1").build();

        chatSessionCommandRepository.delete(session);

        then(chatSessionRepository).should().delete(session);
    }

    @Test
    @DisplayName("chat session command repository는 userKey 기준 전체 삭제를 위임한다")
    void deleteByUserKeyDelegates() {
        chatSessionCommandRepository.deleteByUserKey("user-key-1");

        then(chatSessionRepository).should().deleteByUserKey("user-key-1");
    }
}
