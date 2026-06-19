package com.example.welfare.chat.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.AccessLevel;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ChatReferenceResponse {

    private Long serviceId;
    private String title;
    private String reason;
    private String evidence;
    private List<ChatActionLinkResponse> actionLinks;
}
