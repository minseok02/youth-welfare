package com.example.welfare.chat.service;

import com.example.welfare.chat.repository.ChatSessionCleanupCommandRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ChatSessionCleanupServiceTest {

    @Mock
    private ChatSessionCleanupCommandRepository chatSessionCleanupCommandRepository;

    @InjectMocks
    private ChatSessionCleanupService chatSessionCleanupService;

    @Test
    @DisplayName("빈 userKey면 세션 정리를 건너뛴다")
    void deleteAllByUserKeySkipsBlank() {
        chatSessionCleanupService.deleteAllByUserKey("  ");

        then(chatSessionCleanupCommandRepository).should(never()).deleteByUserKey("  ");
    }

    @Test
    @DisplayName("userKey가 있으면 세션 전체 삭제를 위임한다")
    void deleteAllByUserKeyDelegates() {
        chatSessionCleanupService.deleteAllByUserKey("user-key-1");

        then(chatSessionCleanupCommandRepository).should().deleteByUserKey("user-key-1");
    }
}
