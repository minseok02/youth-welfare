package com.example.welfare.notification.service;

import com.example.welfare.notification.entity.Notification;
import com.example.welfare.notification.repository.NotificationRetryReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationRetryReadService {

    private final NotificationRetryReadRepository notificationRetryReadRepository;

    public List<Notification> findRetryableFailedNotifications(LocalDateTime at) {
        return notificationRetryReadRepository.findRetryableFailedNotifications(at);
    }
}
