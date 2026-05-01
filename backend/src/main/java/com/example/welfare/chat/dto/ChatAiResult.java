package com.example.welfare.chat.dto;

import com.example.welfare.chat.dto.response.ChatReferenceResponse;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ChatAiResult {

    private String answer;
    private boolean needsClarification;
    private List<ChatReferenceResponse> references;
}
