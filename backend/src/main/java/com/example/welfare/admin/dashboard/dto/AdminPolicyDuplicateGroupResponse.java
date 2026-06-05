package com.example.welfare.admin.dashboard.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminPolicyDuplicateGroupResponse(
        long openGroupCount,
        long recentOpenGroupCount24h,
        long openDuplicateRowCount,
        List<Item> recentGroups
) {
    public record Item(
            String sourceType,
            String title,
            String hostOrgKey,
            String hostOrgLabel,
            String reviewClass,
            int duplicateCount,
            String sourceIds,
            LocalDateTime latestCreatedAt,
            String status,
            String reviewNote,
            String reviewedByUserKey,
            LocalDateTime reviewedAt
    ) {
    }
}
