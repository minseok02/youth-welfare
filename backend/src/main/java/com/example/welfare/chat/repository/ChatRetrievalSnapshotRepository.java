package com.example.welfare.chat.repository;

import com.example.welfare.chat.entity.ChatRetrievalSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatRetrievalSnapshotRepository extends JpaRepository<ChatRetrievalSnapshot, Long> {

    List<ChatRetrievalSnapshot> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
}
