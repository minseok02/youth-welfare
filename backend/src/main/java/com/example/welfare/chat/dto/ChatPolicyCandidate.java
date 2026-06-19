package com.example.welfare.chat.dto;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.entity.WelfareServiceDetail;
import com.example.welfare.chat.dto.response.ChatActionLinkResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class ChatPolicyCandidate {

    private Long serviceId;
    private String title;
    private String description;
    private String supportContent;
    private String hostOrg;
    private String applyMethodName;
    private String unifiedCategory;
    private LocalDate applyStartDate;
    private LocalDate applyEndDate;
    private String targetDetail;
    private String supportDetail;
    private String applyMethodDetail;
    private String selectionCriteria;
    private String contactList;
    private String formFiles;
    private String detailUrl;
    private String homepageUrl;
    private List<ChatActionLinkResponse> actionLinks;

    public static ChatPolicyCandidate from(WelfareService service) {
        return ChatPolicyCandidate.builder()
                .serviceId(service.getId())
                .title(service.getTitle())
                .description(service.getDescription())
                .supportContent(service.getSupportContent())
                .hostOrg(service.getHostOrg())
                .applyMethodName(service.getApplyMethodName())
                .unifiedCategory(service.getUnifiedCategory())
                .applyStartDate(service.getApplyStartDate())
                .applyEndDate(service.getApplyEndDate())
                .detailUrl(service.getDetailUrl())
                .actionLinks(List.of())
                .build();
    }

    public static ChatPolicyCandidate from(WelfareService service,
                                           WelfareServiceDetail detail,
                                           List<ChatActionLinkResponse> actionLinks) {
        return ChatPolicyCandidate.builder()
                .serviceId(service.getId())
                .title(service.getTitle())
                .description(service.getDescription())
                .supportContent(service.getSupportContent())
                .hostOrg(service.getHostOrg())
                .applyMethodName(service.getApplyMethodName())
                .unifiedCategory(service.getUnifiedCategory())
                .applyStartDate(service.getApplyStartDate())
                .applyEndDate(service.getApplyEndDate())
                .targetDetail(detail != null ? detail.getTargetDetail() : null)
                .supportDetail(detail != null ? detail.getSupportDetail() : null)
                .applyMethodDetail(detail != null ? detail.getApplyMethodDetail() : null)
                .selectionCriteria(detail != null ? detail.getSelectionCriteria() : null)
                .contactList(detail != null ? detail.getContactList() : null)
                .formFiles(detail != null ? detail.getFormFiles() : null)
                .detailUrl(service.getDetailUrl())
                .homepageUrl(detail != null ? detail.getHomepageUrl() : null)
                .actionLinks(actionLinks != null ? List.copyOf(actionLinks) : List.of())
                .build();
    }
}
