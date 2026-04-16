package com.example.welfare.notification.repository;

import com.example.welfare.notification.entity.NotificationServiceItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationServiceItemRepository extends JpaRepository<NotificationServiceItem, Long> {
}
