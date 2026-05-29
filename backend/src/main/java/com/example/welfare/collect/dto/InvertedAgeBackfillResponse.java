package com.example.welfare.collect.dto;

import com.example.welfare.policy.entity.WelfareService;

import java.util.List;

public record InvertedAgeBackfillResponse(
        String scope,
        List<WelfareService.SourceType> sourceTypes,
        int limitPerSource,
        int scannedCount,
        int repairedCount,
        int missingRawPayloadCount,
        int unrepairedCount,
        int failedCount
) {
}
