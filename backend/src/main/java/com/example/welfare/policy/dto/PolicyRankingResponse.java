package com.example.welfare.policy.dto;

import com.example.welfare.policy.entity.WelfareService;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PolicyRankingResponse {

    private Long serviceId;
    private String title;
    private String unifiedCategory;
    private String sourceType;
    private Long viewCount;
    private Long apiViewCount;
    private double rankingScore;

    public static PolicyRankingResponse of(WelfareService service, double score) {
        return PolicyRankingResponse.builder()
                .serviceId(service.getId())
                .title(service.getTitle())
                .unifiedCategory(service.getUnifiedCategory())
                .sourceType(service.getSourceType().name())
                .viewCount(service.getViewCount() != null ? service.getViewCount() : 0L)
                .apiViewCount(service.getApiViewCount() != null ? service.getApiViewCount() : 0L)
                .rankingScore(score)
                .build();
    }
}

