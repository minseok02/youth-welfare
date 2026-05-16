package com.example.welfare.notification.service;

public record NotificationContent(
        String title,
        String body,
        String deeplinkUrl,
        String absoluteUrl
) {
}
