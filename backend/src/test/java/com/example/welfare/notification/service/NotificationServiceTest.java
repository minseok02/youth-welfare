package com.example.welfare.notification.service;

import com.example.welfare.global.util.JwtUtil;
import com.example.welfare.notification.entity.Notification;
import com.example.welfare.notification.entity.Notification.NotificationChannel;
import com.example.welfare.notification.entity.Notification.NotificationPeriodType;
import com.example.welfare.notification.entity.Notification.NotificationStatus;
import com.example.welfare.notification.dto.NotificationTarget;
import com.example.welfare.notification.gateway.NotificationGateway;
import com.example.welfare.notification.repository.NotificationRepository;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.entity.RecommendationLog;
import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.facade.RecommendationFacade;
import com.example.welfare.recommend.service.RecommendationLogService;
import com.example.welfare.recommend.service.ScoreWeightService;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.service.UserReadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private UserReadService userReadService;
    @Mock
    private RecommendationFacade recommendationFacade;
    @Mock
    private RecommendationLogService logService;
    @Mock
    private ScoreWeightService scoreWeightService;
    @Mock
    private NotificationSlotSelector notificationSlotSelector;
    @Mock
    private NotificationGateway notificationGateway;
    @Mock
    private NotificationHistoryService notificationHistoryService;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationService, "appBaseUrl", "https://youth-welfare.kr");
    }

    @Test
    @DisplayName("알림 발송은 최소 점수 이상 추천만 보내고 ai_reason과 수신거부 링크를 본문에 포함한다")
    void sendTopRecommendationsFiltersByMinScoreAndIncludesReason() {
        User user = User.builder()
                .id(1L)
                .userKey("user-key-1")
                .email("test@example.com")
                .passwordHash("pw")
                .notificationYn(true)
                .notificationPeriod(User.NotificationPeriod.DAILY)
                .notificationMinScore(0.8)
                .displayCount(10)
                .build();
        WelfareService service = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("SRC-11")
                .title("청년 월세 지원")
                .build();
        UserRecommendation pass = UserRecommendation.builder()
                .service(service)
                .finalScore(new BigDecimal("0.91"))
                .aiReason("주거비 부담 완화에 적합")
                .build();
        UserRecommendation fail = UserRecommendation.builder()
                .service(service)
                .finalScore(new BigDecimal("0.60"))
                .aiReason("점수 미달")
                .build();
        ScoreWeight weight = ScoreWeight.builder()
                .ruleWeight(new BigDecimal("0.8"))
                .aiWeight(new BigDecimal("0.2"))
                .build();
        RecommendationLog log = RecommendationLog.builder().id(100L).build();
        NotificationTarget target = new NotificationTarget(1L, "user-key-1", "test@example.com",
                User.NotificationPeriod.DAILY, 0.8, 10);

        given(userReadService.getActiveUserByUserKey("user-key-1")).willReturn(user);
        given(recommendationFacade.getRecommendations(1L, 50)).willReturn(List.of(pass, fail));
        given(notificationSlotSelector.selectCandidates(List.of(pass, fail), 0.8)).willReturn(List.of(pass));
        given(scoreWeightService.getActiveWeight()).willReturn(weight);
        given(logService.logNotification(eq(user), any(), eq(weight))).willReturn(List.of(log));
        given(notificationGateway.send(eq("test@example.com"), eq("[청년복지] 맞춤 정책 추천"), any())).willReturn(true);
        given(jwtUtil.generateNotificationToken("user-key-1", 1L)).willReturn("unsubscribe-token");

        notificationService.sendTopRecommendations(target);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationGateway).send(eq("test@example.com"), eq("[청년복지] 맞춤 정책 추천"), messageCaptor.capture());
        String message = messageCaptor.getValue();
        assertTrue(message.contains("추천 이유: 주거비 부담 완화에 적합"));
        assertTrue(message.contains("unsubscribe-token"));
        assertTrue(!message.contains("점수 미달"));
    }

    @Test
    @DisplayName("최소 점수 이상 추천이 없으면 알림을 보내지 않는다")
    void sendTopRecommendationsSkipsWhenNoCandidatesAboveThreshold() {
        User user = User.builder()
                .id(1L)
                .userKey("user-key-1")
                .email("test@example.com")
                .passwordHash("pw")
                .notificationYn(true)
                .notificationPeriod(User.NotificationPeriod.DAILY)
                .notificationMinScore(0.95)
                .displayCount(10)
                .build();
        WelfareService service = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("SRC-11")
                .title("청년 월세 지원")
                .build();
        UserRecommendation fail = UserRecommendation.builder()
                .service(service)
                .finalScore(new BigDecimal("0.60"))
                .build();
        NotificationTarget target = new NotificationTarget(1L, "user-key-1", "test@example.com",
                User.NotificationPeriod.DAILY, 0.95, 10);

        given(userReadService.getActiveUserByUserKey("user-key-1")).willReturn(user);
        given(recommendationFacade.getRecommendations(1L, 50)).willReturn(List.of(fail));
        given(notificationSlotSelector.selectCandidates(List.of(fail), 0.95)).willReturn(List.of());

        notificationService.sendTopRecommendations(target);

        verify(notificationGateway, never()).send(any(), any(), any());
        verify(notificationHistoryService, never()).saveResult(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("게이트웨이가 false를 반환하면 실패 이력을 저장한다")
    void sendTopRecommendationsStoresFailedHistoryWhenGatewayReturnsFalse() {
        User user = User.builder()
                .id(1L)
                .userKey("user-key-1")
                .email("test@example.com")
                .passwordHash("pw")
                .notificationYn(true)
                .notificationPeriod(User.NotificationPeriod.DAILY)
                .notificationMinScore(0.8)
                .displayCount(10)
                .build();
        WelfareService service = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("SRC-11")
                .title("청년 월세 지원")
                .build();
        UserRecommendation recommendation = UserRecommendation.builder()
                .service(service)
                .finalScore(new BigDecimal("0.91"))
                .aiReason("주거비 부담 완화에 적합")
                .build();
        ScoreWeight weight = ScoreWeight.builder()
                .ruleWeight(new BigDecimal("0.8"))
                .aiWeight(new BigDecimal("0.2"))
                .build();
        RecommendationLog log = RecommendationLog.builder().id(100L).build();
        NotificationTarget target = new NotificationTarget(1L, "user-key-1", "test@example.com",
                User.NotificationPeriod.DAILY, 0.8, 10);

        given(userReadService.getActiveUserByUserKey("user-key-1")).willReturn(user);
        given(recommendationFacade.getRecommendations(1L, 50)).willReturn(List.of(recommendation));
        given(notificationSlotSelector.selectCandidates(List.of(recommendation), 0.8)).willReturn(List.of(recommendation));
        given(scoreWeightService.getActiveWeight()).willReturn(weight);
        given(logService.logNotification(eq(user), any(), eq(weight))).willReturn(List.of(log));
        given(notificationGateway.send(eq("test@example.com"), eq("[청년복지] 맞춤 정책 추천"), any())).willReturn(false);
        given(jwtUtil.generateNotificationToken("user-key-1", 1L)).willReturn("unsubscribe-token");

        notificationService.sendTopRecommendations(target);

        verify(notificationHistoryService).saveResult(
                eq(user),
                eq(NotificationPeriodType.DAILY),
                eq(NotificationChannel.EMAIL),
                eq(NotificationStatus.FAILED),
                eq("[청년복지] 맞춤 정책 추천"),
                any(),
                any(),
                any(),
                eq("notification gateway returned false")
        );
    }

    @Test
    @DisplayName("슬롯 배치는 상위 A 2건과 신규 B 1건을 발송 본문에 반영한다")
    void sendTopRecommendationsUsesSlotSelectorResult() {
        User user = User.builder()
                .id(1L)
                .userKey("user-key-1")
                .email("test@example.com")
                .passwordHash("pw")
                .notificationYn(true)
                .notificationPeriod(User.NotificationPeriod.DAILY)
                .notificationMinScore(0.8)
                .displayCount(10)
                .build();
        WelfareService a1Service = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("SRC-11")
                .title("A1")
                .build();
        WelfareService a2Service = WelfareService.builder()
                .id(12L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("SRC-12")
                .title("A2")
                .build();
        WelfareService bService = WelfareService.builder()
                .id(13L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("SRC-13")
                .title("B1")
                .build();
        UserRecommendation a1 = UserRecommendation.builder()
                .service(a1Service)
                .finalScore(new BigDecimal("0.95"))
                .aiReason("A1 reason")
                .build();
        UserRecommendation a2 = UserRecommendation.builder()
                .service(a2Service)
                .finalScore(new BigDecimal("0.90"))
                .aiReason("A2 reason")
                .build();
        UserRecommendation b1 = UserRecommendation.builder()
                .service(bService)
                .finalScore(new BigDecimal("0.55"))
                .aiReason("B1 reason")
                .build();
        ScoreWeight weight = ScoreWeight.builder()
                .ruleWeight(new BigDecimal("0.8"))
                .aiWeight(new BigDecimal("0.2"))
                .build();
        RecommendationLog log1 = RecommendationLog.builder().id(100L).build();
        RecommendationLog log2 = RecommendationLog.builder().id(101L).build();
        RecommendationLog log3 = RecommendationLog.builder().id(102L).build();
        NotificationTarget target = new NotificationTarget(1L, "user-key-1", "test@example.com",
                User.NotificationPeriod.DAILY, 0.8, 10);

        given(userReadService.getActiveUserByUserKey("user-key-1")).willReturn(user);
        given(recommendationFacade.getRecommendations(1L, 50)).willReturn(List.of(a1, a2, b1));
        given(notificationSlotSelector.selectCandidates(List.of(a1, a2, b1), 0.8)).willReturn(List.of(a1, a2, b1));
        given(scoreWeightService.getActiveWeight()).willReturn(weight);
        given(logService.logNotification(eq(user), any(), eq(weight))).willReturn(List.of(log1, log2, log3));
        given(notificationGateway.send(eq("test@example.com"), eq("[청년복지] 맞춤 정책 추천"), any())).willReturn(true);
        given(jwtUtil.generateNotificationToken("user-key-1", 1L)).willReturn("unsubscribe-token");

        notificationService.sendTopRecommendations(target);

        ArgumentCaptor<List<UserRecommendation>> recommendationsCaptor = ArgumentCaptor.forClass(List.class);
        verify(logService).logNotification(eq(user), recommendationsCaptor.capture(), eq(weight));
        assertThat(recommendationsCaptor.getValue()).extracting(rec -> rec.getService().getTitle())
                .containsExactly("A1", "A2", "B1");
    }

    @Test
    @DisplayName("재시도 발송이 성공하면 상태를 SENT로 전환한다")
    void retryFailedNotificationsMarksSentOnSuccess() {
        User user = User.builder()
                .id(1L)
                .userKey("user-key-1")
                .email("test@example.com")
                .passwordHash("pw")
                .build();
        Notification notification = Notification.builder()
                .userKey("user-key-1")
                .channel(NotificationChannel.EMAIL)
                .periodType(NotificationPeriodType.DAILY)
                .status(NotificationStatus.FAILED)
                .subject("[청년복지] 맞춤 정책 추천")
                .messageText("body")
                .retryCount(1)
                .nextRetryAt(LocalDateTime.now().minusMinutes(1))
                .errorMessage("temporary failure")
                .build();

        given(notificationRepository.findByStatusAndNextRetryAtBefore(eq(NotificationStatus.FAILED), any(LocalDateTime.class)))
                .willReturn(List.of(notification));
        given(userReadService.getNotificationEmailByUserKey("user-key-1")).willReturn("test@example.com");
        given(notificationGateway.send("test@example.com", "[청년복지] 맞춤 정책 추천", "body"))
                .willReturn(true);

        notificationService.retryFailedNotifications();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isNotNull();
        assertThat(notification.getNextRetryAt()).isNull();
        assertThat(notification.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("첫 재시도도 실패하면 2시간 뒤로 다시 예약한다")
    void retryFailedNotificationsSchedulesTwoHourDelayAfterFirstRetryFailure() {
        User user = User.builder()
                .id(1L)
                .userKey("user-key-1")
                .email("test@example.com")
                .passwordHash("pw")
                .build();
        Notification notification = Notification.builder()
                .userKey("user-key-1")
                .channel(NotificationChannel.EMAIL)
                .periodType(NotificationPeriodType.DAILY)
                .status(NotificationStatus.FAILED)
                .subject("[청년복지] 맞춤 정책 추천")
                .messageText("body")
                .retryCount(0)
                .nextRetryAt(LocalDateTime.now().minusMinutes(1))
                .build();

        given(notificationRepository.findByStatusAndNextRetryAtBefore(eq(NotificationStatus.FAILED), any(LocalDateTime.class)))
                .willReturn(List.of(notification));
        given(userReadService.getNotificationEmailByUserKey("user-key-1")).willReturn("test@example.com");
        given(notificationGateway.send("test@example.com", "[청년복지] 맞춤 정책 추천", "body"))
                .willReturn(false);

        LocalDateTime before = LocalDateTime.now();
        notificationService.retryFailedNotifications();
        LocalDateTime after = LocalDateTime.now();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getRetryCount()).isEqualTo(1);
        assertThat(notification.getErrorMessage()).isEqualTo("notification gateway returned false");
        assertThat(notification.getNextRetryAt()).isBetween(before.plusMinutes(120), after.plusMinutes(120));
    }

    @Test
    @DisplayName("최대 재시도 직전 실패하면 더 이상 다음 재시도를 예약하지 않는다")
    void retryFailedNotificationsStopsSchedulingAfterMaxRetry() {
        User user = User.builder()
                .id(1L)
                .userKey("user-key-1")
                .email("test@example.com")
                .passwordHash("pw")
                .build();
        Notification notification = Notification.builder()
                .userKey("user-key-1")
                .channel(NotificationChannel.EMAIL)
                .periodType(NotificationPeriodType.DAILY)
                .status(NotificationStatus.FAILED)
                .subject("[청년복지] 맞춤 정책 추천")
                .messageText("body")
                .retryCount(1)
                .nextRetryAt(LocalDateTime.now().minusMinutes(1))
                .build();

        given(notificationRepository.findByStatusAndNextRetryAtBefore(eq(NotificationStatus.FAILED), any(LocalDateTime.class)))
                .willReturn(List.of(notification));
        given(userReadService.getNotificationEmailByUserKey("user-key-1")).willReturn("test@example.com");
        given(notificationGateway.send("test@example.com", "[청년복지] 맞춤 정책 추천", "body"))
                .willReturn(false);

        notificationService.retryFailedNotifications();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getRetryCount()).isEqualTo(2);
        assertThat(notification.getNextRetryAt()).isNull();
        assertThat(notification.getErrorMessage()).isEqualTo("notification gateway returned false");
    }
}
