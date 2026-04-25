package com.example.welfare.chat.dto.response;

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
}
