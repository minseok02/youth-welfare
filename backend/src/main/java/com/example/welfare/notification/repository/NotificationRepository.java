package com.example.welfare.notification.repository;

import com.example.welfare.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByStatusAndNextRetryAtBefore(Notification.NotificationStatus status, LocalDateTime at);

    @Query("""
            select n.id
              from Notification n
             where n.status = :status
               and n.nextRetryAt is not null
               and n.nextRetryAt <= :at
            """)
    List<Long> findRetryableFailedNotificationIds(
            @Param("status") Notification.NotificationStatus status,
            @Param("at") LocalDateTime at
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
               set n.nextRetryAt = :claimUntil
             where n.id = :notificationId
               and n.status = :status
               and n.nextRetryAt is not null
               and n.nextRetryAt <= :at
            """)
    int claimRetryWindow(
            @Param("notificationId") Long notificationId,
            @Param("status") Notification.NotificationStatus status,
            @Param("at") LocalDateTime at,
            @Param("claimUntil") LocalDateTime claimUntil
    );

    boolean existsByUserKeyAndPeriodTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            String userKey,
            Notification.NotificationPeriodType periodType,
            LocalDateTime createdAtFrom,
            LocalDateTime createdAtTo
    );
}
