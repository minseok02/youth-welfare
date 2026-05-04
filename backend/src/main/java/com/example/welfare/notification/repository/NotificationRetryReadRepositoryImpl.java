package com.example.welfare.notification.repository;

import com.example.welfare.notification.entity.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NotificationRetryReadRepositoryImpl implements NotificationRetryReadRepository {

    private final NotificationRepository notificationRepository;

    @Override
    public List<Notification> findRetryableFailedNotifications(LocalDateTime at) {
        return notificationRepository.findByStatusAndNextRetryAtBefore(Notification.NotificationStatus.FAILED, at);
    }

    @Override
    public List<Long> findRetryableFailedNotificationIds(LocalDateTime at) {
        return notificationRepository.findRetryableFailedNotificationIds(Notification.NotificationStatus.FAILED, at);
    }

    @Override
    public Optional<Notification> findById(Long notificationId) {
        return notificationRepository.findById(notificationId);
    }
}
