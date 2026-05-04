package com.example.welfare.notification.repository;

import com.example.welfare.notification.entity.Notification;
import com.example.welfare.notification.entity.NotificationServiceItem;

import java.util.List;

public interface NotificationHistoryCommandRepository {

    Notification saveNotification(Notification notification);

    void saveNotificationItems(List<NotificationServiceItem> items);
}
