package com.example.welfare.admin.dashboard.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminPolicyFieldCorrectionListResponse(
        List<Item> corrections
) {
    public record Item(
            Long correctionId,
            Long policyId,
            String policyTitle,
            String sourceType,
            String sourceId,
            String correctionType,
            String correctionJson,
            String correctionNote,
            String updatedByUserKey,
            LocalDateTime updatedAt
    ) {
    }
}
