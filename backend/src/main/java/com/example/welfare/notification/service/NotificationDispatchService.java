package com.example.welfare.notification.service;

import com.example.welfare.global.util.RedisKeyHash;
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
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchService {

    static final String RECOMMEND_SUBJECT = "[청년복지] 맞춤 정책 추천";

    private final NotificationRecommendationService notificationRecommendationService;
    private final NotificationDispatchWindowReadService notificationDispatchWindowReadService;
    private final NotificationMessageService notificationMessageService;
    private final NotificationGateway notificationGateway;
    private final NotificationHistoryService notificationHistoryService;
    private final WebPushDispatchService webPushDispatchService;
    private final NotificationAttemptLogService notificationAttemptLogService;

    public NotificationDispatchResult sendTopRecommendations(NotificationTarget target) {
        if (notificationDispatchWindowReadService.hasDispatchHistoryInCurrentWindow(
                target.userKey(),
                toPeriodType(target.notificationPeriod())
        )) {
            log.info("[NotificationDispatchService] 현재 dispatch window 에 이미 이력이 있어 중복 발송을 건너뜁니다. userKeyHash={} period={}",
                    RedisKeyHash.sha256Hex(target.userKey()), target.notificationPeriod());
            return NotificationDispatchResult.skippedWindow();
        }

        NotificationRecommendationService.NotificationDispatchPlan plan =
                notificationRecommendationService.prepareDispatch(target).orElse(null);
        if (plan == null) {
            return NotificationDispatchResult.noRecommendations();
        }

        User user = plan.user();
        String dispatchKey = dispatchKey(user.getUserKey(), plan.periodType());
        Optional<com.example.welfare.notification.entity.Notification> reserved =
                notificationHistoryService.reserveDispatch(
                        user,
                        plan.periodType(),
                        NotificationChannel.EMAIL,
                        dispatchKey,
                        RECOMMEND_SUBJECT
                );
        if (reserved.isEmpty()) {
            log.info("[NotificationDispatchService] dispatch reservation conflict 로 중복 발송을 건너뜁니다. userKeyHash={} period={}",
                    RedisKeyHash.sha256Hex(user.getUserKey()), plan.periodType());
            return NotificationDispatchResult.reservationConflict(plan.recommendations().size());
        }

        com.example.welfare.notification.entity.Notification notification = reserved.get();
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

            boolean emailEnabled = target.notificationEmailYn() && org.springframework.util.StringUtils.hasText(target.email());
            long emailStartedNanos = System.nanoTime();
            boolean sent = !emailEnabled || notificationGateway.send(target.email(), RECOMMEND_SUBJECT, messageText);
            recordAttempt(
                    "email",
                    "recommendation_digest",
                    emailEnabled ? (sent ? "sent" : "gateway_false") : "skipped_disabled",
                    target.userKey(),
                    recommendations.size(),
                    null,
                    sent ? null : "gateway_false",
                    elapsedMs(emailStartedNanos)
            );
            NotificationStatus status = sent ? NotificationStatus.SENT : NotificationStatus.FAILED;
            String errorMessage = sent ? null : "notification gateway returned false";

            notificationHistoryService.saveResult(
                    notification,
                    status,
                    messageText,
                    recommendations,
                    logs,
                    errorMessage,
                    target.notificationInAppYn()
            );
            if (target.notificationWebPushYn()) {
                try {
                    long webPushStartedNanos = System.nanoTime();
                    webPushDispatchService.sendRecommendationDigest(target.userKey(), recommendations);
                    recordAttempt(
                            "web_push",
                            "recommendation_digest",
                            "fanout_completed",
                            target.userKey(),
                            recommendations.size(),
                            null,
                            null,
                            elapsedMs(webPushStartedNanos)
                    );
                } catch (RuntimeException | LinkageError e) {
                    log.warn("[NotificationDispatchService] web push fan-out failed userId={} errorType={}",
                            user.getId(), e.getClass().getSimpleName());
                    recordAttempt(
                            "web_push",
                            "recommendation_digest",
                            "fanout_failed",
                            target.userKey(),
                            recommendations.size(),
                            null,
                            e.getClass().getSimpleName(),
                            0
                    );
                }
            } else {
                recordAttempt(
                        "web_push",
                        "recommendation_digest",
                        "skipped_disabled",
                        target.userKey(),
                        recommendations.size(),
                        null,
                        null,
                        0
                );
            }

            if (!sent) {
                log.warn("[NotificationDispatchService] 알림 발송 실패(게이트웨이 false) userId={}", user.getId());
            }
            return sent
                    ? NotificationDispatchResult.sent(recommendations.size())
                    : NotificationDispatchResult.failed(recommendations.size(), errorMessage);
        } catch (Exception e) {
            try {
                notificationHistoryService.saveResult(
                        notification,
                        NotificationStatus.FAILED,
                        messageText,
                        recommendations,
                        logs,
                        "notification send failed (" + e.getClass().getSimpleName() + ")",
                        target.notificationInAppYn()
                );
            } catch (Exception historyException) {
                log.error("[NotificationDispatchService] 알림 이력 저장 실패 userId={} errorType={}",
                        user.getId(), historyException.getClass().getSimpleName());
            }
            log.error("[NotificationDispatchService] 알림 발송 실패 userId={} errorType={}",
                    user.getId(), e.getClass().getSimpleName());
            return NotificationDispatchResult.failed(recommendations.size(), "notification send failed");
        }
    }

    private com.example.welfare.notification.entity.Notification.NotificationPeriodType toPeriodType(User.NotificationPeriod period) {
        return switch (period) {
            case DAILY -> com.example.welfare.notification.entity.Notification.NotificationPeriodType.DAILY;
            case WEEKLY -> com.example.welfare.notification.entity.Notification.NotificationPeriodType.WEEKLY;
            case NONE -> com.example.welfare.notification.entity.Notification.NotificationPeriodType.MANUAL;
        };
    }

    private String dispatchKey(String userKey,
                               com.example.welfare.notification.entity.Notification.NotificationPeriodType periodType) {
        NotificationDispatchWindowReadService.Window window =
                notificationDispatchWindowReadService.currentWindow(periodType, java.time.LocalDate.now());
        return "%s:%s:%s".formatted(
                periodType.name().toLowerCase(),
                userKey,
                window.start().toLocalDate()
        );
    }

    private long elapsedMs(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private void recordAttempt(String channel,
                               String kind,
                               String outcome,
                               String userKey,
                               int itemCount,
                               String endpointHost,
                               String errorType,
                               long durationMs) {
        String userKeyHash = RedisKeyHash.sha256Hex(userKey);
        if (errorType == null) {
            log.info("[NotificationAttempt] channel={} kind={} outcome={} userKeyHash={} itemCount={} durationMs={}",
                    channel, kind, outcome, userKeyHash, itemCount, durationMs);
        } else {
            log.warn("[NotificationAttempt] channel={} kind={} outcome={} userKeyHash={} itemCount={} errorType={} durationMs={}",
                    channel, kind, outcome, userKeyHash, itemCount, errorType, durationMs);
        }
        notificationAttemptLogService.record(new NotificationAttemptLogCommand(
                userKeyHash,
                channel,
                kind,
                outcome,
                itemCount,
                endpointHost,
                errorType,
                durationMs
        ));
    }

    public record NotificationDispatchResult(
            NotificationDispatchStatus status,
            int recommendationCount,
            String message
    ) {
        public static NotificationDispatchResult skippedWindow() {
            return new NotificationDispatchResult(NotificationDispatchStatus.SKIPPED_WINDOW, 0, null);
        }

        public static NotificationDispatchResult noRecommendations() {
            return new NotificationDispatchResult(NotificationDispatchStatus.NO_RECOMMENDATIONS, 0, null);
        }

        public static NotificationDispatchResult reservationConflict(int recommendationCount) {
            return new NotificationDispatchResult(NotificationDispatchStatus.RESERVATION_CONFLICT, recommendationCount, null);
        }

        public static NotificationDispatchResult sent(int recommendationCount) {
            return new NotificationDispatchResult(NotificationDispatchStatus.SENT, recommendationCount, null);
        }

        public static NotificationDispatchResult failed(int recommendationCount, String message) {
            return new NotificationDispatchResult(NotificationDispatchStatus.FAILED, recommendationCount, message);
        }
    }

    public enum NotificationDispatchStatus {
        SENT,
        FAILED,
        NO_RECOMMENDATIONS,
        SKIPPED_WINDOW,
        RESERVATION_CONFLICT
    }
}
