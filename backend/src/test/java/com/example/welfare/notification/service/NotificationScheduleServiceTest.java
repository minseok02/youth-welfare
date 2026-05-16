package com.example.welfare.notification.service;

import com.example.welfare.notification.dto.NotificationTarget;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.service.UserNotificationReadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationScheduleServiceTest {

    @Mock
    private UserNotificationReadService userNotificationReadService;

    @Mock
    private NotificationDispatchService notificationDispatchService;

    @Mock
    private DeadlineReminderDispatchService deadlineReminderDispatchService;

    @Mock
    private NotificationRetryService notificationRetryService;

    @Mock
    private NotificationExecutionGuard notificationExecutionGuard;

    @InjectMocks
    private NotificationScheduleService notificationScheduleService;

    @Test
    @DisplayName("일간 스케줄은 DAILY 대상 목록을 읽어 dispatch service에 위임한다")
    void sendDailyNotificationsDelegatesToDispatchService() {
        NotificationTarget target = new NotificationTarget(1L, "user-key-1", "test@example.com",
                User.NotificationPeriod.DAILY, true, true, true, 0.8, 10);
        given(notificationExecutionGuard.runIfAvailable(eq(NotificationScheduleService.DAILY_LOCK_NAME), any()))
                .willAnswer(invocation -> {
                    Runnable runnable = invocation.getArgument(1);
                    runnable.run();
                    return true;
                });
        given(userNotificationReadService.getNotificationTargets(User.NotificationPeriod.DAILY))
                .willReturn(List.of(target));

        notificationScheduleService.sendDailyNotifications();

        verify(notificationDispatchService).sendTopRecommendations(target);
    }

    @Test
    @DisplayName("주간 스케줄은 WEEKLY 대상 목록을 읽어 dispatch service에 위임한다")
    void sendWeeklyNotificationsDelegatesToDispatchService() {
        NotificationTarget target = new NotificationTarget(1L, "user-key-1", "test@example.com",
                User.NotificationPeriod.WEEKLY, true, true, true, 0.8, 10);
        given(notificationExecutionGuard.runIfAvailable(eq(NotificationScheduleService.WEEKLY_LOCK_NAME), any()))
                .willAnswer(invocation -> {
                    Runnable runnable = invocation.getArgument(1);
                    runnable.run();
                    return true;
                });
        given(userNotificationReadService.getNotificationTargets(User.NotificationPeriod.WEEKLY))
                .willReturn(List.of(target));

        notificationScheduleService.sendWeeklyNotifications();

        verify(notificationDispatchService).sendTopRecommendations(target);
    }

    @Test
    @DisplayName("일간 마감임박 스케줄은 DAILY 대상 목록을 읽어 deadline dispatch service에 위임한다")
    void sendDailyDeadlineRemindersDelegatesToDeadlineDispatchService() {
        NotificationTarget target = new NotificationTarget(1L, "user-key-1", "test@example.com",
                User.NotificationPeriod.DAILY, true, true, true, 0.8, 10);
        given(notificationExecutionGuard.runIfAvailable(eq(NotificationScheduleService.DEADLINE_DAILY_LOCK_NAME), any()))
                .willAnswer(invocation -> {
                    Runnable runnable = invocation.getArgument(1);
                    runnable.run();
                    return true;
                });
        given(userNotificationReadService.getNotificationTargets(User.NotificationPeriod.DAILY))
                .willReturn(List.of(target));
        org.springframework.test.util.ReflectionTestUtils.setField(notificationScheduleService, "deadlineReminderDays", 3);
        given(deadlineReminderDispatchService.sendBookmarkedDeadlineReminder(target, 3))
                .willReturn(DeadlineReminderDispatchService.DeadlineReminderDispatchResult.sent(1));

        notificationScheduleService.sendDailyDeadlineReminders();

        verify(deadlineReminderDispatchService).sendBookmarkedDeadlineReminder(target, 3);
    }

    @Test
    @DisplayName("일간 마감임박 스케줄은 conflict 대상이 있어도 다음 대상까지 계속 처리한다")
    void sendDailyDeadlineRemindersContinuesAfterConflict() {
        NotificationTarget first = new NotificationTarget(1L, "user-key-1", "test1@example.com",
                User.NotificationPeriod.DAILY, true, true, true, 0.8, 10);
        NotificationTarget second = new NotificationTarget(2L, "user-key-2", "test2@example.com",
                User.NotificationPeriod.DAILY, true, true, true, 0.8, 10);
        given(notificationExecutionGuard.runIfAvailable(eq(NotificationScheduleService.DEADLINE_DAILY_LOCK_NAME), any()))
                .willAnswer(invocation -> {
                    Runnable runnable = invocation.getArgument(1);
                    runnable.run();
                    return true;
                });
        given(userNotificationReadService.getNotificationTargets(User.NotificationPeriod.DAILY))
                .willReturn(List.of(first, second));
        org.springframework.test.util.ReflectionTestUtils.setField(notificationScheduleService, "deadlineReminderDays", 3);
        given(deadlineReminderDispatchService.sendBookmarkedDeadlineReminder(first, 3))
                .willReturn(DeadlineReminderDispatchService.DeadlineReminderDispatchResult.reservationConflict(1));
        given(deadlineReminderDispatchService.sendBookmarkedDeadlineReminder(second, 3))
                .willReturn(DeadlineReminderDispatchService.DeadlineReminderDispatchResult.sent(1));

        notificationScheduleService.sendDailyDeadlineReminders();

        verify(deadlineReminderDispatchService).sendBookmarkedDeadlineReminder(first, 3);
        verify(deadlineReminderDispatchService).sendBookmarkedDeadlineReminder(second, 3);
        verify(deadlineReminderDispatchService, times(2)).sendBookmarkedDeadlineReminder(any(), eq(3));
    }

    @Test
    @DisplayName("retry 스케줄은 retry service에 위임한다")
    void retryFailedNotificationsDelegatesToRetryService() {
        given(notificationExecutionGuard.runIfAvailable(eq(NotificationScheduleService.RETRY_LOCK_NAME), any()))
                .willAnswer(invocation -> {
                    Runnable runnable = invocation.getArgument(1);
                    runnable.run();
                    return true;
                });
        given(notificationRetryService.retryFailedNotifications())
                .willReturn(new NotificationRetryService.RetryRunResult(3, 2, 1, 1, 1, 0));

        notificationScheduleService.retryFailedNotifications();

        verify(notificationRetryService).retryFailedNotifications();
    }

    @Test
    @DisplayName("스케줄 lock 을 획득하지 못하면 알림 실행을 건너뛴다")
    void sendDailyNotificationsSkipsWhenLockBusy() {
        given(notificationExecutionGuard.runIfAvailable(eq(NotificationScheduleService.DAILY_LOCK_NAME), any()))
                .willReturn(false);

        notificationScheduleService.sendDailyNotifications();

        verify(userNotificationReadService, never()).getNotificationTargets(User.NotificationPeriod.DAILY);
        verify(notificationDispatchService, never()).sendTopRecommendations(any());
    }

    @Test
    @DisplayName("마감임박 스케줄 lock 을 획득하지 못하면 deadline dispatch를 건너뛴다")
    void sendDailyDeadlineRemindersSkipsWhenLockBusy() {
        given(notificationExecutionGuard.runIfAvailable(eq(NotificationScheduleService.DEADLINE_DAILY_LOCK_NAME), any()))
                .willReturn(false);
        org.springframework.test.util.ReflectionTestUtils.setField(notificationScheduleService, "deadlineReminderDays", 3);

        notificationScheduleService.sendDailyDeadlineReminders();

        verify(userNotificationReadService, never()).getNotificationTargets(User.NotificationPeriod.DAILY);
        verify(deadlineReminderDispatchService, never()).sendBookmarkedDeadlineReminder(any(), anyInt());
    }
}
