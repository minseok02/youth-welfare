package com.example.welfare.user.repository;

public record NotificationTargetAggregateReadModel(
        Long userId,
        String userKey,
        String notificationPeriod,
        Double notificationMinScore,
        int displayCount,
        String emailEnc
) {
}
