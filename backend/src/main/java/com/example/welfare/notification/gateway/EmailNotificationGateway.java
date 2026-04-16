package com.example.welfare.notification.gateway;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailNotificationGateway implements NotificationGateway {

    private final EmailClient emailClient;

    @Override
    public boolean send(String to, String subject, String text) {
        return emailClient.send(to, subject, text);
    }
}
