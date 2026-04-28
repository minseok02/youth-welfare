package com.example.welfare.notification.service;

import com.example.welfare.notification.entity.Notification;
import com.example.welfare.notification.entity.Notification.NotificationChannel;
import com.example.welfare.notification.entity.Notification.NotificationPeriodType;
import com.example.welfare.notification.entity.Notification.NotificationStatus;
import com.example.welfare.notification.entity.NotificationServiceItem;
import com.example.welfare.notification.repository.NotificationRepository;
import com.example.welfare.notification.repository.NotificationServiceItemRepository;
import com.example.welfare.recommend.entity.RecommendationLog;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationHistoryService {

    private final NotificationRepository notificationRepository;
    private final NotificationServiceItemRepository notificationServiceItemRepository;

    @Transactional
    public Notification saveResult(User user,
                                   NotificationPeriodType periodType,
                                   NotificationChannel channel,
                                   NotificationStatus status,
                                   String subject,
                                   String messageText,
                                   List<UserRecommendation> recommendations,
                                   List<RecommendationLog> logs,
                                   String errorMessage) {
        Notification notification = Notification.builder()
                .userId(user.getId())
                .userKey(user.getUserKey())
                .periodType(periodType)
                .channel(channel)
                .status(status)
                .subject(subject)
                .messageText(messageText)
                .totalServices(recommendations.size())
                .sentAt(status == NotificationStatus.SENT ? LocalDateTime.now() : null)
                .build();
        if (status == NotificationStatus.FAILED) {
            notification.failInitially(LocalDateTime.now().plusMinutes(30), errorMessage);
        }
        notification = notificationRepository.save(notification);

        if (recommendations.isEmpty()) {
            return notification;
        }

        List<NotificationServiceItem> items = new ArrayList<>(recommendations.size());
        for (int i = 0; i < recommendations.size(); i++) {
            UserRecommendation rec = recommendations.get(i);
            Long recommendationLogId = i < logs.size() ? logs.get(i).getId() : null;
            items.add(NotificationServiceItem.builder()
                    .notification(notification)
                    .service(rec.getService())
                    .recommendationLogId(recommendationLogId)
                    .rankOrder(i + 1)
                    .finalScore(rec.getFinalScore())
                    .serviceTitle(rec.getService().getTitle())
                    .build());
        }
        notificationServiceItemRepository.saveAll(items);
        return notification;
    }
}
