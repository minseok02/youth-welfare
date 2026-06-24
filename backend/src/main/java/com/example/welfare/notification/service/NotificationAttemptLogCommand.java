package com.example.welfare.notification.service;

public record NotificationAttemptLogCommand(
        String userKeyHash,
        String channel,
        String kind,
        String outcome,
        int itemCount,
        String endpointHost,
        String errorType,
        long durationMs
) {
}
