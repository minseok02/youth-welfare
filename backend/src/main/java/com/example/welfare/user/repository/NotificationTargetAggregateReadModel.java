package com.example.welfare.user.repository;

public record NotificationTargetAggregateReadModel(
        Long userId,
        String userKey,
        String notificationPeriod,
        Boolean notificationEmailYn,
        Boolean notificationInAppYn,
        Boolean notificationWebPushYn,
        Double notificationMinScore,
        int displayCount,
        String emailEnc
) {
}
