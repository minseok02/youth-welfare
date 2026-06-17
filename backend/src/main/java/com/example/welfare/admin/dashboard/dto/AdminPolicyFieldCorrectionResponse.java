package com.example.welfare.admin.dashboard.dto;

import java.time.LocalDateTime;

public record AdminPolicyFieldCorrectionResponse(
        Long correctionId,
        Long policyId,
        String correctionType,
        String correctionJson,
        Long reportId,
        String correctionNote,
        String updatedByUserKey,
        LocalDateTime updatedAt
) {
}
