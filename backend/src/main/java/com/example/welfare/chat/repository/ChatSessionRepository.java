package com.example.welfare.chat.repository;

import com.example.welfare.chat.entity.ChatSession;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    List<ChatSession> findAllByUserKey(String userKey);

    List<ChatSession> findByUserKeyOrderByLastMessageAtDesc(String userKey, Pageable pageable);

    Optional<ChatSession> findByIdAndUserKey(Long sessionId, String userKey);
}
