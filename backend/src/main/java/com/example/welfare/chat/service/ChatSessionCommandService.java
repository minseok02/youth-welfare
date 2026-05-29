package com.example.welfare.chat.service;

import com.example.welfare.chat.dto.request.CreateChatSessionRequest;
import com.example.welfare.chat.dto.response.ChatSessionResponse;
import com.example.welfare.chat.entity.ChatSession;
import com.example.welfare.chat.repository.ChatSessionCleanupCommandRepository;
import com.example.welfare.chat.repository.ChatSessionCommandRepository;
import com.example.welfare.chat.repository.ChatSessionReadRepository;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.user.service.ActiveUserReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ChatSessionCommandService {

    private final ChatSessionReadRepository chatSessionReadRepository;
    private final ChatSessionCommandRepository chatSessionCommandRepository;
    private final ChatSessionCleanupCommandRepository chatSessionCleanupCommandRepository;
    private final ActiveUserReadService activeUserReadService;

    @Transactional
    public ChatSessionResponse createSession(Long userId, CreateChatSessionRequest request) {
        ActiveUserReadService.ActiveUserContext activeUserContext = activeUserReadService.getActiveUserContext(userId);
        ChatSession session = chatSessionCommandRepository.save(ChatSession.builder()
                .userKey(activeUserContext.userKey())
                .title(normalizeTitle(request != null ? request.getTitle() : null))
                .build());
        return ChatSessionResponse.from(session);
    }

    @Transactional
    public void deleteSession(Long userId, Long sessionId) {
        ActiveUserReadService.ActiveUserContext activeUserContext = activeUserReadService.getActiveUserContext(userId);
        chatSessionReadRepository.findOwnedSession(sessionId, activeUserContext.userKey())
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_SESSION_NOT_FOUND));
        chatSessionCleanupCommandRepository.deleteByIdAndUserKey(sessionId, activeUserContext.userKey());
    }

    private String normalizeTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return null;
        }
        return title.trim();
    }
}
