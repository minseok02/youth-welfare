package com.example.welfare.notification.repository;

import com.example.welfare.notification.entity.Notification;
import com.example.welfare.notification.entity.NotificationServiceItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NotificationHistoryCommandRepositoryImpl implements NotificationHistoryCommandRepository {

    private static final String RESERVE_NOTIFICATION_SQL = """
            insert into notifications (
                user_key,
                dispatch_key,
                channel,
                period_type,
                status,
                subject,
                total_services,
                retry_count
            ) values (
                :userKey,
                :dispatchKey,
                :channel,
                :periodType,
                :status,
                :subject,
                :totalServices,
                :retryCount
            )
            on conflict (dispatch_key) do nothing
            """;

    private final NotificationRepository notificationRepository;
    private final NotificationServiceItemRepository notificationServiceItemRepository;
    private final EntityManager entityManager;

    @Override
    public Notification saveNotification(Notification notification) {
        return notificationRepository.save(notification);
    }

    @Override
    public Optional<Notification> reserveNotification(Notification notification) {
        Query query = entityManager.createNativeQuery(RESERVE_NOTIFICATION_SQL)
                .setParameter("userKey", notification.getUserKey())
                .setParameter("dispatchKey", notification.getDispatchKey())
                .setParameter("channel", notification.getChannel().name())
                .setParameter("periodType", notification.getPeriodType().name())
                .setParameter("status", notification.getStatus().name())
                .setParameter("subject", notification.getSubject())
                .setParameter("totalServices", notification.getTotalServices())
                .setParameter("retryCount", notification.getRetryCount());
        int inserted = query.executeUpdate();
        if (inserted == 0) {
            return Optional.empty();
        }
        return notificationRepository.findByDispatchKey(notification.getDispatchKey());
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
