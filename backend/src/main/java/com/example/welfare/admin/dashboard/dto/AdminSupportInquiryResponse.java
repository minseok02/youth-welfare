package com.example.welfare.admin.dashboard.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminSupportInquiryResponse(
        long openCount,
        long recentOpenCount24h,
        List<Item> recentInquiries
) {
    public record Item(
            Long inquiryId,
            String categoryCode,
            String categoryLabel,
            String contactEmail,
            String message,
            String routePath,
            String userKey,
            LocalDateTime createdAt,
            String status,
            String reviewNote,
            String reviewedByUserKey,
            LocalDateTime reviewedAt
    ) {
    }
}
