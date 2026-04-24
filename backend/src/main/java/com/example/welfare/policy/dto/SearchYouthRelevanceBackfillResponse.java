package com.example.welfare.policy.dto;

public record SearchYouthRelevanceBackfillResponse(
        int processedCount,
        int updatedCount,
        int relevantCount,
        int excludedCount
) {
}
