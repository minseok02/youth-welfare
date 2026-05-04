package com.example.welfare.chat.repository;

import com.example.welfare.chat.entity.ChatSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ChatSessionReadRepositoryImpl implements ChatSessionReadRepository {

    private final ChatSessionRepository chatSessionRepository;

    @Override
    public List<ChatSession> findRecentSessions(String userKey, int limit) {
        return chatSessionRepository.findByUserKeyOrderByLastMessageAtDesc(userKey, PageRequest.of(0, limit));
    }

    @Override
    public Optional<ChatSession> findOwnedSession(Long sessionId, String userKey) {
        return chatSessionRepository.findByIdAndUserKey(sessionId, userKey);
    }
}
