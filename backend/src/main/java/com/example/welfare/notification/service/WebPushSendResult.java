package com.example.welfare.notification.service;

public record WebPushSendResult(
        boolean success,
        boolean disableSubscription,
        String errorMessage
) {
    public static WebPushSendResult sent() {
        return new WebPushSendResult(true, false, null);
    }

    public static WebPushSendResult failure(String errorMessage) {
        return new WebPushSendResult(false, false, errorMessage);
    }

    public static WebPushSendResult disable(String errorMessage) {
        return new WebPushSendResult(false, true, errorMessage);
    }
}
