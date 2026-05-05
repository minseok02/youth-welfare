package com.example.welfare.notification.repository;

import com.example.welfare.notification.entity.Notification;
import com.example.welfare.notification.entity.NotificationServiceItem;

import java.util.List;
import java.util.Optional;

public interface NotificationHistoryCommandRepository {

    Notification saveNotification(Notification notification);

    Optional<Notification> reserveNotification(Notification notification);

    void replaceNotificationItems(Long notificationId, List<NotificationServiceItem> items);

    void saveNotificationItems(List<NotificationServiceItem> items);
}
