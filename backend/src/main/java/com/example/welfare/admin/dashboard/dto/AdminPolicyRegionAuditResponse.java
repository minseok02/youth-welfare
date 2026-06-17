package com.example.welfare.admin.dashboard.dto;

import java.util.List;

public record AdminPolicyRegionAuditResponse(
        int scannedCount,
        int candidateCount,
        int createdReportCount,
        int skippedExistingReportCount,
        List<Item> candidates
) {
    public record Item(
            Long policyId,
            String policyTitle,
            String sourceType,
            String sourceId,
            String reason,
            List<AdminPolicyRegionCorrectionResponse.RegionItem> suggestedRegions
    ) {
    }
}
