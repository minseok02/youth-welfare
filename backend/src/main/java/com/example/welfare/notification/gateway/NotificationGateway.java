package com.example.welfare.notification.gateway;

public interface NotificationGateway {

    boolean send(String to, String subject, String text);
}
