package com.example.welfare.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NotificationUnsubscribeRequest(
        @NotBlank
        @Size(max = 512)
        String token
) {
}
