package com.example.welfare.admin.dashboard.dto;

public record AdminNotificationStaleHideRequest(
        String kind,
        String title,
        String deeplinkUrl,
        Integer olderThanDays
) {
}
