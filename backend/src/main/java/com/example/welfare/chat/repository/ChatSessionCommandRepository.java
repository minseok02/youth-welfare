package com.example.welfare.chat.repository;

import com.example.welfare.chat.entity.ChatSession;

public interface ChatSessionCommandRepository {

    ChatSession save(ChatSession session);

    void delete(ChatSession session);

    void deleteByUserKey(String userKey);
}
