package com.example.welfare.admin.dashboard.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AdminPolicyLinkReviewResponse(
        long openCount,
        long recentOpenCount24h,
        List<Item> recentReviews
) {
    public record Item(
            Long serviceId,
            String policyTitle,
            String sourceType,
            String sourceId,
            String hostOrgLabel,
            String operatingOrgLabel,
            String categoryMain,
            String categorySub,
            LocalDate applyEndDate,
            LocalDate endDate,
            LocalDateTime createdAt,
            String status,
            String reviewNote,
            String reviewedByUserKey,
            LocalDateTime reviewedAt
    ) {
    }
}
