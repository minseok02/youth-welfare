package com.example.welfare.chat.repository;

import com.example.welfare.chat.entity.ChatMessage;
import com.example.welfare.chat.entity.ChatSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ChatMessageReadRepositoryImpl implements ChatMessageReadRepository {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Override
    public Optional<ChatSession> findOwnedSession(Long sessionId, String userKey) {
        return chatSessionRepository.findByIdAndUserKey(sessionId, userKey);
    }

    @Override
    public List<ChatMessage> findMessages(Long sessionId) {
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Override
    public List<ChatMessage> findRecentMessages(Long sessionId, int limit) {
        return chatMessageRepository.findBySessionIdOrderByCreatedAtDesc(sessionId, PageRequest.of(0, limit));
    }
}
