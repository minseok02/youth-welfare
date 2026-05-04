package com.example.welfare.notification.service;

import com.example.welfare.notification.entity.Notification;
import com.example.welfare.notification.entity.Notification.NotificationChannel;
import com.example.welfare.notification.entity.Notification.NotificationPeriodType;
import com.example.welfare.notification.entity.Notification.NotificationStatus;
import com.example.welfare.notification.gateway.NotificationGateway;
import com.example.welfare.user.service.UserNotificationReadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class NotificationRetryServiceTest {

    @Mock
    private NotificationRetryReadService notificationRetryReadService;
    @Mock
    private NotificationRetryCommandService notificationRetryCommandService;
    @Mock
    private UserNotificationReadService userNotificationReadService;
    @Mock
    private NotificationGateway notificationGateway;

    @InjectMocks
    private NotificationRetryService notificationRetryService;

    @Test
    @DisplayName("재시도 발송이 성공하면 상태를 SENT로 전환한다")
    void retryFailedNotificationsMarksSentOnSuccess() {
        Notification notification = sampleFailedNotification(1);

        given(notificationRetryReadService.findRetryableFailedNotifications(any(LocalDateTime.class)))
                .willReturn(List.of(notification));
        given(userNotificationReadService.getNotificationEmailByUserKey("user-key-1")).willReturn("test@example.com");
        given(notificationGateway.send("test@example.com", "[청년복지] 맞춤 정책 추천", "body"))
                .willReturn(true);

        notificationRetryService.retryFailedNotifications();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isNotNull();
        assertThat(notification.getNextRetryAt()).isNull();
        assertThat(notification.getErrorMessage()).isNull();
        org.mockito.Mockito.verify(notificationRetryCommandService).save(notification);
    }

    @Test
    @DisplayName("첫 재시도도 실패하면 2시간 뒤로 다시 예약한다")
    void retryFailedNotificationsSchedulesTwoHourDelayAfterFirstRetryFailure() {
        Notification notification = sampleFailedNotification(0);

        given(notificationRetryReadService.findRetryableFailedNotifications(any(LocalDateTime.class)))
                .willReturn(List.of(notification));
        given(userNotificationReadService.getNotificationEmailByUserKey("user-key-1")).willReturn("test@example.com");
        given(notificationGateway.send("test@example.com", "[청년복지] 맞춤 정책 추천", "body"))
                .willReturn(false);

        LocalDateTime before = LocalDateTime.now();
        notificationRetryService.retryFailedNotifications();
        LocalDateTime after = LocalDateTime.now();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getRetryCount()).isEqualTo(1);
        assertThat(notification.getErrorMessage()).isEqualTo("notification gateway returned false");
        assertThat(notification.getNextRetryAt()).isBetween(before.plusMinutes(120), after.plusMinutes(120));
        org.mockito.Mockito.verify(notificationRetryCommandService).save(notification);
    }

    @Test
    @DisplayName("최대 재시도 직전 실패하면 더 이상 다음 재시도를 예약하지 않는다")
    void retryFailedNotificationsStopsSchedulingAfterMaxRetry() {
        Notification notification = sampleFailedNotification(1);

        given(notificationRetryReadService.findRetryableFailedNotifications(any(LocalDateTime.class)))
                .willReturn(List.of(notification));
        given(userNotificationReadService.getNotificationEmailByUserKey("user-key-1")).willReturn("test@example.com");
        given(notificationGateway.send("test@example.com", "[청년복지] 맞춤 정책 추천", "body"))
                .willReturn(false);

        notificationRetryService.retryFailedNotifications();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getRetryCount()).isEqualTo(2);
        assertThat(notification.getNextRetryAt()).isNull();
        assertThat(notification.getErrorMessage()).isEqualTo("notification gateway returned false");
        org.mockito.Mockito.verify(notificationRetryCommandService).save(notification);
    }

    private Notification sampleFailedNotification(int retryCount) {
        return Notification.builder()
                .userKey("user-key-1")
                .channel(NotificationChannel.EMAIL)
                .periodType(NotificationPeriodType.DAILY)
                .status(NotificationStatus.FAILED)
                .subject("[청년복지] 맞춤 정책 추천")
                .messageText("body")
                .retryCount(retryCount)
                .nextRetryAt(LocalDateTime.now().minusMinutes(1))
                .errorMessage("temporary failure")
                .build();
    }
}
