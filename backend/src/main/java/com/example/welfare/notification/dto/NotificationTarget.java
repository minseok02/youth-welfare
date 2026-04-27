package com.example.welfare.notification.dto;

import com.example.welfare.user.entity.User;

public record NotificationTarget(
        Long userId,
        String userKey,
        String email,
        User.NotificationPeriod notificationPeriod,
        Double notificationMinScore,
        int displayCount
) {
}
