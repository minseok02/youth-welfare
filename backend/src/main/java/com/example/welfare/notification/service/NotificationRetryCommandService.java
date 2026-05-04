package com.example.welfare.notification.service;

import com.example.welfare.notification.entity.Notification;
import com.example.welfare.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationRetryCommandService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public boolean claimForRetry(Long notificationId, LocalDateTime at, LocalDateTime claimUntil) {
        return notificationRepository.claimRetryWindow(
                notificationId,
                Notification.NotificationStatus.FAILED,
                at,
                claimUntil
        ) == 1;
    }

    @Transactional
    public Notification save(Notification notification) {
        return notificationRepository.save(notification);
    }
}
