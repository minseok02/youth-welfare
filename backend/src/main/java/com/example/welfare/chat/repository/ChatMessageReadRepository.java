package com.example.welfare.chat.repository;

import com.example.welfare.chat.entity.ChatMessage;
import com.example.welfare.chat.entity.ChatSession;

import java.util.List;
import java.util.Optional;

public interface ChatMessageReadRepository {

    Optional<ChatSession> findOwnedSession(Long sessionId, String userKey);

    List<ChatMessage> findMessages(Long sessionId);

    List<ChatMessage> findRecentMessages(Long sessionId, int limit);
}
