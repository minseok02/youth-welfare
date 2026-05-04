package com.example.welfare.notification.repository;

import com.example.welfare.notification.entity.Notification;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRetryReadRepository {

    List<Notification> findRetryableFailedNotifications(LocalDateTime at);
}
