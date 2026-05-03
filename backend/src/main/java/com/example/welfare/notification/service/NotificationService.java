package com.example.welfare.notification.service;

import com.example.welfare.notification.gateway.NotificationGateway;
import com.example.welfare.notification.entity.Notification.NotificationChannel;
import com.example.welfare.notification.entity.Notification.NotificationPeriodType;
import com.example.welfare.notification.entity.Notification.NotificationStatus;
import com.example.welfare.notification.dto.NotificationTarget;
import com.example.welfare.recommend.entity.RecommendationLog;
import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.facade.RecommendationFacade;
import com.example.welfare.recommend.service.RecommendationLogService;
import com.example.welfare.recommend.service.ScoreWeightService;
import com.example.welfare.global.util.JwtUtil;
import com.example.welfare.notification.entity.Notification;
import com.example.welfare.user.entity.User.NotificationPeriod;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.service.UserNotificationReadService;
import com.example.welfare.notification.repository.NotificationRepository;
import com.example.welfare.user.service.UserReadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDateTime;

/**
 * 알림 발송 서비스 — Gmail SMTP 이메일만 사용
 * - [A, A, B?] 슬롯 배치로 최대 3건 발송
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final UserReadService userReadService;
    private final UserNotificationReadService userNotificationReadService;
    private final RecommendationFacade recommendationFacade;
    private final RecommendationLogService logService;
    private final ScoreWeightService scoreWeightService;
    private final NotificationSlotSelector notificationSlotSelector;
    private final NotificationGateway notificationGateway;
    private final NotificationHistoryService notificationHistoryService;
    private final NotificationRepository notificationRepository;
    private final JwtUtil jwtUtil;

    private static final int TOP_N = 3;
    private static final String RECOMMEND_SUBJECT = "[청년복지] 맞춤 정책 추천";
    private static final int MAX_RETRY_COUNT = 2;

    @Value("${app.base-url:https://youth-welfare.kr}")
    private String appBaseUrl;

    /**
     * 매일 오전 8시 — 일간 알림 수신 동의 유저에게 [A, A, B?] 최대 3건 발송
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    public void sendDailyNotifications() {
        List<NotificationTarget> targets = userNotificationReadService.getNotificationTargets(NotificationPeriod.DAILY);
        log.info("[NotificationService] 일간 알림 대상: {}명", targets.size());
        targets.forEach(this::sendTopRecommendations);
    }

    /**
     * 매주 월요일 오전 8시 — 주간 알림
     */
    @Scheduled(cron = "0 0 8 * * MON", zone = "Asia/Seoul")
    public void sendWeeklyNotifications() {
        List<NotificationTarget> targets = userNotificationReadService.getNotificationTargets(NotificationPeriod.WEEKLY);
        log.info("[NotificationService] 주간 알림 대상: {}명", targets.size());
        targets.forEach(this::sendTopRecommendations);
    }

    @Scheduled(cron = "0 */30 * * * *", zone = "Asia/Seoul")
    public void retryFailedNotifications() {
        List<Notification> failedNotifications = notificationRepository.findByStatusAndNextRetryAtBefore(
                NotificationStatus.FAILED, LocalDateTime.now());
        failedNotifications.stream()
                .filter(notification -> notification.getRetryCount() < MAX_RETRY_COUNT)
                .forEach(this::retryNotification);
    }

    @Transactional
    public void sendTopRecommendations(NotificationTarget target) {
        User user = userReadService.getActiveUserByUserKey(target.userKey());
        double minScore = target.notificationMinScore() != null ? target.notificationMinScore() : 0.0;
        List<UserRecommendation> recs = List.of();
        List<RecommendationLog> logs = List.of();
        String messageText = null;
        try {
            int poolSize = Math.max(50, Math.max(TOP_N, target.displayCount()));
            List<UserRecommendation> recommendationPool = recommendationFacade.getRecommendations(target.userId(), poolSize);
            recs = notificationSlotSelector.selectCandidates(recommendationPool, minScore);
            if (recs.isEmpty()) return;

            ScoreWeight weight = scoreWeightService.getActiveWeight();
            logs = logService.logNotification(user, recs, weight);
            messageText = buildEmailText(target.userKey(), target.userId(), recs, logs);

            boolean sent = notificationGateway.send(
                    target.email(),
                    RECOMMEND_SUBJECT,
                    messageText
            );

            NotificationStatus status = sent ? NotificationStatus.SENT : NotificationStatus.FAILED;
            String errorMessage = sent ? null : "notification gateway returned false";
            notificationHistoryService.saveResult(
                    user,
                    toPeriodType(target.notificationPeriod()),
                    NotificationChannel.EMAIL,
                    status,
                    RECOMMEND_SUBJECT,
                    messageText,
                    recs,
                    logs,
                    errorMessage
            );

            if (!sent) {
                log.warn("[NotificationService] 알림 발송 실패(게이트웨이 false) userId={}", user.getId());
            }
        } catch (Exception e) {
            try {
                notificationHistoryService.saveResult(
                        user,
                        toPeriodType(target.notificationPeriod()),
                        NotificationChannel.EMAIL,
                        NotificationStatus.FAILED,
                        RECOMMEND_SUBJECT,
                        messageText,
                        recs,
                        logs,
                        e.getMessage()
                );
            } catch (Exception historyException) {
                log.error("[NotificationService] 알림 이력 저장 실패 userId={}: {}",
                        user.getId(), historyException.getMessage());
            }
            log.error("[NotificationService] 알림 발송 실패 userId={}: {}", user.getId(), e.getMessage());
        }
    }

    private NotificationPeriodType toPeriodType(NotificationPeriod period) {
        return switch (period) {
            case DAILY -> NotificationPeriodType.DAILY;
            case WEEKLY -> NotificationPeriodType.WEEKLY;
            case NONE -> NotificationPeriodType.MANUAL;
        };
    }

    private String buildEmailText(List<UserRecommendation> recs, List<RecommendationLog> logs) {
        return buildEmailText(null, null, recs, logs);
    }

    private String buildEmailText(String userKey, Long userId, List<UserRecommendation> recs, List<RecommendationLog> logs) {
        StringBuilder sb = new StringBuilder("맞춤 복지 정책 추천\n\n");
        for (int i = 0; i < recs.size(); i++) {
            UserRecommendation rec = recs.get(i);
            String logId = (i < logs.size()) ? String.valueOf(logs.get(i).getId()) : "";
            sb.append(i + 1).append(". ").append(rec.getService().getTitle()).append("\n");
            if (rec.getAiReason() != null && !rec.getAiReason().isBlank()) {
                sb.append("   추천 이유: ").append(rec.getAiReason()).append("\n");
            }
            sb.append("   ").append(appBaseUrl).append("/policies/")
                    .append(rec.getService().getId())
                    .append("?log_id=").append(logId).append("\n\n");
        }
        if (userKey != null && userId != null) {
            sb.append("수신 거부: ").append(appBaseUrl).append("/api/notifications/unsubscribe?token=")
                    .append(jwtUtil.generateNotificationToken(userKey, userId))
                    .append("\n");
        }
        return sb.toString();
    }

    private void retryNotification(Notification notification) {
        try {
            boolean sent = notificationGateway.send(
                    resolveNotificationEmail(notification),
                    notification.getSubject(),
                    notification.getMessageText()
            );
            if (sent) {
                notification.markSent();
                return;
            }
            scheduleNextRetry(notification, "notification gateway returned false");
        } catch (Exception e) {
            scheduleNextRetry(notification, e.getMessage());
        }
    }

    private void scheduleNextRetry(Notification notification, String errorMessage) {
        if (notification.getRetryCount() + 1 >= MAX_RETRY_COUNT) {
            notification.scheduleRetry(null, errorMessage);
            return;
        }
        long delayMinutes = 120;
        notification.scheduleRetry(LocalDateTime.now().plusMinutes(delayMinutes), errorMessage);
    }

    private String resolveNotificationEmail(Notification notification) {
        if (notification.getUserKey() == null || notification.getUserKey().isBlank()) {
            throw new IllegalStateException("Notification user_key is required for retry");
        }
        return userNotificationReadService.getNotificationEmailByUserKey(notification.getUserKey());
    }
}
