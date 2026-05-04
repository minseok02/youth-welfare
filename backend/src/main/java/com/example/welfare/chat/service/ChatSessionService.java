package com.example.welfare.chat.service;

import com.example.welfare.chat.dto.request.CreateChatSessionRequest;
import com.example.welfare.chat.dto.response.ChatSessionResponse;
import com.example.welfare.chat.entity.ChatSession;
import com.example.welfare.chat.repository.ChatSessionCommandRepository;
import com.example.welfare.chat.repository.ChatSessionReadRepository;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.user.service.ActiveUserReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private static final int RECENT_SESSION_LIMIT = 20;

    private final ChatSessionReadRepository chatSessionReadRepository;
    private final ChatSessionCommandRepository chatSessionCommandRepository;
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

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> getSessions(Long userId) {
        ActiveUserReadService.ActiveUserContext activeUserContext = activeUserReadService.getActiveUserContext(userId);
        return chatSessionReadRepository.findRecentSessions(activeUserContext.userKey(), RECENT_SESSION_LIMIT)
                .stream()
                .map(ChatSessionResponse::from)
                .toList();
    }

    @Transactional
    public void deleteSession(Long userId, Long sessionId) {
        ActiveUserReadService.ActiveUserContext activeUserContext = activeUserReadService.getActiveUserContext(userId);
        ChatSession session = chatSessionReadRepository.findOwnedSession(sessionId, activeUserContext.userKey())
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_SESSION_NOT_FOUND));
        chatSessionCommandRepository.delete(session);
    }

    private String normalizeTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return null;
        }
        return title.trim();
    }
}
