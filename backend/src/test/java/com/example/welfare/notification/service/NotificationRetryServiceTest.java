package com.example.welfare.notification.service;

import com.example.welfare.global.util.RedisKeyHash;
import com.example.welfare.notification.entity.Notification;
import com.example.welfare.notification.entity.Notification.NotificationChannel;
import com.example.welfare.notification.entity.Notification.NotificationPeriodType;
import com.example.welfare.notification.entity.Notification.NotificationStatus;
import com.example.welfare.notification.gateway.NotificationGateway;
import com.example.welfare.user.service.UserNotificationReadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
    @Mock
    private NotificationAttemptLogService notificationAttemptLogService;

    @InjectMocks
    private NotificationRetryService notificationRetryService;

    @Test
    @DisplayName("재시도 발송이 성공하면 상태를 SENT로 전환한다")
    void retryFailedNotificationsMarksSentOnSuccess() {
        Notification notification = sampleFailedNotification(1);

        given(notificationRetryReadService.findRetryableFailedNotificationIds(any(LocalDateTime.class)))
                .willReturn(List.of(1L));
        given(notificationRetryCommandService.claimForRetry(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(true);
        given(notificationRetryReadService.findById(1L)).willReturn(java.util.Optional.of(notification));
        given(userNotificationReadService.getNotificationEmailByUserKey("user-key-1")).willReturn("test@example.com");
        given(notificationGateway.send("test@example.com", "[청년복지] 맞춤 정책 추천", "body"))
                .willReturn(true);

        NotificationRetryService.RetryRunResult result = notificationRetryService.retryFailedNotifications();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isNotNull();
        assertThat(notification.getNextRetryAt()).isNull();
        assertThat(notification.getErrorMessage()).isNull();
        assertThat(result.sentCount()).isEqualTo(1);
        assertThat(result.terminalFailureCount()).isZero();
        org.mockito.Mockito.verify(notificationRetryCommandService).save(notification);
        assertRetryAttempt("sent", null);
    }

    @Test
    @DisplayName("첫 재시도도 실패하면 2시간 뒤로 다시 예약한다")
    void retryFailedNotificationsSchedulesTwoHourDelayAfterFirstRetryFailure() {
        Notification notification = sampleFailedNotification(0);

        given(notificationRetryReadService.findRetryableFailedNotificationIds(any(LocalDateTime.class)))
                .willReturn(List.of(1L));
        given(notificationRetryCommandService.claimForRetry(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(true);
        given(notificationRetryReadService.findById(1L)).willReturn(java.util.Optional.of(notification));
        given(userNotificationReadService.getNotificationEmailByUserKey("user-key-1")).willReturn("test@example.com");
        given(notificationGateway.send("test@example.com", "[청년복지] 맞춤 정책 추천", "body"))
                .willReturn(false);

        LocalDateTime before = LocalDateTime.now();
        NotificationRetryService.RetryRunResult result = notificationRetryService.retryFailedNotifications();
        LocalDateTime after = LocalDateTime.now();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getRetryCount()).isEqualTo(1);
        assertThat(notification.getErrorMessage()).isEqualTo("notification gateway returned false");
        assertThat(notification.getNextRetryAt()).isBetween(before.plusMinutes(120), after.plusMinutes(120));
        assertThat(result.rescheduledCount()).isEqualTo(1);
        org.mockito.Mockito.verify(notificationRetryCommandService).save(notification);
        assertRetryAttempt("rescheduled", "gateway_false");
    }

    @Test
    @DisplayName("최대 재시도 직전 실패하면 더 이상 다음 재시도를 예약하지 않는다")
    void retryFailedNotificationsStopsSchedulingAfterMaxRetry() {
        Notification notification = sampleFailedNotification(1);

        given(notificationRetryReadService.findRetryableFailedNotificationIds(any(LocalDateTime.class)))
                .willReturn(List.of(1L));
        given(notificationRetryCommandService.claimForRetry(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(true);
        given(notificationRetryReadService.findById(1L)).willReturn(java.util.Optional.of(notification));
        given(userNotificationReadService.getNotificationEmailByUserKey("user-key-1")).willReturn("test@example.com");
        given(notificationGateway.send("test@example.com", "[청년복지] 맞춤 정책 추천", "body"))
                .willReturn(false);

        NotificationRetryService.RetryRunResult result = notificationRetryService.retryFailedNotifications();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getRetryCount()).isEqualTo(2);
        assertThat(notification.getNextRetryAt()).isNull();
        assertThat(notification.getErrorMessage()).isEqualTo("notification gateway returned false");
        assertThat(result.terminalFailureCount()).isEqualTo(1);
        org.mockito.Mockito.verify(notificationRetryCommandService).save(notification);
        assertRetryAttempt("terminal_failed", "gateway_false");
    }

    @Test
    @DisplayName("재시도 예외 메시지의 secret-like 값은 저장 전에 마스킹한다")
    void retryFailedNotificationsRedactsSecretLikeExceptionMessage() {
        Notification notification = sampleFailedNotification(0);

        given(notificationRetryReadService.findRetryableFailedNotificationIds(any(LocalDateTime.class)))
                .willReturn(List.of(1L));
        given(notificationRetryCommandService.claimForRetry(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(true);
        given(notificationRetryReadService.findById(1L)).willReturn(java.util.Optional.of(notification));
        given(userNotificationReadService.getNotificationEmailByUserKey("user-key-1")).willReturn("test@example.com");
        given(notificationGateway.send("test@example.com", "[청년복지] 맞춤 정책 추천", "body"))
                .willThrow(new RuntimeException(
                        "smtp failed token=raw-token password=raw-password url=jdbc:postgresql://app:db-secret@db.example/app"
                ));

        notificationRetryService.retryFailedNotifications();

        assertThat(notification.getErrorMessage())
                .contains("notification retry failed (RuntimeException)")
                .contains("token=<redacted>")
                .contains("password=<redacted>")
                .contains("jdbc:postgresql://<redacted>@db.example")
                .doesNotContain("raw-token")
                .doesNotContain("raw-password")
                .doesNotContain("db-secret");
        org.mockito.Mockito.verify(notificationRetryCommandService).save(notification);
        assertRetryAttempt("rescheduled", "RuntimeException");
    }

    @Test
    @DisplayName("retry claim 에 실패한 row 는 발송하지 않는다")
    void retryFailedNotificationsSkipsWhenClaimFails() {
        given(notificationRetryReadService.findRetryableFailedNotificationIds(any(LocalDateTime.class)))
                .willReturn(List.of(1L));
        given(notificationRetryCommandService.claimForRetry(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(false);

        NotificationRetryService.RetryRunResult result = notificationRetryService.retryFailedNotifications();

        verify(notificationGateway, never()).send(any(), any(), any());
        verify(notificationRetryCommandService, never()).save(any());
        verify(notificationAttemptLogService, never()).record(any());
        assertThat(result.skippedClaimCount()).isEqualTo(1);
    }

    private void assertRetryAttempt(String outcome, String errorType) {
        ArgumentCaptor<NotificationAttemptLogCommand> captor =
                ArgumentCaptor.forClass(NotificationAttemptLogCommand.class);
        verify(notificationAttemptLogService).record(captor.capture());

        NotificationAttemptLogCommand command = captor.getValue();
        assertThat(command.userKeyHash()).isEqualTo(RedisKeyHash.sha256Hex("user-key-1"));
        assertThat(command.channel()).isEqualTo("email");
        assertThat(command.kind()).isEqualTo("notification_retry");
        assertThat(command.outcome()).isEqualTo(outcome);
        assertThat(command.itemCount()).isEqualTo(0);
        assertThat(command.endpointHost()).isNull();
        assertThat(command.errorType()).isEqualTo(errorType);
        assertThat(command.durationMs()).isGreaterThanOrEqualTo(0);
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
