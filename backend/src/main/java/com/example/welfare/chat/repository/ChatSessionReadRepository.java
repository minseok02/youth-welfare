package com.example.welfare.chat.repository;

import com.example.welfare.chat.entity.ChatSession;

import java.util.List;
import java.util.Optional;

public interface ChatSessionReadRepository {

    List<ChatSession> findRecentSessions(String userKey, int limit);

    Optional<ChatSession> findOwnedSession(Long sessionId, String userKey);
}
