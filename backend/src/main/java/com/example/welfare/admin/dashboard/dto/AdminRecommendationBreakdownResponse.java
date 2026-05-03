package com.example.welfare.admin.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AdminRecommendationBreakdownResponse(
        LocalDateTime generatedAt,
        int windowDays,
        long totalLogs,
        long clickedLogs,
        long fallbackLogs,
        List<SourceBreakdown> sourceBreakdowns,
        List<CategoryBreakdown> categoryBreakdowns,
        List<WeightBreakdown> weightBreakdowns,
        List<RecommendationSample> recentFallbackSamples,
        List<RecommendationSample> recentClickedSamples
) {

    public record SourceBreakdown(
            String sourceType,
            long sentCount,
            long clickedCount,
            long fallbackCount,
            BigDecimal clickThroughRate,
            BigDecimal fallbackRate
    ) {
    }

    public record CategoryBreakdown(
            String category,
            long sentCount,
            long clickedCount,
            long fallbackCount,
            BigDecimal clickThroughRate,
            BigDecimal fallbackRate
    ) {
    }

    public record WeightBreakdown(
            String weightKey,
            BigDecimal ruleWeight,
            BigDecimal aiWeight,
            long sentCount,
            long clickedCount,
            long fallbackCount,
            BigDecimal clickThroughRate,
            BigDecimal fallbackRate
    ) {
    }

    public record RecommendationSample(
            Long logId,
            Long serviceId,
            String title,
            String sourceType,
            String category,
            BigDecimal finalScore,
            boolean fallback,
            boolean clicked,
            LocalDateTime sentAt,
            LocalDateTime clickedAt
    ) {
    }
}
