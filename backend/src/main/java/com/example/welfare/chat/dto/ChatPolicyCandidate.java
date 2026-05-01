package com.example.welfare.chat.dto;

import com.example.welfare.policy.entity.WelfareService;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatPolicyCandidate {

    private Long serviceId;
    private String title;
    private String description;
    private String supportContent;
    private String hostOrg;

    public static ChatPolicyCandidate from(WelfareService service) {
        return ChatPolicyCandidate.builder()
                .serviceId(service.getId())
                .title(service.getTitle())
                .description(service.getDescription())
                .supportContent(service.getSupportContent())
                .hostOrg(service.getHostOrg())
                .build();
    }
}
