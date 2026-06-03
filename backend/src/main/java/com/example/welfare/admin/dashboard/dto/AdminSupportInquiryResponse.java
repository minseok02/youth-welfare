package com.example.welfare.admin.dashboard.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminSupportInquiryResponse(
        long openCount,
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
            LocalDateTime createdAt
    ) {
    }
}
