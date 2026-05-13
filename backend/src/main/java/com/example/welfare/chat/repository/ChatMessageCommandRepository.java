package com.example.welfare.chat.repository;

import com.example.welfare.chat.entity.ChatSession;

import java.time.LocalDateTime;

public interface ChatMessageCommandRepository {

    ChatSession requireSession(Long sessionId);

    void appendUserMessage(Long sessionId, String content, String sessionTitle);

    void appendAssistantMessage(Long sessionId,
                                String answer,
                                String referencedServiceIds,
                                String referencesJson,
                                LocalDateTime lastMessageAt);
}
