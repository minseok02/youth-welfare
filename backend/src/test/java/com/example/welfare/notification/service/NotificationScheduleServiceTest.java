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

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationScheduleServiceTest {

    @Mock
    private UserNotificationReadService userNotificationReadService;

    @Mock
    private NotificationDispatchService notificationDispatchService;

    @Mock
    private NotificationRetryService notificationRetryService;

    @InjectMocks
    private NotificationScheduleService notificationScheduleService;

    @Test
    @DisplayName("일간 스케줄은 DAILY 대상 목록을 읽어 dispatch service에 위임한다")
    void sendDailyNotificationsDelegatesToDispatchService() {
        NotificationTarget target = new NotificationTarget(1L, "user-key-1", "test@example.com",
                User.NotificationPeriod.DAILY, 0.8, 10);
        given(userNotificationReadService.getNotificationTargets(User.NotificationPeriod.DAILY))
                .willReturn(List.of(target));

        notificationScheduleService.sendDailyNotifications();

        verify(notificationDispatchService).sendTopRecommendations(target);
    }

    @Test
    @DisplayName("주간 스케줄은 WEEKLY 대상 목록을 읽어 dispatch service에 위임한다")
    void sendWeeklyNotificationsDelegatesToDispatchService() {
        NotificationTarget target = new NotificationTarget(1L, "user-key-1", "test@example.com",
                User.NotificationPeriod.WEEKLY, 0.8, 10);
        given(userNotificationReadService.getNotificationTargets(User.NotificationPeriod.WEEKLY))
                .willReturn(List.of(target));

        notificationScheduleService.sendWeeklyNotifications();

        verify(notificationDispatchService).sendTopRecommendations(target);
    }

    @Test
    @DisplayName("retry 스케줄은 retry service에 위임한다")
    void retryFailedNotificationsDelegatesToRetryService() {
        notificationScheduleService.retryFailedNotifications();

        verify(notificationRetryService).retryFailedNotifications();
    }
}
