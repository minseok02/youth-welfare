package com.example.welfare.notification.service;

import com.example.welfare.notification.dto.NotificationTarget;
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
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    @InjectMocks
    private NotificationDispatchService notificationDispatchService;

    @Test
    @DisplayName("dispatch 는 추천 계획이 없으면 발송하지 않는다")
    void sendTopRecommendationsSkipsWhenPlanMissing() {
        NotificationTarget target = new NotificationTarget(1L, "user-key-1", "test@example.com",
                User.NotificationPeriod.DAILY, 0.8, 10);
        given(notificationDispatchWindowReadService.hasDispatchHistoryInCurrentWindow("user-key-1", NotificationPeriodType.DAILY))
                .willReturn(false);
        given(notificationRecommendationService.prepareDispatch(target)).willReturn(Optional.empty());

        notificationDispatchService.sendTopRecommendations(target);

        verify(notificationGateway, never()).send(any(), any(), any());
        verify(notificationHistoryService, never()).saveResult(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("게이트웨이가 false를 반환하면 실패 이력을 저장한다")
    void sendTopRecommendationsStoresFailedHistoryWhenGatewayReturnsFalse() {
        User user = sampleUser();
        UserRecommendation recommendation = sampleRecommendation();
        RecommendationLog log = RecommendationLog.builder().id(100L).build();
        NotificationTarget target = new NotificationTarget(1L, "user-key-1", "test@example.com",
                User.NotificationPeriod.DAILY, 0.8, 10);
        NotificationRecommendationService.NotificationDispatchPlan plan =
                new NotificationRecommendationService.NotificationDispatchPlan(
                        user,
                        NotificationPeriodType.DAILY,
                        List.of(recommendation),
                        List.of(log)
                );

        given(notificationDispatchWindowReadService.hasDispatchHistoryInCurrentWindow("user-key-1", NotificationPeriodType.DAILY))
                .willReturn(false);
        given(notificationRecommendationService.prepareDispatch(target)).willReturn(Optional.of(plan));
        given(notificationMessageService.buildRecommendationMessage("user-key-1", 1L, List.of(recommendation), List.of(log)))
                .willReturn("body");
        given(notificationGateway.send("test@example.com", "[청년복지] 맞춤 정책 추천", "body")).willReturn(false);

        notificationDispatchService.sendTopRecommendations(target);

        verify(notificationHistoryService).saveResult(
                eq(user),
                eq(NotificationPeriodType.DAILY),
                eq(NotificationChannel.EMAIL),
                eq(NotificationStatus.FAILED),
                eq("[청년복지] 맞춤 정책 추천"),
                eq("body"),
                eq(List.of(recommendation)),
                eq(List.of(log)),
                eq("notification gateway returned false")
        );
    }

    @Test
    @DisplayName("현재 dispatch window 에 이미 이력이 있으면 중복 발송을 건너뛴다")
    void sendTopRecommendationsSkipsWhenWindowAlreadyDispatched() {
        NotificationTarget target = new NotificationTarget(1L, "user-key-1", "test@example.com",
                User.NotificationPeriod.DAILY, 0.8, 10);
        given(notificationDispatchWindowReadService.hasDispatchHistoryInCurrentWindow("user-key-1", NotificationPeriodType.DAILY))
                .willReturn(true);

        notificationDispatchService.sendTopRecommendations(target);

        verify(notificationRecommendationService, never()).prepareDispatch(any());
        verify(notificationGateway, never()).send(any(), any(), any());
        verify(notificationHistoryService, never()).saveResult(any(), any(), any(), any(), any(), any(), any(), any(), any());
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
