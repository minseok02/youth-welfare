package com.example.welfare.notification.service;

import com.example.welfare.notification.dto.NotificationTarget;
import com.example.welfare.notification.entity.Notification.NotificationChannel;
import com.example.welfare.notification.entity.Notification.NotificationStatus;
import com.example.welfare.notification.gateway.NotificationGateway;
import com.example.welfare.recommend.entity.RecommendationLog;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchService {

    static final String RECOMMEND_SUBJECT = "[청년복지] 맞춤 정책 추천";

    private final NotificationRecommendationService notificationRecommendationService;
    private final NotificationMessageService notificationMessageService;
    private final NotificationGateway notificationGateway;
    private final NotificationHistoryService notificationHistoryService;

    public void sendTopRecommendations(NotificationTarget target) {
        NotificationRecommendationService.NotificationDispatchPlan plan =
                notificationRecommendationService.prepareDispatch(target).orElse(null);
        if (plan == null) {
            return;
        }

        User user = plan.user();
        List<UserRecommendation> recommendations = plan.recommendations();
        List<RecommendationLog> logs = plan.logs();
        String messageText = null;
        try {
            messageText = notificationMessageService.buildRecommendationMessage(
                    target.userKey(),
                    target.userId(),
                    recommendations,
                    logs
            );

            boolean sent = notificationGateway.send(target.email(), RECOMMEND_SUBJECT, messageText);
            NotificationStatus status = sent ? NotificationStatus.SENT : NotificationStatus.FAILED;
            String errorMessage = sent ? null : "notification gateway returned false";

            notificationHistoryService.saveResult(
                    user,
                    plan.periodType(),
                    NotificationChannel.EMAIL,
                    status,
                    RECOMMEND_SUBJECT,
                    messageText,
                    recommendations,
                    logs,
                    errorMessage
            );

            if (!sent) {
                log.warn("[NotificationDispatchService] 알림 발송 실패(게이트웨이 false) userId={}", user.getId());
            }
        } catch (Exception e) {
            try {
                notificationHistoryService.saveResult(
                        user,
                        plan.periodType(),
                        NotificationChannel.EMAIL,
                        NotificationStatus.FAILED,
                        RECOMMEND_SUBJECT,
                        messageText,
                        recommendations,
                        logs,
                        e.getMessage()
                );
            } catch (Exception historyException) {
                log.error("[NotificationDispatchService] 알림 이력 저장 실패 userId={}: {}",
                        user.getId(), historyException.getMessage());
            }
            log.error("[NotificationDispatchService] 알림 발송 실패 userId={}: {}", user.getId(), e.getMessage());
        }
    }
}
