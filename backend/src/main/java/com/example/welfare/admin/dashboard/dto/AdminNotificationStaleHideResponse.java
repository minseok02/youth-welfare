package com.example.welfare.admin.dashboard.dto;

public record AdminNotificationStaleHideResponse(
        long hiddenCount,
        String kind,
        String title,
        String deeplinkUrl,
        int olderThanDays
) {
}
