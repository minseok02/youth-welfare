package com.example.welfare.policy.dto;

import java.util.List;

public record PolicyCategoryAuditResponse(
        long totalPolicyCount,
        long searchablePolicyCount,
        double searchablePolicyRatio,
        List<CategoryCount> unifiedCategoryCounts,
        List<CategorySummary> topUnifiedCategorySummaries,
        List<SourceCategoryMappingCount> youthBroadCategoryMappings,
        List<SourceCategorySummary> youthBroadCategorySummaries
) {
    public record CategoryCount(
            String unifiedCategory,
            long totalCount,
            long searchableCount
    ) {
    }

    public record CategorySummary(
            String unifiedCategory,
            long totalCount,
            long searchableCount,
            double totalShare,
            double searchableCoverage
    ) {
    }

    public record SourceCategoryMappingCount(
            String sourceCategory,
            String unifiedCategory,
            long totalCount
    ) {
    }

    public record SourceCategorySummary(
            String sourceCategory,
            long totalCount,
            String dominantUnifiedCategory,
            long dominantCount,
            double dominantShare,
            List<SourceCategoryMappingCount> mappings
    ) {
    }
}
