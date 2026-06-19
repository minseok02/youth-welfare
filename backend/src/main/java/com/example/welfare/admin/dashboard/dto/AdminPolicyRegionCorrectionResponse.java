package com.example.welfare.admin.dashboard.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminPolicyRegionCorrectionResponse(
        Long correctionId,
        Long policyId,
        String correctionScope,
        List<RegionItem> regions,
        Long reportId,
        String correctionNote,
        String updatedByUserKey,
        LocalDateTime updatedAt
) {
    public record RegionItem(
            String regionCode,
            String sidoName,
            String sggName
    ) {
    }
}
