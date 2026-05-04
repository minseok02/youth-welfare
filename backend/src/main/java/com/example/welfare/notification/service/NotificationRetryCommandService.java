package com.example.welfare.notification.service;

import com.example.welfare.notification.entity.Notification;
import com.example.welfare.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationRetryCommandService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public Notification save(Notification notification) {
        return notificationRepository.save(notification);
    }
}
