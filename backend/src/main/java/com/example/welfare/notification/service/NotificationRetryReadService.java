package com.example.welfare.notification.service;

import com.example.welfare.notification.entity.Notification;
import com.example.welfare.notification.repository.NotificationRetryReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationRetryReadService {

    private final NotificationRetryReadRepository notificationRetryReadRepository;

    public List<Notification> findRetryableFailedNotifications(LocalDateTime at) {
        return notificationRetryReadRepository.findRetryableFailedNotifications(at);
    }

    public List<Long> findRetryableFailedNotificationIds(LocalDateTime at) {
        return notificationRetryReadRepository.findRetryableFailedNotificationIds(at);
    }

    public Optional<Notification> findById(Long notificationId) {
        return notificationRetryReadRepository.findById(notificationId);
    }
}
