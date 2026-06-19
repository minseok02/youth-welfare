package com.example.welfare.admin.dashboard.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminPolicyRegionCorrectionListResponse(
        List<Item> corrections
) {
    public record Item(
            Long correctionId,
            Long policyId,
            String policyTitle,
            String sourceType,
            String sourceId,
            String correctionScope,
            boolean active,
            List<AdminPolicyRegionCorrectionResponse.RegionItem> regions,
            String correctionNote,
            String updatedByUserKey,
            LocalDateTime updatedAt
    ) {
    }
}
