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
    private String youthMajorLabel;
    private String youthMidLabel;
    private String provisionMethodLabel;
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
        return from(rec, logId, null);
    }

    public static RecommendationResponse from(UserRecommendation rec,
                                              Long logId,
                                              RecommendationCandidateProjection projection) {
        return RecommendationResponse.builder()
                .id(rec.getId())
                .serviceId(rec.getService().getId())
                .logId(logId)
                .title(rec.getService().getTitle())
                .description(rec.getService().getDescription())
                .unifiedCategory(resolveUnifiedCategory(rec, projection))
                .youthMajorLabel(resolveYouthMajorLabel(projection))
                .youthMidLabel(resolveYouthMidLabel(projection))
                .provisionMethodLabel(resolveProvisionMethodLabel(projection))
                .status(rec.getService().getStatus().name())
                .finalScore(rec.getFinalScore())
                .aiScore(rec.getAiScore())
                .aiReason(rec.getAiReason())
                .isBookmarked(rec.isBookmarked())
                .recommendedAt(rec.getRecommendedAt())
                .build();
    }

    private static String resolveUnifiedCategory(UserRecommendation rec,
                                                 RecommendationCandidateProjection projection) {
        if (projection != null && projection.unifiedCategoryCompat() != null) {
            return projection.unifiedCategoryCompat();
        }
        return rec.getService().getUnifiedCategory();
    }

    private static String resolveYouthMidLabel(RecommendationCandidateProjection projection) {
        return projection != null ? projection.youthMidLabel() : null;
    }

    private static String resolveYouthMajorLabel(RecommendationCandidateProjection projection) {
        return projection != null ? projection.youthMajorLabel() : null;
    }

    private static String resolveProvisionMethodLabel(RecommendationCandidateProjection projection) {
        return projection != null ? projection.provisionMethodLabel() : null;
    }
}
