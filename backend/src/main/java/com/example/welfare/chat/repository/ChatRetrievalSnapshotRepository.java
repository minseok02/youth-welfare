package com.example.welfare.chat.repository;

import com.example.welfare.chat.entity.ChatRetrievalSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRetrievalSnapshotRepository extends JpaRepository<ChatRetrievalSnapshot, Long> {
}
