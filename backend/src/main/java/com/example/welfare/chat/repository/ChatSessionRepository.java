package com.example.welfare.chat.repository;

import com.example.welfare.chat.entity.ChatSession;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    List<ChatSession> findByUserKeyOrderByLastMessageAtDesc(String userKey, Pageable pageable);

    Optional<ChatSession> findByIdAndUserKey(Long sessionId, String userKey);

    @Modifying
    @Query("""
            DELETE FROM ChatSession cs
            WHERE cs.userKey = :userKey
            """)
    void deleteByUserKey(@Param("userKey") String userKey);
}
