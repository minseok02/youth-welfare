package com.example.welfare.chat.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatBranchOptionResponse {

    private String branchKey;
    private String label;
    private String guideQuestion;
}
