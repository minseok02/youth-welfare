package com.example.welfare.notification.dto;

import com.example.welfare.user.entity.User;

public record NotificationTarget(
        Long userId,
        String userKey,
        String email,
        User.NotificationPeriod notificationPeriod,
        boolean notificationEmailYn,
        boolean notificationInAppYn,
        boolean notificationWebPushYn,
        Double notificationMinScore,
        int displayCount
) {
}
