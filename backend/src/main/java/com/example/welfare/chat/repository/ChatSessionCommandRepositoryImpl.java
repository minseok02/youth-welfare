package com.example.welfare.chat.repository;

import com.example.welfare.chat.entity.ChatSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatSessionCommandRepositoryImpl implements ChatSessionCommandRepository {

    private final ChatSessionRepository chatSessionRepository;

    @Override
    public ChatSession save(ChatSession session) {
        return chatSessionRepository.save(session);
    }

    @Override
    public void delete(ChatSession session) {
        chatSessionRepository.delete(session);
    }

    @Override
    public void deleteByUserKey(String userKey) {
        chatSessionRepository.deleteByUserKey(userKey);
    }
}
