package com.example.welfare.chat.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatReferenceResponse {

    private Long serviceId;
    private String title;
    private String reason;
}
