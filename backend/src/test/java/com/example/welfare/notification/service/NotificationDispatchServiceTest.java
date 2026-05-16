package com.example.welfare.notification.service;

import com.example.welfare.notification.dto.NotificationTarget;
import com.example.welfare.notification.entity.Notification;
import com.example.welfare.notification.entity.Notification.NotificationChannel;
import com.example.welfare.notification.entity.Notification.NotificationPeriodType;
import com.example.welfare.notification.entity.Notification.NotificationStatus;
import com.example.welfare.notification.gateway.NotificationGateway;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.entity.RecommendationLog;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchServiceTest {

    @Mock
    private NotificationRecommendationService notificationRecommendationService;
    @Mock
    private NotificationDispatchWindowReadService notificationDispatchWindowReadService;
    @Mock
    private NotificationMessageService notificationMessageService;
    @Mock
    private NotificationGateway notificationGateway;
    @Mock
    private NotificationHistoryService notificationHistoryService;
    @Mock
    private WebPushDispatchService webPushDispatchService;

    @InjectMocks
    private NotificationDispatchService notificationDispatchService;

    @Test
    @DisplayName("dispatch 는 추천 계획이 없으면 발송하지 않는다")
    void sendTopRecommendationsSkipsWhenPlanMissing() {
        NotificationTarget target = new NotificationTarget(1L, "user-key-1", "test@example.com",
                User.NotificationPeriod.DAILY, true, true, true, 0.8, 10);
        given(notificationDispatchWindowReadService.hasDispatchHistoryInCurrentWindow("user-key-1", NotificationPeriodType.DAILY))
                .willReturn(false);
        given(notificationRecommendationService.prepareDispatch(target)).willReturn(Optional.empty());

        notificationDispatchService.sendTopRecommendations(target);

        verify(notificationGateway, never()).send(any(), any(), any());
        verify(notificationHistoryService, never()).reserveDispatch(any(), any(), any(), any(), any());
        verify(notificationHistoryService, never()).saveResult(any(), any(), any(), any(), any(), any(), anyBoolean());
        verify(webPushDispatchService, never()).sendRecommendationDigest(any(), any());
    }

    @Test
    @DisplayName("게이트웨이가 false를 반환하면 실패 이력을 저장한다")
    void sendTopRecommendationsStoresFailedHistoryWhenGatewayReturnsFalse() {
        User user = sampleUser();
        UserRecommendation recommendation = sampleRecommendation();
        RecommendationLog log = RecommendationLog.builder().id(100L).build();
        NotificationTarget target = new NotificationTarget(1L, "user-key-1", "test@example.com",
                User.NotificationPeriod.DAILY, true, true, true, 0.8, 10);
        NotificationRecommendationService.NotificationDispatchPlan plan =
                new NotificationRecommendationService.NotificationDispatchPlan(
                        user,
                        NotificationPeriodType.DAILY,
                        List.of(recommendation),
                        List.of(log)
                );

        given(notificationDispatchWindowReadService.hasDispatchHistoryInCurrentWindow("user-key-1", NotificationPeriodType.DAILY))
                .willReturn(false);
        given(notificationDispatchWindowReadService.currentWindow(NotificationPeriodType.DAILY, LocalDate.now()))
                .willReturn(new NotificationDispatchWindowReadService.Window(
                        LocalDate.now().atStartOfDay(),
                        LocalDate.now().plusDays(1).atStartOfDay()
                ));
        given(notificationRecommendationService.prepareDispatch(target)).willReturn(Optional.of(plan));
        given(notificationHistoryService.reserveDispatch(
                eq(user),
                eq(NotificationPeriodType.DAILY),
                eq(NotificationChannel.EMAIL),
                eq("daily:user-key-1:" + java.time.LocalDate.now()),
                eq("[청년복지] 맞춤 정책 추천")
        )).willReturn(Optional.of(Notification.builder()
                .id(1L)
                .userKey("user-key-1")
                .dispatchKey("daily:user-key-1:" + java.time.LocalDate.now())
                .periodType(NotificationPeriodType.DAILY)
                .channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.PENDING)
                .subject("[청년복지] 맞춤 정책 추천")
                .build()));
        given(notificationMessageService.buildRecommendationMessage("user-key-1", 1L, List.of(recommendation), List.of(log)))
                .willReturn("body");
        given(notificationGateway.send("test@example.com", "[청년복지] 맞춤 정책 추천", "body")).willReturn(false);

        notificationDispatchService.sendTopRecommendations(target);

        verify(notificationHistoryService).saveResult(
                any(Notification.class),
                eq(NotificationStatus.FAILED),
                eq("body"),
                eq(List.of(recommendation)),
                eq(List.of(log)),
                eq("notification gateway returned false"),
                eq(true)
        );
        verify(webPushDispatchService).sendRecommendationDigest("user-key-1", List.of(recommendation));
    }

    @Test
    @DisplayName("현재 dispatch window 에 이미 이력이 있으면 중복 발송을 건너뛴다")
    void sendTopRecommendationsSkipsWhenWindowAlreadyDispatched() {
        NotificationTarget target = new NotificationTarget(1L, "user-key-1", "test@example.com",
                User.NotificationPeriod.DAILY, true, true, true, 0.8, 10);
        given(notificationDispatchWindowReadService.hasDispatchHistoryInCurrentWindow("user-key-1", NotificationPeriodType.DAILY))
                .willReturn(true);

        notificationDispatchService.sendTopRecommendations(target);

        verify(notificationRecommendationService, never()).prepareDispatch(any());
        verify(notificationGateway, never()).send(any(), any(), any());
        verify(notificationHistoryService, never()).reserveDispatch(any(), any(), any(), any(), any());
        verify(notificationHistoryService, never()).saveResult(any(), any(), any(), any(), any(), any(), anyBoolean());
        verify(webPushDispatchService, never()).sendRecommendationDigest(any(), any());
    }

    @Test
    @DisplayName("reservation 충돌이면 중복 발송을 건너뛴다")
    void sendTopRecommendationsSkipsWhenReservationConflicts() {
        User user = sampleUser();
        UserRecommendation recommendation = sampleRecommendation();
        RecommendationLog log = RecommendationLog.builder().id(100L).build();
        NotificationTarget target = new NotificationTarget(1L, "user-key-1", "test@example.com",
                User.NotificationPeriod.DAILY, true, true, true, 0.8, 10);
        NotificationRecommendationService.NotificationDispatchPlan plan =
                new NotificationRecommendationService.NotificationDispatchPlan(
                        user,
                        NotificationPeriodType.DAILY,
                        List.of(recommendation),
                        List.of(log)
                );
        given(notificationDispatchWindowReadService.hasDispatchHistoryInCurrentWindow("user-key-1", NotificationPeriodType.DAILY))
                .willReturn(false);
        given(notificationDispatchWindowReadService.currentWindow(NotificationPeriodType.DAILY, LocalDate.now()))
                .willReturn(new NotificationDispatchWindowReadService.Window(
                        LocalDate.now().atStartOfDay(),
                        LocalDate.now().plusDays(1).atStartOfDay()
                ));
        given(notificationRecommendationService.prepareDispatch(target)).willReturn(Optional.of(plan));
        given(notificationHistoryService.reserveDispatch(any(), any(), any(), any(), any()))
                .willReturn(Optional.empty());

        notificationDispatchService.sendTopRecommendations(target);

        verify(notificationGateway, never()).send(any(), any(), any());
        verify(notificationHistoryService, never()).saveResult(any(), any(), any(), any(), any(), any(), anyBoolean());
        verify(webPushDispatchService, never()).sendRecommendationDigest(any(), any());
    }

    @Test
    @DisplayName("이메일 채널이 꺼져 있으면 게이트웨이를 건너뛰고 다른 채널만 처리한다")
    void sendTopRecommendationsSkipsEmailWhenEmailChannelDisabled() {
        User user = sampleUser();
        UserRecommendation recommendation = sampleRecommendation();
        RecommendationLog log = RecommendationLog.builder().id(100L).build();
        NotificationTarget target = new NotificationTarget(1L, "user-key-1", "test@example.com",
                User.NotificationPeriod.DAILY, false, true, true, 0.8, 10);
        NotificationRecommendationService.NotificationDispatchPlan plan =
                new NotificationRecommendationService.NotificationDispatchPlan(
                        user,
                        NotificationPeriodType.DAILY,
                        List.of(recommendation),
                        List.of(log)
                );

        given(notificationDispatchWindowReadService.hasDispatchHistoryInCurrentWindow("user-key-1", NotificationPeriodType.DAILY))
                .willReturn(false);
        given(notificationDispatchWindowReadService.currentWindow(NotificationPeriodType.DAILY, LocalDate.now()))
                .willReturn(new NotificationDispatchWindowReadService.Window(
                        LocalDate.now().atStartOfDay(),
                        LocalDate.now().plusDays(1).atStartOfDay()
                ));
        given(notificationRecommendationService.prepareDispatch(target)).willReturn(Optional.of(plan));
        given(notificationHistoryService.reserveDispatch(any(), any(), any(), any(), any()))
                .willReturn(Optional.of(Notification.builder()
                        .id(1L)
                        .userKey("user-key-1")
                        .dispatchKey("daily:user-key-1:" + java.time.LocalDate.now())
                        .periodType(NotificationPeriodType.DAILY)
                        .channel(NotificationChannel.EMAIL)
                        .status(NotificationStatus.PENDING)
                        .subject("[청년복지] 맞춤 정책 추천")
                        .build()));
        given(notificationMessageService.buildRecommendationMessage("user-key-1", 1L, List.of(recommendation), List.of(log)))
                .willReturn("body");

        NotificationDispatchService.NotificationDispatchResult result =
                notificationDispatchService.sendTopRecommendations(target);

        assertThat(result.status()).isEqualTo(NotificationDispatchService.NotificationDispatchStatus.SENT);
        verify(notificationGateway, never()).send(any(), any(), any());
        verify(notificationHistoryService).saveResult(
                any(Notification.class),
                eq(NotificationStatus.SENT),
                eq("body"),
                eq(List.of(recommendation)),
                eq(List.of(log)),
                eq(null),
                eq(true)
        );
        verify(webPushDispatchService).sendRecommendationDigest("user-key-1", List.of(recommendation));
    }

    @Test
    @DisplayName("인앱 채널이 꺼져 있으면 user_alerts 를 만들지 않는다")
    void sendTopRecommendationsSkipsInAppWhenDisabled() {
        User user = sampleUser();
        UserRecommendation recommendation = sampleRecommendation();
        RecommendationLog log = RecommendationLog.builder().id(100L).build();
        NotificationTarget target = new NotificationTarget(1L, "user-key-1", "test@example.com",
                User.NotificationPeriod.DAILY, true, false, false, 0.8, 10);
        NotificationRecommendationService.NotificationDispatchPlan plan =
                new NotificationRecommendationService.NotificationDispatchPlan(
                        user,
                        NotificationPeriodType.DAILY,
                        List.of(recommendation),
                        List.of(log)
                );

        given(notificationDispatchWindowReadService.hasDispatchHistoryInCurrentWindow("user-key-1", NotificationPeriodType.DAILY))
                .willReturn(false);
        given(notificationDispatchWindowReadService.currentWindow(NotificationPeriodType.DAILY, LocalDate.now()))
                .willReturn(new NotificationDispatchWindowReadService.Window(
                        LocalDate.now().atStartOfDay(),
                        LocalDate.now().plusDays(1).atStartOfDay()
                ));
        given(notificationRecommendationService.prepareDispatch(target)).willReturn(Optional.of(plan));
        given(notificationHistoryService.reserveDispatch(any(), any(), any(), any(), any()))
                .willReturn(Optional.of(Notification.builder()
                        .id(1L)
                        .userKey("user-key-1")
                        .dispatchKey("daily:user-key-1:" + java.time.LocalDate.now())
                        .periodType(NotificationPeriodType.DAILY)
                        .channel(NotificationChannel.EMAIL)
                        .status(NotificationStatus.PENDING)
                        .subject("[청년복지] 맞춤 정책 추천")
                        .build()));
        given(notificationMessageService.buildRecommendationMessage("user-key-1", 1L, List.of(recommendation), List.of(log)))
                .willReturn("body");
        given(notificationGateway.send("test@example.com", "[청년복지] 맞춤 정책 추천", "body")).willReturn(true);

        notificationDispatchService.sendTopRecommendations(target);

        verify(notificationHistoryService).saveResult(
                any(Notification.class),
                eq(NotificationStatus.SENT),
                eq("body"),
                eq(List.of(recommendation)),
                eq(List.of(log)),
                eq(null),
                eq(false)
        );
        verify(webPushDispatchService, never()).sendRecommendationDigest(any(), any());
    }

    @Test
    @DisplayName("웹푸시 채널이 꺼져 있으면 웹푸시 fan-out을 건너뛴다")
    void sendTopRecommendationsSkipsWebPushWhenDisabled() {
        User user = sampleUser();
        UserRecommendation recommendation = sampleRecommendation();
        RecommendationLog log = RecommendationLog.builder().id(100L).build();
        NotificationTarget target = new NotificationTarget(1L, "user-key-1", "test@example.com",
                User.NotificationPeriod.DAILY, true, true, false, 0.8, 10);
        NotificationRecommendationService.NotificationDispatchPlan plan =
                new NotificationRecommendationService.NotificationDispatchPlan(
                        user,
                        NotificationPeriodType.DAILY,
                        List.of(recommendation),
                        List.of(log)
                );

        given(notificationDispatchWindowReadService.hasDispatchHistoryInCurrentWindow("user-key-1", NotificationPeriodType.DAILY))
                .willReturn(false);
        given(notificationDispatchWindowReadService.currentWindow(NotificationPeriodType.DAILY, LocalDate.now()))
                .willReturn(new NotificationDispatchWindowReadService.Window(
                        LocalDate.now().atStartOfDay(),
                        LocalDate.now().plusDays(1).atStartOfDay()
                ));
        given(notificationRecommendationService.prepareDispatch(target)).willReturn(Optional.of(plan));
        given(notificationHistoryService.reserveDispatch(any(), any(), any(), any(), any()))
                .willReturn(Optional.of(Notification.builder()
                        .id(1L)
                        .userKey("user-key-1")
                        .dispatchKey("daily:user-key-1:" + java.time.LocalDate.now())
                        .periodType(NotificationPeriodType.DAILY)
                        .channel(NotificationChannel.EMAIL)
                        .status(NotificationStatus.PENDING)
                        .subject("[청년복지] 맞춤 정책 추천")
                        .build()));
        given(notificationMessageService.buildRecommendationMessage("user-key-1", 1L, List.of(recommendation), List.of(log)))
                .willReturn("body");
        given(notificationGateway.send("test@example.com", "[청년복지] 맞춤 정책 추천", "body")).willReturn(true);

        notificationDispatchService.sendTopRecommendations(target);

        verify(webPushDispatchService, never()).sendRecommendationDigest(any(), any());
        verify(notificationHistoryService).saveResult(
                any(Notification.class),
                eq(NotificationStatus.SENT),
                eq("body"),
                eq(List.of(recommendation)),
                eq(List.of(log)),
                eq(null),
                eq(true)
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

    private UserRecommendation sampleRecommendation() {
        WelfareService service = WelfareService.builder()
                .id(11L)
                .title("청년 월세 지원")
                .build();
        return UserRecommendation.builder()
                .service(service)
                .finalScore(new BigDecimal("0.91"))
                .aiReason("주거비 부담 완화에 적합")
                .build();
    }
}
