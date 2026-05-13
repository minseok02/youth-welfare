package com.example.welfare.policy.dto;

import com.example.welfare.policy.entity.WelfareService;

import java.util.List;

public record PolicyReferenceUrlBackfillResponse(
        String scope,
        List<WelfareService.SourceType> sourceTypes,
        int limitPerSource,
        boolean missingOnly,
        int scannedCount,
        int skippedCount,
        int updatedCount,
        int missingServiceCount,
        int failedCount
) {
}
