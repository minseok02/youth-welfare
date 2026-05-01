package com.example.welfare.chat.dto.response;

import com.example.welfare.chat.entity.ChatSession;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatSessionResponse {

    private Long sessionId;
    private String title;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;

    public static ChatSessionResponse from(ChatSession session) {
        return ChatSessionResponse.builder()
                .sessionId(session.getId())
                .title(session.getTitle())
                .lastMessageAt(session.getLastMessageAt())
                .createdAt(session.getCreatedAt())
                .build();
    }
}
