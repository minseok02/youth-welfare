package com.example.welfare.notification.dto;

public record NotificationDeadlineTestDispatchResponse(
        String userKey,
        String email,
        int days,
        String dispatchStatus,
        int policyCount,
        String message
) {
}
