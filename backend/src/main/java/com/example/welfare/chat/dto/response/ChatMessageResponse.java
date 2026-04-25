package com.example.welfare.chat.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ChatMessageResponse {

    private Long messageId;
    private String role;
    private String content;
    private List<Long> referencedServiceIds;
    private LocalDateTime createdAt;
}
