package com.example.welfare.policy.dto;

import com.example.welfare.policy.entity.WelfareService;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class PolicySummaryResponse {

    private Long id;
    private String title;
    private String description;
    private String unifiedCategory;
    private String status;
    private String hostOrg;
    private Integer minAge;
    private Integer maxAge;
    private String applyMethodName;
    private LocalDate applyStartDate;
    private LocalDate applyEndDate;
    private Boolean isOnlineApply;
    private boolean bookmarked;

    public static PolicySummaryResponse from(WelfareService ws, boolean bookmarked) {
        return PolicySummaryResponse.builder()
                .id(ws.getId())
                .title(ws.getTitle())
                .description(ws.getDescription())
                .unifiedCategory(ws.getUnifiedCategory())
                .status(ws.getStatus().name())
                .hostOrg(ws.getHostOrg())
                .minAge(ws.getMinAge())
                .maxAge(ws.getMaxAge())
                .applyMethodName(ws.getApplyMethodName())
                .applyStartDate(ws.getApplyStartDate())
                .applyEndDate(ws.getApplyEndDate())
                .isOnlineApply(ws.getIsOnlineApply())
                .bookmarked(bookmarked)
                .build();
    }
}
