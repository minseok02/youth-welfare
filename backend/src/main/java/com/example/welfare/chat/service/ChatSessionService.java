package com.example.welfare.chat.service;

import com.example.welfare.chat.dto.request.CreateChatSessionRequest;
import com.example.welfare.chat.dto.response.ChatSessionResponse;
import com.example.welfare.chat.entity.ChatSession;
import com.example.welfare.chat.repository.ChatSessionRepository;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private static final int RECENT_SESSION_LIMIT = 20;

    private final ChatSessionRepository chatSessionRepository;
    private final UserRepository userRepository;

    @Transactional
    public ChatSessionResponse createSession(Long userId, CreateChatSessionRequest request) {
        User user = findActiveUser(userId);
        ChatSession session = chatSessionRepository.save(ChatSession.builder()
                .user(user)
                .title(normalizeTitle(request != null ? request.getTitle() : null))
                .build());
        return ChatSessionResponse.from(session);
    }

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> getSessions(Long userId) {
        findActiveUser(userId);
        return chatSessionRepository.findByUserIdOrderByLastMessageAtDesc(
                        userId, PageRequest.of(0, RECENT_SESSION_LIMIT))
                .stream()
                .map(ChatSessionResponse::from)
                .toList();
    }

    @Transactional
    public void deleteSession(Long userId, Long sessionId) {
        findActiveUser(userId);
        ChatSession session = chatSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_SESSION_NOT_FOUND));
        chatSessionRepository.delete(session);
    }

    private User findActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!user.isActive()) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }
        return user;
    }

    private String normalizeTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return null;
        }
        return title.trim();
    }
}
