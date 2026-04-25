package com.example.welfare.chat.service;

import com.example.welfare.chat.dto.response.ChatMessageResponse;
import com.example.welfare.chat.entity.ChatMessage;
import com.example.welfare.chat.repository.ChatMessageRepository;
import com.example.welfare.chat.repository.ChatSessionRepository;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(Long userId, Long sessionId) {
        findActiveUser(userId);
        chatSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_SESSION_NOT_FOUND));

        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(this::toResponse)
                .toList();
    }

    private ChatMessageResponse toResponse(ChatMessage message) {
        return ChatMessageResponse.from(message, parseReferencedServiceIds(message.getReferencedServiceIds()));
    }

    private List<Long> parseReferencedServiceIds(String rawReferencedServiceIds) {
        if (!StringUtils.hasText(rawReferencedServiceIds)) {
            return List.of();
        }

        try {
            List<Long> referencedServiceIds = objectMapper.readValue(
                    rawReferencedServiceIds, new TypeReference<List<Long>>() {
                    });
            return referencedServiceIds != null ? referencedServiceIds : List.of();
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private User findActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!user.isActive()) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }
        return user;
    }
}
