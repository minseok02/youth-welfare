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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeadlineReminderDispatchServiceTest {

    @Mock
    private ActiveUserReadService activeUserReadService;
    @Mock
    private RecommendationBookmarkReadService recommendationBookmarkReadService;
    @Mock
    private NotificationMessageService notificationMessageService;
    @Mock
    private NotificationGateway notificationGateway;
    @Mock
    private NotificationHistoryService notificationHistoryService;
    @Mock
    private WebPushDispatchService webPushDispatchService;
    @Mock
    private NotificationAttemptLogService notificationAttemptLogService;

    @InjectMocks
    private DeadlineReminderDispatchService deadlineReminderDispatchService;

    @Test
    @DisplayName("마감 임박 북마크가 없으면 발송하지 않는다")
    void sendBookmarkedDeadlineReminderSkipsWhenNoCandidates() {
        NotificationTarget target = new NotificationTarget(1L, "user-key-1", "test@example.com",
                User.NotificationPeriod.NONE, true, true, true, 0.0, 10);
        given(activeUserReadService.getActiveUserByUserKey("user-key-1")).willReturn(sampleUser());
        given(recommendationBookmarkReadService.findLatestBookmarkedRecommendations("user-key-1"))
                .willReturn(List.of(sampleRecommendation(11L, "만료 정책", LocalDate.now().minusDays(1))));

        DeadlineReminderDispatchService.DeadlineReminderDispatchResult result =
                deadlineReminderDispatchService.sendBookmarkedDeadlineReminder(target, 3);

        assertThat(result.status()).isEqualTo(DeadlineReminderDispatchService.DeadlineReminderDispatchStatus.NO_CANDIDATES);
        verify(notificationHistoryService, never()).reserveDispatch(any(), any(), any(), any(), any());
        verify(notificationGateway, never()).send(any(), any(), any());
        verify(webPushDispatchService, never()).sendDeadlineReminder(any(), any(), anyInt());
    }

    @Test
    @DisplayName("이메일 채널이 꺼져 있으면 게이트웨이를 건너뛰고 인앱만 저장한다")
    void sendBookmarkedDeadlineReminderSkipsEmailWhenEmailDisabled() {
        NotificationTarget target = new NotificationTarget(1L, "user-key-1", "test@example.com",
                User.NotificationPeriod.NONE, false, true, false, 0.0, 10);
        User user = sampleUser();
        WelfareService service = sampleService(11L, "청년 월세 지원", LocalDate.now().plusDays(2));
        Notification notification = Notification.builder()
                .id(1L)
                .userKey("user-key-1")
                .dispatchKey("deadline:user-key-1:" + LocalDate.now() + ":3")
                .periodType(NotificationPeriodType.MANUAL)
                .channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.PENDING)
                .subject("[청년복지] 북마크 정책 마감 임박 알림")
                .build();

        given(activeUserReadService.getActiveUserByUserKey("user-key-1")).willReturn(user);
        given(recommendationBookmarkReadService.findLatestBookmarkedRecommendations("user-key-1"))
                .willReturn(List.of(sampleRecommendation(service)));
        given(notificationHistoryService.reserveDispatch(
                eq(user),
                eq(NotificationPeriodType.MANUAL),
                eq(NotificationChannel.EMAIL),
                eq("deadline:user-key-1:" + LocalDate.now() + ":3"),
                eq("[청년복지] 북마크 정책 마감 임박 알림")
        )).willReturn(Optional.of(notification));
        given(notificationMessageService.buildDeadlineReminderMessage("user-key-1", 1L, List.of(service), 3))
                .willReturn("deadline-body");

        DeadlineReminderDispatchService.DeadlineReminderDispatchResult result =
                deadlineReminderDispatchService.sendBookmarkedDeadlineReminder(target, 3);

        assertThat(result.status()).isEqualTo(DeadlineReminderDispatchService.DeadlineReminderDispatchStatus.SENT);
        verify(notificationGateway, never()).send(any(), any(), any());
        verify(notificationHistoryService).saveDeadlineReminderResult(
                eq(notification),
                eq(NotificationStatus.SENT),
                eq("deadline-body"),
                eq(List.of(service)),
                eq(null),
                eq(true),
                eq(3)
        );
        verify(webPushDispatchService, never()).sendDeadlineReminder(any(), any(), anyInt());
    }

    @Test
    @DisplayName("웹푸시 fan-out 이 실패해도 이미 성공한 deadline 이력을 실패로 되돌리지 않는다")
    void sendBookmarkedDeadlineReminderDoesNotFailDispatchWhenWebPushFanOutThrows() {
        NotificationTarget target = new NotificationTarget(1L, "user-key-1", "test@example.com",
                User.NotificationPeriod.NONE, true, true, true, 0.0, 10);
        User user = sampleUser();
        WelfareService service = sampleService(11L, "청년 월세 지원", LocalDate.now().plusDays(2));
        Notification notification = Notification.builder()
                .id(1L)
                .userKey("user-key-1")
                .dispatchKey("deadline:user-key-1:" + LocalDate.now() + ":3")
                .periodType(NotificationPeriodType.MANUAL)
                .channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.PENDING)
                .subject("[청년복지] 북마크 정책 마감 임박 알림")
                .build();

        given(activeUserReadService.getActiveUserByUserKey("user-key-1")).willReturn(user);
        given(recommendationBookmarkReadService.findLatestBookmarkedRecommendations("user-key-1"))
                .willReturn(List.of(sampleRecommendation(service)));
        given(notificationHistoryService.reserveDispatch(
                eq(user),
                eq(NotificationPeriodType.MANUAL),
                eq(NotificationChannel.EMAIL),
                eq("deadline:user-key-1:" + LocalDate.now() + ":3"),
                eq("[청년복지] 북마크 정책 마감 임박 알림")
        )).willReturn(Optional.of(notification));
        given(notificationMessageService.buildDeadlineReminderMessage("user-key-1", 1L, List.of(service), 3))
                .willReturn("deadline-body");
        given(notificationGateway.send("test@example.com", "[청년복지] 북마크 정책 마감 임박 알림", "deadline-body"))
                .willReturn(true);
        doThrow(new IllegalStateException("push unavailable"))
                .when(webPushDispatchService)
                .sendDeadlineReminder("user-key-1", List.of(service), 3);

        DeadlineReminderDispatchService.DeadlineReminderDispatchResult result =
                deadlineReminderDispatchService.sendBookmarkedDeadlineReminder(target, 3);

        assertThat(result.status()).isEqualTo(DeadlineReminderDispatchService.DeadlineReminderDispatchStatus.SENT);
        verify(notificationHistoryService, times(1)).saveDeadlineReminderResult(
                eq(notification),
                eq(NotificationStatus.SENT),
                eq("deadline-body"),
                eq(List.of(service)),
                eq(null),
                eq(true),
                eq(3)
        );
    }

    private User sampleUser() {
        return User.builder()
                .id(1L)
                .userKey("user-key-1")
                .email("test@example.com")
                .passwordHash("pw")
                .build();
    }

    private UserRecommendation sampleRecommendation(long id, String title, LocalDate applyEndDate) {
        return sampleRecommendation(sampleService(id, title, applyEndDate));
    }

    private UserRecommendation sampleRecommendation(WelfareService service) {
        return UserRecommendation.builder()
                .service(service)
                .build();
    }

    private WelfareService sampleService(long id, String title, LocalDate applyEndDate) {
        return WelfareService.builder()
                .id(id)
                .title(title)
                .applyEndDate(applyEndDate)
                .build();
    }
}
