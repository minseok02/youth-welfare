package com.example.welfare.chat.service;

import com.example.welfare.chat.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatSessionCleanupService {

    private final ChatSessionRepository chatSessionRepository;

    @Transactional
    public void deleteAllByUserKey(String userKey) {
        if (userKey == null || userKey.isBlank()) {
            return;
        }
        chatSessionRepository.deleteByUserKey(userKey);
    }
}
