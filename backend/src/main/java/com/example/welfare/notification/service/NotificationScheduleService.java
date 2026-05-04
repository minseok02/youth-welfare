package com.example.welfare.notification.service;

import com.example.welfare.notification.dto.NotificationTarget;
import com.example.welfare.user.entity.User.NotificationPeriod;
import com.example.welfare.user.service.UserNotificationReadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationScheduleService {

    static final String DAILY_LOCK_NAME = "notification-daily";
    static final String WEEKLY_LOCK_NAME = "notification-weekly";
    static final String RETRY_LOCK_NAME = "notification-retry";

    private final UserNotificationReadService userNotificationReadService;
    private final NotificationDispatchService notificationDispatchService;
    private final NotificationRetryService notificationRetryService;
    private final NotificationExecutionGuard notificationExecutionGuard;

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

    @Scheduled(cron = "0 */30 * * * *", zone = "Asia/Seoul")
    public void retryFailedNotifications() {
        notificationExecutionGuard.runIfAvailable(RETRY_LOCK_NAME, notificationRetryService::retryFailedNotifications);
    }

    private void sendNotifications(NotificationPeriod period, String label) {
        List<NotificationTarget> targets = userNotificationReadService.getNotificationTargets(period);
        log.info("[NotificationScheduleService] {} 알림 대상: {}명", label, targets.size());
        targets.forEach(notificationDispatchService::sendTopRecommendations);
    }
}
