package com.example.welfare.notification.dto;

public record NotificationDigestTestDispatchResponse(
        String userKey,
        String email,
        String notificationPeriod,
        Double notificationMinScore,
        int displayCount,
        String dispatchStatus,
        int recommendationCount,
        String message
) {
}
