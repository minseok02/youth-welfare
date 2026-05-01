package com.example.welfare.chat.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ChatAnswerResponse {

    private Long sessionId;
    private String answer;
    private boolean needsClarification;
    private List<ChatReferenceResponse> references;
}
