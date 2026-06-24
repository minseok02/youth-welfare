package com.example.welfare.notification.service;

import com.example.welfare.notification.entity.Notification;
import com.example.welfare.notification.gateway.NotificationGateway;
import com.example.welfare.user.service.UserNotificationReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationRetryService {

    static final int MAX_RETRY_COUNT = 2;
    private static final long RETRY_DELAY_MINUTES = 120L;
    private static final long RETRY_CLAIM_MINUTES = 30L;

    private final NotificationRetryReadService notificationRetryReadService;
    private final NotificationRetryCommandService notificationRetryCommandService;
    private final UserNotificationReadService userNotificationReadService;
    private final NotificationGateway notificationGateway;

    public RetryRunResult retryFailedNotifications() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime claimUntil = now.plusMinutes(RETRY_CLAIM_MINUTES);
        java.util.List<Long> dueIds = notificationRetryReadService.findRetryableFailedNotificationIds(now);
        int claimedCount = 0;
        int skippedClaimCount = 0;
        int sentCount = 0;
        int rescheduledCount = 0;
        int terminalFailureCount = 0;

        for (Long notificationId : dueIds) {
            java.util.Optional<Notification> claimed = claimNotification(notificationId, now, claimUntil);
            if (claimed.isEmpty()) {
                skippedClaimCount++;
                continue;
            }
            claimedCount++;
            RetryOutcome outcome = retryNotification(claimed.orElseThrow());
            switch (outcome) {
                case SENT -> sentCount++;
                case RESCHEDULED -> rescheduledCount++;
                case TERMINAL_FAILED -> terminalFailureCount++;
            }
        }

        return new RetryRunResult(
                dueIds.size(),
                claimedCount,
                skippedClaimCount,
                sentCount,
                rescheduledCount,
                terminalFailureCount
        );
    }

    private java.util.Optional<Notification> claimNotification(Long notificationId,
                                                               LocalDateTime at,
                                                               LocalDateTime claimUntil) {
        boolean claimed = notificationRetryCommandService.claimForRetry(notificationId, at, claimUntil);
        if (!claimed) {
            return java.util.Optional.empty();
        }
        return notificationRetryReadService.findById(notificationId);
    }

    private RetryOutcome retryNotification(Notification notification) {
        try {
            boolean sent = notificationGateway.send(
                    resolveNotificationEmail(notification),
                    notification.getSubject(),
                    notification.getMessageText()
            );
            if (sent) {
                notification.markSent();
                notificationRetryCommandService.save(notification);
                return RetryOutcome.SENT;
            }
            return scheduleNextRetry(notification, "notification gateway returned false");
        } catch (Exception e) {
            return scheduleNextRetry(notification, "notification retry failed (" + e.getClass().getSimpleName() + ")");
        }
    }

    private RetryOutcome scheduleNextRetry(Notification notification, String errorMessage) {
        if (notification.getRetryCount() + 1 >= MAX_RETRY_COUNT) {
            notification.scheduleRetry(null, errorMessage);
            notificationRetryCommandService.save(notification);
            return RetryOutcome.TERMINAL_FAILED;
        }
        notification.scheduleRetry(LocalDateTime.now().plusMinutes(RETRY_DELAY_MINUTES), errorMessage);
        notificationRetryCommandService.save(notification);
        return RetryOutcome.RESCHEDULED;
    }

    private String resolveNotificationEmail(Notification notification) {
        if (notification.getUserKey() == null || notification.getUserKey().isBlank()) {
            throw new IllegalStateException("Notification user_key is required for retry");
        }
        return userNotificationReadService.getNotificationEmailByUserKey(notification.getUserKey());
    }

    enum RetryOutcome {
        SENT,
        RESCHEDULED,
        TERMINAL_FAILED
    }

    public record RetryRunResult(
            int dueCount,
            int claimedCount,
            int skippedClaimCount,
            int sentCount,
            int rescheduledCount,
            int terminalFailureCount
    ) {
    }
}
