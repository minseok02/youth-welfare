package com.example.welfare.notification.repository;

import com.example.welfare.notification.entity.Notification;
import com.example.welfare.notification.entity.NotificationServiceItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class NotificationHistoryCommandRepositoryImpl implements NotificationHistoryCommandRepository {

    private final NotificationRepository notificationRepository;
    private final NotificationServiceItemRepository notificationServiceItemRepository;

    @Override
    public Notification saveNotification(Notification notification) {
        return notificationRepository.save(notification);
    }

    @Override
    public void saveNotificationItems(List<NotificationServiceItem> items) {
        notificationServiceItemRepository.saveAll(items);
    }
}
