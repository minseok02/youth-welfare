package com.example.welfare.chat.dto.response;

import com.example.welfare.chat.dto.ChatAnswerMode;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ChatAnswerResponse {

    private Long sessionId;
    private String answer;
    private boolean needsClarification;
    private ChatAnswerMode answerMode;
    private List<ChatBranchOptionResponse> branchSuggestions;
    private List<ChatReferenceResponse> references;
}
