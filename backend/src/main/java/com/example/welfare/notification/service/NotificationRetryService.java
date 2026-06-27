package com.example.welfare.notification.service;

import com.example.welfare.global.util.LogSanitizer;
import com.example.welfare.global.util.RedisKeyHash;
import com.example.welfare.notification.entity.Notification;
import com.example.welfare.notification.gateway.NotificationGateway;
import com.example.welfare.user.service.UserNotificationReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class NotificationRetryService {

    static final int MAX_RETRY_COUNT = 2;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;
    private static final long RETRY_DELAY_MINUTES = 120L;
    private static final long RETRY_CLAIM_MINUTES = 30L;

    private final NotificationRetryReadService notificationRetryReadService;
    private final NotificationRetryCommandService notificationRetryCommandService;
    private final UserNotificationReadService userNotificationReadService;
    private final NotificationGateway notificationGateway;
    private final NotificationAttemptLogService notificationAttemptLogService;

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
        long startedNanos = System.nanoTime();
        try {
            boolean sent = notificationGateway.send(
                    resolveNotificationEmail(notification),
                    notification.getSubject(),
                    notification.getMessageText()
            );
            if (sent) {
                notification.markSent();
                notificationRetryCommandService.save(notification);
                recordRetryAttempt(notification, "sent", null, elapsedMs(startedNanos));
                return RetryOutcome.SENT;
            }
            return scheduleNextRetry(notification, "notification gateway returned false", "gateway_false", startedNanos);
        } catch (Exception e) {
            return scheduleNextRetry(
                    notification,
                    retryFailureMessage(e),
                    e.getClass().getSimpleName(),
                    startedNanos
            );
        }
    }

    private String retryFailureMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return "notification retry failed (" + e.getClass().getSimpleName() + ")";
        }
        String sanitizedMessage = LogSanitizer.sanitizeSingleLine(message, MAX_ERROR_MESSAGE_LENGTH);
        String formatted = "notification retry failed (" + e.getClass().getSimpleName() + "): " + sanitizedMessage;
        return formatted.length() > MAX_ERROR_MESSAGE_LENGTH
                ? formatted.substring(0, MAX_ERROR_MESSAGE_LENGTH)
                : formatted;
    }

    private RetryOutcome scheduleNextRetry(Notification notification,
                                           String errorMessage,
                                           String errorType,
                                           long startedNanos) {
        if (notification.getRetryCount() + 1 >= MAX_RETRY_COUNT) {
            notification.scheduleRetry(null, errorMessage);
            notificationRetryCommandService.save(notification);
            recordRetryAttempt(notification, "terminal_failed", errorType, elapsedMs(startedNanos));
            return RetryOutcome.TERMINAL_FAILED;
        }
        notification.scheduleRetry(LocalDateTime.now().plusMinutes(RETRY_DELAY_MINUTES), errorMessage);
        notificationRetryCommandService.save(notification);
        recordRetryAttempt(notification, "rescheduled", errorType, elapsedMs(startedNanos));
        return RetryOutcome.RESCHEDULED;
    }

    private String resolveNotificationEmail(Notification notification) {
        if (notification.getUserKey() == null || notification.getUserKey().isBlank()) {
            throw new IllegalStateException("Notification user_key is required for retry");
        }
        return userNotificationReadService.getNotificationEmailByUserKey(notification.getUserKey());
    }

    private void recordRetryAttempt(Notification notification, String outcome, String errorType, long durationMs) {
        notificationAttemptLogService.record(new NotificationAttemptLogCommand(
                RedisKeyHash.sha256Hex(notification.getUserKey()),
                notification.getChannel().name(),
                "notification_retry",
                outcome,
                notification.getTotalServices() == null ? 0 : notification.getTotalServices(),
                null,
                errorType,
                durationMs
        ));
    }

    private long elapsedMs(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
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
