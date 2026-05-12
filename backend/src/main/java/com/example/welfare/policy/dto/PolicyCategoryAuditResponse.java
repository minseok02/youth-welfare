package com.example.welfare.policy.dto;

import java.util.List;

public record PolicyCategoryAuditResponse(
        long totalPolicyCount,
        long searchablePolicyCount,
        List<CategoryCount> unifiedCategoryCounts,
        List<SourceCategoryMappingCount> youthBroadCategoryMappings
) {
    public record CategoryCount(
            String unifiedCategory,
            long totalCount,
            long searchableCount
    ) {
    }

    public record SourceCategoryMappingCount(
            String sourceCategory,
            String unifiedCategory,
            long totalCount
    ) {
    }
}
