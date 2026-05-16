package com.example.welfare.notification.service;

import com.example.welfare.notification.dto.NotificationTarget;
import com.example.welfare.user.entity.User.NotificationPeriod;
import com.example.welfare.user.service.UserNotificationReadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationScheduleService {

    static final String DAILY_LOCK_NAME = "notification-daily";
    static final String WEEKLY_LOCK_NAME = "notification-weekly";
    static final String DEADLINE_DAILY_LOCK_NAME = "notification-deadline-daily";
    static final String RETRY_LOCK_NAME = "notification-retry";

    private final UserNotificationReadService userNotificationReadService;
    private final NotificationDispatchService notificationDispatchService;
    private final DeadlineReminderDispatchService deadlineReminderDispatchService;
    private final NotificationRetryService notificationRetryService;
    private final NotificationExecutionGuard notificationExecutionGuard;
    @Value("${notification.deadline-reminder.days:3}")
    private int deadlineReminderDays;

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    public void sendDailyNotifications() {
        notificationExecutionGuard.runIfAvailable(
                DAILY_LOCK_NAME,
                () -> sendNotifications(NotificationPeriod.DAILY, "일간")
        );
    }

    @Scheduled(cron = "0 0 8 * * MON", zone = "Asia/Seoul")
    public void sendWeeklyNotifications() {
        notificationExecutionGuard.runIfAvailable(
                WEEKLY_LOCK_NAME,
                () -> sendNotifications(NotificationPeriod.WEEKLY, "주간")
        );
    }

    @Scheduled(cron = "0 30 8 * * *", zone = "Asia/Seoul")
    public void sendDailyDeadlineReminders() {
        notificationExecutionGuard.runIfAvailable(
                DEADLINE_DAILY_LOCK_NAME,
                () -> sendDeadlineReminders(NotificationPeriod.DAILY, deadlineReminderDays, "일간 마감임박")
        );
    }

    @Scheduled(cron = "0 */30 * * * *", zone = "Asia/Seoul")
    public void retryFailedNotifications() {
        notificationExecutionGuard.runIfAvailable(RETRY_LOCK_NAME, () -> {
            NotificationRetryService.RetryRunResult result = notificationRetryService.retryFailedNotifications();
            log.info("[NotificationScheduleService] retry 실행 due={} claimed={} skippedClaim={} sent={} rescheduled={} terminalFailed={}",
                    result.dueCount(),
                    result.claimedCount(),
                    result.skippedClaimCount(),
                    result.sentCount(),
                    result.rescheduledCount(),
                    result.terminalFailureCount());
        });
    }

    private void sendNotifications(NotificationPeriod period, String label) {
        List<NotificationTarget> targets = userNotificationReadService.getNotificationTargets(period);
        log.info("[NotificationScheduleService] {} 알림 대상: {}명", label, targets.size());
        targets.forEach(notificationDispatchService::sendTopRecommendations);
    }

    private void sendDeadlineReminders(NotificationPeriod period, int days, String label) {
        List<NotificationTarget> targets = userNotificationReadService.getNotificationTargets(period);
        int sent = 0;
        int noCandidates = 0;
        int failed = 0;
        int conflicts = 0;
        for (NotificationTarget target : targets) {
            DeadlineReminderDispatchService.DeadlineReminderDispatchResult result =
                    deadlineReminderDispatchService.sendBookmarkedDeadlineReminder(target, days);
            switch (result.status()) {
                case SENT -> sent++;
                case NO_CANDIDATES -> noCandidates++;
                case FAILED -> failed++;
                case RESERVATION_CONFLICT -> conflicts++;
            }
        }
        log.info("[NotificationScheduleService] {} 알림 대상={} days={} sent={} noCandidates={} failed={} conflicts={}",
                label, targets.size(), days, sent, noCandidates, failed, conflicts);
    }
}
