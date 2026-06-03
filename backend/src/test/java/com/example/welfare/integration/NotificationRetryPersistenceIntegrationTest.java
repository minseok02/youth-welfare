package com.example.welfare.integration;

import com.example.welfare.notification.entity.Notification;
import com.example.welfare.notification.entity.Notification.NotificationChannel;
import com.example.welfare.notification.entity.Notification.NotificationPeriodType;
import com.example.welfare.notification.entity.Notification.NotificationStatus;
import com.example.welfare.notification.gateway.NotificationGateway;
import com.example.welfare.notification.repository.NotificationRepository;
import com.example.welfare.notification.service.NotificationRetryService;
import com.example.welfare.user.service.UserNotificationReadService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("integration")
class NotificationRetryPersistenceIntegrationTest {

    private static final String TEST_SUBJECT_PREFIX = "[it-notification-retry] ";

    @Autowired
    private NotificationRetryService notificationRetryService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private NotificationGateway notificationGateway;

    @MockitoBean
    private UserNotificationReadService userNotificationReadService;

    @BeforeEach
    void setup() {
        cleanup();
    }

    @AfterEach
    void teardown() {
        cleanup();
    }

    @Test
    @DisplayName("retry 성공 후 notifications row 는 SENT 상태로 실제 저장된다")
    void retrySuccessPersistsSentState() {
        Notification notification = notificationRepository.save(Notification.builder()
                .userKey(randomUserKey())
                .channel(NotificationChannel.EMAIL)
                .periodType(NotificationPeriodType.DAILY)
                .status(NotificationStatus.FAILED)
                .subject(TEST_SUBJECT_PREFIX + "success")
                .messageText("retry body")
                .retryCount(1)
                .nextRetryAt(LocalDateTime.now().minusMinutes(5))
                .errorMessage("temporary failure")
                .build());

        given(userNotificationReadService.getNotificationEmailByUserKey(notification.getUserKey()))
                .willReturn("retry-success@example.com");
        given(notificationGateway.send("retry-success@example.com", notification.getSubject(), "retry body"))
                .willReturn(true);

        NotificationRetryService.RetryRunResult result = notificationRetryService.retryFailedNotifications();

        Notification reloaded = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(reloaded.getSentAt()).isNotNull();
        assertThat(reloaded.getNextRetryAt()).isNull();
        assertThat(reloaded.getErrorMessage()).isNull();
        assertThat(reloaded.getRetryCount()).isEqualTo(1);
        assertThat(result.sentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("retry 실패 후 notifications row 는 retryCount 와 nextRetryAt 이 실제 저장된다")
    void retryFailurePersistsNextRetrySchedule() {
        Notification notification = notificationRepository.save(Notification.builder()
                .userKey(randomUserKey())
                .channel(NotificationChannel.EMAIL)
                .periodType(NotificationPeriodType.WEEKLY)
                .status(NotificationStatus.FAILED)
                .subject(TEST_SUBJECT_PREFIX + "failure")
                .messageText("retry body")
                .retryCount(0)
                .nextRetryAt(LocalDateTime.now().minusMinutes(5))
                .errorMessage("temporary failure")
                .build());

        given(userNotificationReadService.getNotificationEmailByUserKey(notification.getUserKey()))
                .willReturn("retry-failure@example.com");
        given(notificationGateway.send("retry-failure@example.com", notification.getSubject(), "retry body"))
                .willReturn(false);

        LocalDateTime before = LocalDateTime.now();
        NotificationRetryService.RetryRunResult result = notificationRetryService.retryFailedNotifications();
        LocalDateTime after = LocalDateTime.now();

        Notification reloaded = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(reloaded.getRetryCount()).isEqualTo(1);
        assertThat(reloaded.getErrorMessage()).isEqualTo("notification gateway returned false");
        assertThat(reloaded.getNextRetryAt()).isBetween(
                before.plusMinutes(120).minusSeconds(1),
                after.plusMinutes(120).plusSeconds(1)
        );
        assertThat(reloaded.getSentAt()).isNull();
        assertThat(result.rescheduledCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("최대 재시도 직전 실패 후 notifications row 는 더 이상 nextRetryAt 을 남기지 않는다")
    void retryFailureAtMaxPersistsTerminalFailure() {
        Notification notification = notificationRepository.save(Notification.builder()
                .userKey(randomUserKey())
                .channel(NotificationChannel.EMAIL)
                .periodType(NotificationPeriodType.DAILY)
                .status(NotificationStatus.FAILED)
                .subject(TEST_SUBJECT_PREFIX + "terminal")
                .messageText("retry body")
                .retryCount(1)
                .nextRetryAt(LocalDateTime.now().minusMinutes(5))
                .errorMessage("temporary failure")
                .build());

        given(userNotificationReadService.getNotificationEmailByUserKey(notification.getUserKey()))
                .willReturn("retry-terminal@example.com");
        given(notificationGateway.send("retry-terminal@example.com", notification.getSubject(), "retry body"))
                .willReturn(false);

        NotificationRetryService.RetryRunResult result = notificationRetryService.retryFailedNotifications();

        Notification reloaded = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(reloaded.getRetryCount()).isEqualTo(2);
        assertThat(reloaded.getNextRetryAt()).isNull();
        assertThat(reloaded.getErrorMessage()).isEqualTo("notification gateway returned false");
        assertThat(result.terminalFailureCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("retry 대상은 먼저 claim 되어 즉시 재실행에서 중복 발송되지 않는다")
    void retryClaimPreventsImmediateDuplicateRetry() {
        Notification notification = notificationRepository.save(Notification.builder()
                .userKey(randomUserKey())
                .channel(NotificationChannel.EMAIL)
                .periodType(NotificationPeriodType.DAILY)
                .status(NotificationStatus.FAILED)
                .subject(TEST_SUBJECT_PREFIX + "claim")
                .messageText("retry body")
                .retryCount(0)
                .nextRetryAt(LocalDateTime.now().minusMinutes(5))
                .errorMessage("temporary failure")
                .build());

        given(userNotificationReadService.getNotificationEmailByUserKey(notification.getUserKey()))
                .willThrow(new IllegalStateException("lookup failed after claim"));

        NotificationRetryService.RetryRunResult result = notificationRetryService.retryFailedNotifications();

        Notification reloaded = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(reloaded.getRetryCount()).isEqualTo(1);
        assertThat(reloaded.getNextRetryAt()).isAfter(LocalDateTime.now().plusMinutes(100));
        assertThat(reloaded.getErrorMessage()).contains("lookup failed after claim");
        assertThat(result.rescheduledCount()).isEqualTo(1);
    }

    private void cleanup() {
        jdbcTemplate.update("delete from notification_services where notification_id in (select id from notifications where subject like ?)",
                TEST_SUBJECT_PREFIX + "%");
        jdbcTemplate.update("delete from notifications where subject like ?", TEST_SUBJECT_PREFIX + "%");
    }

    private String randomUserKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
