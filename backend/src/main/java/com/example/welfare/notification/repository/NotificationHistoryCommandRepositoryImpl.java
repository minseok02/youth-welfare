package com.example.welfare.notification.repository;

import com.example.welfare.notification.entity.Notification;
import com.example.welfare.notification.entity.NotificationServiceItem;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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
    public Optional<Notification> reserveNotification(Notification notification) {
        try {
            return Optional.of(notificationRepository.save(notification));
        } catch (DataIntegrityViolationException e) {
            return Optional.empty();
        }
    }

    @Override
    public void replaceNotificationItems(Long notificationId, List<NotificationServiceItem> items) {
        notificationServiceItemRepository.deleteByNotificationId(notificationId);
        if (items.isEmpty()) {
            return;
        }
        notificationServiceItemRepository.saveAll(items);
    }

    @Override
    public void saveNotificationItems(List<NotificationServiceItem> items) {
        notificationServiceItemRepository.saveAll(items);
    }
}
