package com.example.welfare.chat.service;

import com.example.welfare.chat.entity.ChatMessage;
import com.example.welfare.chat.entity.ChatMessageRole;
import com.example.welfare.chat.entity.ChatSession;
import com.example.welfare.chat.repository.ChatMessageRepository;
import com.example.welfare.chat.repository.ChatSessionRepository;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ChatMessageCommandService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional
    public void appendUserMessage(Long sessionId, String content, String sessionTitle) {
        ChatSession session = findSession(sessionId);
        if (StringUtils.hasText(sessionTitle) && !StringUtils.hasText(session.getTitle())) {
            session.updateTitle(sessionTitle);
        }

        chatMessageRepository.save(ChatMessage.builder()
                .session(session)
                .role(ChatMessageRole.USER)
                .content(content)
                .build());
    }

    @Transactional
    public void appendAssistantMessage(Long sessionId, String answer, String referencedServiceIds, LocalDateTime lastMessageAt) {
        ChatSession session = findSession(sessionId);
        chatMessageRepository.save(ChatMessage.builder()
                .session(session)
                .role(ChatMessageRole.ASSISTANT)
                .content(answer)
                .referencedServiceIds(referencedServiceIds)
                .build());
        session.updateLastMessageAt(lastMessageAt);
    }

    private ChatSession findSession(Long sessionId) {
        return chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_SESSION_NOT_FOUND));
    }
}
