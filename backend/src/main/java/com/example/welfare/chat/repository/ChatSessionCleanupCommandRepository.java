package com.example.welfare.chat.repository;

public interface ChatSessionCleanupCommandRepository {

    void deleteByUserKey(String userKey);

    void deleteByIdAndUserKey(Long sessionId, String userKey);
}
