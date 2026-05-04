package com.example.welfare.notification.repository;

import com.example.welfare.notification.entity.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class NotificationRetryReadRepositoryImpl implements NotificationRetryReadRepository {

    private final NotificationRepository notificationRepository;

    @Override
    public List<Notification> findRetryableFailedNotifications(LocalDateTime at) {
        return notificationRepository.findByStatusAndNextRetryAtBefore(Notification.NotificationStatus.FAILED, at);
    }
}
