package com.example.welfare.notification.dto;

import com.example.welfare.notification.entity.WebPushSubscription;

import java.time.LocalDateTime;

public record WebPushSubscriptionResponse(
        Long id,
        String endpoint,
        String deviceLabel,
        boolean enabled,
        LocalDateTime lastSeenAt,
        LocalDateTime createdAt
) {

    public static WebPushSubscriptionResponse from(WebPushSubscription subscription) {
        return new WebPushSubscriptionResponse(
                subscription.getId(),
                subscription.getEndpoint(),
                subscription.getDeviceLabel(),
                subscription.isEnabled(),
                subscription.getLastSeenAt(),
                subscription.getCreatedAt()
        );
    }
}
