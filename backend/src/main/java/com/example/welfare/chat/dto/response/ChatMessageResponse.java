package com.example.welfare.chat.dto.response;

import com.example.welfare.chat.entity.ChatMessage;
import com.example.welfare.chat.dto.ChatAnswerMode;
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
    private List<ChatReferenceResponse> references;
    private ChatAnswerMode answerMode;
    private boolean needsClarification;
    private List<ChatBranchOptionResponse> branchSuggestions;
    private LocalDateTime createdAt;

    public static ChatMessageResponse from(ChatMessage message,
                                           List<Long> referencedServiceIds,
                                           List<ChatReferenceResponse> references,
                                           ChatAnswerMode answerMode,
                                           boolean needsClarification,
                                           List<ChatBranchOptionResponse> branchSuggestions) {
        return ChatMessageResponse.builder()
                .messageId(message.getId())
                .role(message.getRole().name())
                .content(message.getContent())
                .referencedServiceIds(referencedServiceIds)
                .references(references)
                .answerMode(answerMode)
                .needsClarification(needsClarification)
                .branchSuggestions(branchSuggestions)
                .createdAt(message.getCreatedAt())
                .build();
    }
}
