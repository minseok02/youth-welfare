package com.example.welfare.notification.dto;

public record WebPushTestSendResponse(
        int attemptedCount,
        int sentCount,
        int disabledCount,
        int failedCount
) {
}
