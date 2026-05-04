package com.example.welfare.notification.service;

import com.example.welfare.notification.dto.NotificationTarget;
import com.example.welfare.notification.entity.Notification.NotificationPeriodType;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.entity.RecommendationLog;
import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.service.RecommendationAccessService;
import com.example.welfare.recommend.service.RecommendationLogService;
import com.example.welfare.recommend.service.ScoreWeightService;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.service.ActiveUserReadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class NotificationRecommendationServiceTest {

    @Mock
    private ActiveUserReadService activeUserReadService;
    @Mock
    private RecommendationAccessService recommendationAccessService;
    @Mock
    private RecommendationLogService recommendationLogService;
    @Mock
    private ScoreWeightService scoreWeightService;
    @Mock
    private NotificationSlotSelector notificationSlotSelector;

    @InjectMocks
    private NotificationRecommendationService notificationRecommendationService;

    @Test
    @DisplayName("추천 준비는 active user 조회, recommendation pool 로드, 슬롯 선택, 알림 로그 생성까지 수행한다")
    void prepareDispatchBuildsPlan() {
        User user = sampleUser();
        UserRecommendation a1 = sampleRecommendation("A1", "A1 reason", "0.95");
        UserRecommendation a2 = sampleRecommendation("A2", "A2 reason", "0.90");
        ScoreWeight weight = ScoreWeight.builder()
                .ruleWeight(new BigDecimal("0.8"))
                .aiWeight(new BigDecimal("0.2"))
                .build();
        RecommendationLog log1 = RecommendationLog.builder().id(100L).build();
        RecommendationLog log2 = RecommendationLog.builder().id(101L).build();
        NotificationTarget target = new NotificationTarget(1L, "user-key-1", "test@example.com",
                User.NotificationPeriod.DAILY, 0.8, 10);

        given(activeUserReadService.getActiveUserByUserKey("user-key-1")).willReturn(user);
        given(recommendationAccessService.getRecommendations(1L, 50)).willReturn(List.of(a1, a2));
        given(notificationSlotSelector.selectCandidates(List.of(a1, a2), 0.8)).willReturn(List.of(a1, a2));
        given(scoreWeightService.getActiveWeight()).willReturn(weight);
        given(recommendationLogService.logNotification(eq(user), any(), eq(weight))).willReturn(List.of(log1, log2));

        NotificationRecommendationService.NotificationDispatchPlan plan =
                notificationRecommendationService.prepareDispatch(target).orElseThrow();

        assertThat(plan.user()).isEqualTo(user);
        assertThat(plan.periodType()).isEqualTo(NotificationPeriodType.DAILY);
        assertThat(plan.recommendations()).containsExactly(a1, a2);
        assertThat(plan.logs()).containsExactly(log1, log2);
    }

    @Test
    @DisplayName("슬롯 선택 결과가 비면 empty를 반환하고 로그를 만들지 않는다")
    void prepareDispatchReturnsEmptyWhenNoCandidates() {
        User user = sampleUser();
        UserRecommendation fail = sampleRecommendation("A1", "A1 reason", "0.60");
        NotificationTarget target = new NotificationTarget(1L, "user-key-1", "test@example.com",
                User.NotificationPeriod.DAILY, 0.95, 10);

        given(activeUserReadService.getActiveUserByUserKey("user-key-1")).willReturn(user);
        given(recommendationAccessService.getRecommendations(1L, 50)).willReturn(List.of(fail));
        given(notificationSlotSelector.selectCandidates(List.of(fail), 0.95)).willReturn(List.of());

        assertThat(notificationRecommendationService.prepareDispatch(target)).isEmpty();
        verify(recommendationLogService, never()).logNotification(any(), any(), any());
    }

    private User sampleUser() {
        return User.builder()
                .id(1L)
                .userKey("user-key-1")
                .email("test@example.com")
                .passwordHash("pw")
                .build();
    }

    private UserRecommendation sampleRecommendation(String title, String aiReason, String finalScore) {
        WelfareService service = WelfareService.builder()
                .id((long) title.hashCode())
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("SRC-" + title)
                .title(title)
                .build();
        return UserRecommendation.builder()
                .service(service)
                .finalScore(new BigDecimal(finalScore))
                .aiReason(aiReason)
                .build();
    }
}
