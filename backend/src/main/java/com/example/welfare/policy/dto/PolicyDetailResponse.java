package com.example.welfare.policy.dto;

import com.example.welfare.policy.entity.ServiceRegion;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.entity.WelfareServiceDetail;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class PolicyDetailResponse {

    private Long id;
    private String title;
    private String description;
    private String unifiedCategory;
    private String status;
    private String sourceType;
    private String hostOrg;
    private String operatingOrg;
    private Integer minAge;
    private Integer maxAge;
    private Integer minIncome;
    private Integer maxIncome;
    private String supportContent;
    private String applyMethodName;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate applyStartDate;
    private LocalDate applyEndDate;
    private String lifeStage;
    private String detailUrl;
    private String supportCycle;
    private String provisionType;
    private Boolean isOnlineApply;
    private boolean bookmarked;

    // 상세 정보
    private String targetDetail;
    private String supportDetail;
    private String applyMethodDetail;
    private String contactList;

    // 관련 목록
    private List<String> regions;
    private List<TagItem> tags;

    @Getter
    @Builder
    public static class TagItem {
        private String tagType;
        private String tagValue;
    }

    public static PolicyDetailResponse of(WelfareService ws,
                                           WelfareServiceDetail detail,
                                           List<ServiceRegion> regions,
                                           List<ServiceTag> tags,
                                           boolean bookmarked) {
        List<String> regionNames = regions.stream()
                .map(r -> r.getSidoName() != null
                        ? r.getSidoName() + (r.getSggName() != null ? " " + r.getSggName() : "")
                        : r.getRegionCode())
                .collect(Collectors.toList());

        List<TagItem> tagItems = tags.stream()
                .map(t -> TagItem.builder()
                        .tagType(t.getTagType().name())
                        .tagValue(t.getTagValue())
                        .build())
                .collect(Collectors.toList());

        return PolicyDetailResponse.builder()
                .id(ws.getId())
                .title(ws.getTitle())
                .description(ws.getDescription())
                .unifiedCategory(ws.getUnifiedCategory())
                .status(ws.getStatus().name())
                .sourceType(ws.getSourceType().name())
                .hostOrg(ws.getHostOrg())
                .operatingOrg(ws.getOperatingOrg())
                .minAge(ws.getMinAge())
                .maxAge(ws.getMaxAge())
                .minIncome(ws.getMinIncome())
                .maxIncome(ws.getMaxIncome())
                .supportContent(ws.getSupportContent())
                .applyMethodName(ws.getApplyMethodName())
                .startDate(ws.getStartDate())
                .endDate(ws.getEndDate())
                .applyStartDate(ws.getApplyStartDate())
                .applyEndDate(ws.getApplyEndDate())
                .lifeStage(ws.getLifeStage())
                .detailUrl(ws.getDetailUrl())
                .supportCycle(ws.getSupportCycle())
                .provisionType(ws.getProvisionType())
                .isOnlineApply(ws.getIsOnlineApply())
                .bookmarked(bookmarked)
                .targetDetail(detail != null ? detail.getTargetDetail() : null)
                .supportDetail(detail != null ? detail.getSupportDetail() : null)
                .applyMethodDetail(detail != null ? detail.getApplyMethodDetail() : null)
                .contactList(detail != null ? detail.getContactList() : null)
                .regions(regionNames)
                .tags(tagItems)
                .build();
    }
}
