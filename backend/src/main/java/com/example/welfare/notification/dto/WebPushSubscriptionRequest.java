package com.example.welfare.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class WebPushSubscriptionRequest {

    @NotBlank
    @Size(max = 500)
    private String endpoint;

    @NotBlank
    @Size(max = 255)
    private String p256dh;

    @NotBlank
    @Size(max = 255)
    private String auth;

    @Size(max = 500)
    private String userAgent;

    @Size(max = 100)
    private String deviceLabel;
}
