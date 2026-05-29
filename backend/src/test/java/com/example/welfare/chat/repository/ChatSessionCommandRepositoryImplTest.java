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

}
