package com.example.welfare.notification.service;

import com.example.welfare.notification.entity.Notification;
import com.example.welfare.notification.gateway.NotificationGateway;
import com.example.welfare.user.service.UserNotificationReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationRetryService {

    static final int MAX_RETRY_COUNT = 2;
    private static final long RETRY_DELAY_MINUTES = 120L;

    private final NotificationRetryReadService notificationRetryReadService;
    private final UserNotificationReadService userNotificationReadService;
    private final NotificationGateway notificationGateway;

    public void retryFailedNotifications() {
        notificationRetryReadService.findRetryableFailedNotifications(LocalDateTime.now()).stream()
                .filter(notification -> notification.getRetryCount() < MAX_RETRY_COUNT)
                .forEach(this::retryNotification);
    }

    private void retryNotification(Notification notification) {
        try {
            boolean sent = notificationGateway.send(
                    resolveNotificationEmail(notification),
                    notification.getSubject(),
                    notification.getMessageText()
            );
            if (sent) {
                notification.markSent();
                return;
            }
            scheduleNextRetry(notification, "notification gateway returned false");
        } catch (Exception e) {
            scheduleNextRetry(notification, e.getMessage());
        }
    }

    private void scheduleNextRetry(Notification notification, String errorMessage) {
        if (notification.getRetryCount() + 1 >= MAX_RETRY_COUNT) {
            notification.scheduleRetry(null, errorMessage);
            return;
        }
        notification.scheduleRetry(LocalDateTime.now().plusMinutes(RETRY_DELAY_MINUTES), errorMessage);
    }

    private String resolveNotificationEmail(Notification notification) {
        if (notification.getUserKey() == null || notification.getUserKey().isBlank()) {
            throw new IllegalStateException("Notification user_key is required for retry");
        }
        return userNotificationReadService.getNotificationEmailByUserKey(notification.getUserKey());
    }
}
