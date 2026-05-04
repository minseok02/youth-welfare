package com.example.welfare.chat.repository;

import com.example.welfare.chat.entity.ChatMessage;
import com.example.welfare.chat.entity.ChatMessageRole;
import com.example.welfare.chat.entity.ChatSession;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class ChatMessageCommandRepositoryImpl implements ChatMessageCommandRepository {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Override
    public ChatSession requireSession(Long sessionId) {
        return chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_SESSION_NOT_FOUND));
    }

    @Override
    public void appendUserMessage(Long sessionId, String content, String sessionTitle) {
        ChatSession session = requireSession(sessionId);
        if (StringUtils.hasText(sessionTitle) && !StringUtils.hasText(session.getTitle())) {
            session.updateTitle(sessionTitle);
        }

        chatMessageRepository.save(ChatMessage.builder()
                .session(session)
                .role(ChatMessageRole.USER)
                .content(content)
                .build());
    }

    @Override
    public void appendAssistantMessage(Long sessionId, String answer, String referencedServiceIds, LocalDateTime lastMessageAt) {
        ChatSession session = requireSession(sessionId);
        chatMessageRepository.save(ChatMessage.builder()
                .session(session)
                .role(ChatMessageRole.ASSISTANT)
                .content(answer)
                .referencedServiceIds(referencedServiceIds)
                .build());
        session.updateLastMessageAt(lastMessageAt);
    }
}
