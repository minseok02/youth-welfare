package com.example.welfare.admin.dashboard.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminPolicyErrorReportResponse(
        long openCount,
        long recentOpenCount24h,
        List<Item> recentReports
) {
    public record Item(
            Long reportId,
            Long policyId,
            String policyTitle,
            String sourceType,
            String sourceId,
            String reasonCode,
            String reasonLabel,
            String note,
            String userKey,
            LocalDateTime createdAt,
            String status,
            String reviewNote,
            String reviewedByUserKey,
            LocalDateTime reviewedAt
    ) {
    }
}
