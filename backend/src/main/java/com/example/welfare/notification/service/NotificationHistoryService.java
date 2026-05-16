package com.example.welfare.notification.service;

import com.example.welfare.notification.entity.Notification;
import com.example.welfare.notification.entity.Notification.NotificationChannel;
import com.example.welfare.notification.entity.Notification.NotificationPeriodType;
import com.example.welfare.notification.entity.Notification.NotificationStatus;
import com.example.welfare.notification.entity.NotificationServiceItem;
import com.example.welfare.notification.repository.NotificationHistoryCommandRepository;
import com.example.welfare.recommend.entity.RecommendationLog;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationHistoryService {

    private static final long INITIAL_RETRY_DELAY_MINUTES = 30L;

    private final NotificationHistoryCommandRepository notificationHistoryCommandRepository;
    private final UserAlertCommandService userAlertCommandService;

    @Transactional
    public Optional<Notification> reserveDispatch(User user,
                                                  NotificationPeriodType periodType,
                                                  NotificationChannel channel,
                                                  String dispatchKey,
                                                  String subject) {
        Notification notification = Notification.builder()
                .userKey(user.getUserKey())
                .dispatchKey(dispatchKey)
                .periodType(periodType)
                .channel(channel)
                .subject(subject)
                .status(NotificationStatus.PENDING)
                .build();
        notification.reserveDispatch();
        return notificationHistoryCommandRepository.reserveNotification(notification);
    }

    @Transactional
    public Notification saveResult(Notification notification,
                                   NotificationStatus status,
                                   String messageText,
                                   List<UserRecommendation> recommendations,
                                   List<RecommendationLog> logs,
                                   String errorMessage,
                                   boolean createInAppAlert) {
        notification.updateDispatchPayload(messageText, recommendations.size());
        if (status == NotificationStatus.SENT) {
            notification.markSent();
        } else if (status == NotificationStatus.FAILED) {
            notification.failInitially(LocalDateTime.now().plusMinutes(INITIAL_RETRY_DELAY_MINUTES), errorMessage);
        } else {
            notification.reserveDispatch();
        }

        Notification saved = notificationHistoryCommandRepository.saveNotification(notification);
        notificationHistoryCommandRepository.replaceNotificationItems(
                saved.getId(),
                buildItems(saved, recommendations, logs)
        );
        if (createInAppAlert) {
            userAlertCommandService.createRecommendationDigestAlert(saved, recommendations);
        }
        return saved;
    }

    private List<NotificationServiceItem> buildItems(Notification notification,
                                                     List<UserRecommendation> recommendations,
                                                     List<RecommendationLog> logs) {
        if (recommendations.isEmpty()) {
            return List.of();
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
        return items;
    }
}
