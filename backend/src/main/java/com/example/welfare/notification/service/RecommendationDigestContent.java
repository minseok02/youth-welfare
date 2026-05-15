package com.example.welfare.notification.service;

public record RecommendationDigestContent(
        String title,
        String body,
        String deeplinkUrl,
        String absoluteUrl
) {
}
