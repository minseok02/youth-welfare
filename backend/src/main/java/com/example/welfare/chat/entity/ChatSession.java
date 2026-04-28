package com.example.welfare.chat.entity;

import com.example.welfare.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_sessions", indexes = {
        @Index(name = "idx_cs_user_last_message", columnList = "user_id, last_message_at DESC"),
        @Index(name = "idx_cs_user_created", columnList = "user_id, created_at DESC")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class ChatSession extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_key", length = 32, columnDefinition = "CHAR(32)")
    private String userKey;

    @Column(length = 100)
    private String title;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime lastMessageAt = LocalDateTime.now();

    public void updateTitle(String title) {
        this.title = title;
    }

    public void updateLastMessageAt(LocalDateTime lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }
}
