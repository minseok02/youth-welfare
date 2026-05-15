package com.example.welfare.notification.dto;

import com.example.welfare.notification.entity.UserAlert;

import java.time.LocalDateTime;

public record UserAlertResponse(
        Long id,
        String kind,
        String status,
        String title,
        String body,
        String deeplinkUrl,
        String metadataJson,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {

    public static UserAlertResponse from(UserAlert alert) {
        return new UserAlertResponse(
                alert.getId(),
                alert.getKind().name(),
                alert.getStatus().name(),
                alert.getTitle(),
                alert.getBody(),
                alert.getDeeplinkUrl(),
                alert.getMetadataJson(),
                alert.getReadAt(),
                alert.getCreatedAt()
        );
    }
}
