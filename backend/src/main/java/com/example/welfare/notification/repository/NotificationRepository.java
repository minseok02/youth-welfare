package com.example.welfare.notification.repository;

import com.example.welfare.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByStatusAndNextRetryAtBefore(Notification.NotificationStatus status, LocalDateTime at);
}
