package com.example.welfare.notification.repository;

import com.example.welfare.notification.entity.Notification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRetryReadRepository {

    List<Notification> findRetryableFailedNotifications(LocalDateTime at);

    List<Long> findRetryableFailedNotificationIds(LocalDateTime at);

    Optional<Notification> findById(Long notificationId);
}
