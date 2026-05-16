package com.example.welfare.notification.service;

import com.example.welfare.notification.entity.WebPushSubscription;

public interface WebPushSenderClient {

    boolean isConfigured();

    WebPushSendResult send(WebPushSubscription subscription, NotificationContent content);
}
