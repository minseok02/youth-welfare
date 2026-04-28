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

    List<ChatSession> findByUserIdOrderByLastMessageAtDesc(Long userId, Pageable pageable);

    Optional<ChatSession> findByIdAndUserId(Long sessionId, Long userId);

    @Modifying
    @Query("""
            DELETE FROM ChatSession cs
            WHERE cs.user.id = :userId
            """)
    void deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("""
            DELETE FROM ChatSession cs
            WHERE cs.userKey = :userKey
            """)
    void deleteByUserKey(@Param("userKey") String userKey);
}
