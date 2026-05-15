package com.example.welfare.notification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WebPushTestSendRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String body;

    @NotBlank
    private String url;
}
