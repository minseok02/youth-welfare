package com.example.welfare.recommend.dto;

import com.example.welfare.recommend.entity.UserRecommendation;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class RecommendationResponse {

    private Long id;
    private Long serviceId;
    private Long logId;             // CTR 클릭 추적용 (?log_id= 파라미터)
    private String title;
    private String description;
    private String unifiedCategory;
    private String status;
    private BigDecimal finalScore;
    private BigDecimal aiScore;     // null 가능
    private String aiReason;        // null 가능
    private boolean isBookmarked;
    private LocalDateTime recommendedAt;

    public static RecommendationResponse from(UserRecommendation rec) {
        return from(rec, null);
    }

    public static RecommendationResponse from(UserRecommendation rec, Long logId) {
        return RecommendationResponse.builder()
                .id(rec.getId())
                .serviceId(rec.getService().getId())
                .logId(logId)
                .title(rec.getService().getTitle())
                .description(rec.getService().getDescription())
                .unifiedCategory(rec.getService().getUnifiedCategory())
                .status(rec.getService().getStatus().name())
                .finalScore(rec.getFinalScore())
                .aiScore(rec.getAiScore())
                .aiReason(rec.getAiReason())
                .isBookmarked(rec.isBookmarked())
                .recommendedAt(rec.getRecommendedAt())
                .build();
    }
}
