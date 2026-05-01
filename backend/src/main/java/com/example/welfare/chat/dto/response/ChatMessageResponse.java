package com.example.welfare.chat.dto.response;

import com.example.welfare.chat.entity.ChatMessage;
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

    public static ChatMessageResponse from(ChatMessage message, List<Long> referencedServiceIds) {
        return ChatMessageResponse.builder()
                .messageId(message.getId())
                .role(message.getRole().name())
                .content(message.getContent())
                .referencedServiceIds(referencedServiceIds)
                .createdAt(message.getCreatedAt())
                .build();
    }
}
