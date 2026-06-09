package com.example.welfare.notification.service;

import com.example.welfare.notification.dto.NotificationTarget;
import com.example.welfare.notification.entity.Notification;
import com.example.welfare.notification.entity.Notification.NotificationChannel;
import com.example.welfare.notification.entity.Notification.NotificationPeriodType;
import com.example.welfare.notification.entity.Notification.NotificationStatus;
import com.example.welfare.notification.gateway.NotificationGateway;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.service.RecommendationBookmarkReadService;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.service.ActiveUserReadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeadlineReminderDispatchService {

    static final String DEADLINE_SUBJECT = "[청년복지] 북마크 정책 마감 임박 알림";

    private final ActiveUserReadService activeUserReadService;
    private final RecommendationBookmarkReadService recommendationBookmarkReadService;
    private final NotificationMessageService notificationMessageService;
    private final NotificationGateway notificationGateway;
    private final NotificationHistoryService notificationHistoryService;
    private final WebPushDispatchService webPushDispatchService;

    public DeadlineReminderDispatchResult sendBookmarkedDeadlineReminder(NotificationTarget target, int days) {
        User user = activeUserReadService.getActiveUserByUserKey(target.userKey());
        List<WelfareService> services = selectDeadlineCandidates(target.userKey(), days);
        if (services.isEmpty()) {
            return DeadlineReminderDispatchResult.noCandidates();
        }

        String dispatchKey = dispatchKey(target.userKey(), days, LocalDate.now());
        Optional<Notification> reserved = notificationHistoryService.reserveDispatch(
                user,
                NotificationPeriodType.MANUAL,
                NotificationChannel.EMAIL,
                dispatchKey,
                DEADLINE_SUBJECT
        );
        if (reserved.isEmpty()) {
            log.info("[DeadlineReminderDispatchService] dispatch reservation conflict userKey={} days={}",
                    target.userKey(), days);
            return DeadlineReminderDispatchResult.reservationConflict(services.size());
        }

        Notification notification = reserved.get();
        String messageText = null;
        try {
            messageText = notificationMessageService.buildDeadlineReminderMessage(
                    target.userKey(),
                    target.userId(),
                    services,
                    days
            );

            boolean emailEnabled = target.notificationEmailYn() && StringUtils.hasText(target.email());
            boolean sent = !emailEnabled || notificationGateway.send(target.email(), DEADLINE_SUBJECT, messageText);
            NotificationStatus status = sent ? NotificationStatus.SENT : NotificationStatus.FAILED;
            String errorMessage = sent ? null : "notification gateway returned false";

            notificationHistoryService.saveDeadlineReminderResult(
                    notification,
                    status,
                    messageText,
                    services,
                    errorMessage,
                    target.notificationInAppYn(),
                    days
            );
            if (target.notificationWebPushYn()) {
                try {
                    webPushDispatchService.sendDeadlineReminder(target.userKey(), services, days);
                } catch (RuntimeException | LinkageError e) {
                    log.warn("[DeadlineReminderDispatchService] deadline web push fan-out failed userId={}: {}",
                            user.getId(), e.getMessage());
                }
            }

            if (!sent) {
                log.warn("[DeadlineReminderDispatchService] deadline reminder send failed(게이트웨이 false) userId={}", user.getId());
            }
            return sent
                    ? DeadlineReminderDispatchResult.sent(services.size())
                    : DeadlineReminderDispatchResult.failed(services.size(), errorMessage);
        } catch (Exception e) {
            try {
                notificationHistoryService.saveDeadlineReminderResult(
                        notification,
                        NotificationStatus.FAILED,
                        messageText,
                        services,
                        e.getMessage(),
                        target.notificationInAppYn(),
                        days
                );
            } catch (Exception historyException) {
                log.error("[DeadlineReminderDispatchService] deadline reminder history save failed userId={}: {}",
                        user.getId(), historyException.getMessage());
            }
            log.error("[DeadlineReminderDispatchService] deadline reminder send failed userId={}: {}",
                    user.getId(), e.getMessage());
            return DeadlineReminderDispatchResult.failed(services.size(), e.getMessage());
        }
    }

    private List<WelfareService> selectDeadlineCandidates(String userKey, int days) {
        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(days);
        return recommendationBookmarkReadService.findLatestBookmarkedRecommendations(userKey).stream()
                .map(UserRecommendation::getService)
                .filter(service -> service.getApplyEndDate() != null)
                .filter(service -> !service.getApplyEndDate().isBefore(today))
                .filter(service -> !service.getApplyEndDate().isAfter(deadline))
                .sorted(Comparator.comparing(WelfareService::getApplyEndDate).thenComparing(WelfareService::getId))
                .distinct()
                .limit(3)
                .toList();
    }

    private String dispatchKey(String userKey, int days, LocalDate today) {
        return "deadline:%s:%s:%d".formatted(userKey, today, days);
    }

    public record DeadlineReminderDispatchResult(
            DeadlineReminderDispatchStatus status,
            int policyCount,
            String message
    ) {
        public static DeadlineReminderDispatchResult noCandidates() {
            return new DeadlineReminderDispatchResult(DeadlineReminderDispatchStatus.NO_CANDIDATES, 0, null);
        }

        public static DeadlineReminderDispatchResult reservationConflict(int policyCount) {
            return new DeadlineReminderDispatchResult(DeadlineReminderDispatchStatus.RESERVATION_CONFLICT, policyCount, null);
        }

        public static DeadlineReminderDispatchResult sent(int policyCount) {
            return new DeadlineReminderDispatchResult(DeadlineReminderDispatchStatus.SENT, policyCount, null);
        }

        public static DeadlineReminderDispatchResult failed(int policyCount, String message) {
            return new DeadlineReminderDispatchResult(DeadlineReminderDispatchStatus.FAILED, policyCount, message);
        }
    }

    public enum DeadlineReminderDispatchStatus {
        SENT,
        FAILED,
        NO_CANDIDATES,
        RESERVATION_CONFLICT
    }
}
