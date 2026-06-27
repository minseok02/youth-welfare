package com.example.welfare.notification.service;

import com.example.welfare.notification.entity.Notification;
import com.example.welfare.notification.entity.UserAlert;
import com.example.welfare.notification.repository.UserAlertRepository;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.entity.UserRecommendation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserAlertCommandServiceTest {

    @Mock
    private UserAlertRepository userAlertRepository;
    @Mock
    private RecommendationDigestContentService recommendationDigestContentService;
    @Mock
    private DeadlineReminderContentService deadlineReminderContentService;

    @Test
    @DisplayName("같은 사용자에게 같은 title/deeplink unread digest가 있으면 새 alert를 만들지 않는다")
    void skipsDuplicateUnreadRecommendationDigestAlert() {
        UserAlertCommandService service = newService();
        Notification notification = notification("daily:user-key-7:2026-06-27");
        List<UserRecommendation> recommendations = List.of(recommendation(3324L));
        given(userAlertRepository.findByEventKey("daily:user-key-7:2026-06-27"))
                .willReturn(Optional.empty());
        given(recommendationDigestContentService.build(recommendations))
                .willReturn(new NotificationContent(
                        "맞춤 정책 추천이 도착했어요",
                        "1건 추천: 청년 정책",
                        "/policies/3324",
                        "https://youthmoa.kr/policies/3324"
                ));
        given(userAlertRepository.existsByUserKeyAndKindAndStatusAndTitleAndDeeplinkUrl(
                "user-key-7",
                UserAlert.UserAlertKind.RECOMMENDATION_DIGEST,
                UserAlert.UserAlertStatus.UNREAD,
                "맞춤 정책 추천이 도착했어요",
                "/policies/3324"
        )).willReturn(true);

        service.createRecommendationDigestAlert(notification, recommendations);

        then(userAlertRepository).should(never()).save(any(UserAlert.class));
    }

    @Test
    @DisplayName("같은 title/deeplink unread digest가 없으면 recommendation digest alert를 저장한다")
    void createsRecommendationDigestAlertWhenNoDuplicateUnreadExists() {
        UserAlertCommandService service = newService();
        Notification notification = notification("daily:user-key-7:2026-06-28");
        List<UserRecommendation> recommendations = List.of(recommendation(3324L));
        given(userAlertRepository.findByEventKey("daily:user-key-7:2026-06-28"))
                .willReturn(Optional.empty());
        given(recommendationDigestContentService.build(recommendations))
                .willReturn(new NotificationContent(
                        "맞춤 정책 추천이 도착했어요",
                        "1건 추천: 청년 정책",
                        "/policies/3324",
                        "https://youthmoa.kr/policies/3324"
                ));
        given(userAlertRepository.existsByUserKeyAndKindAndStatusAndTitleAndDeeplinkUrl(
                "user-key-7",
                UserAlert.UserAlertKind.RECOMMENDATION_DIGEST,
                UserAlert.UserAlertStatus.UNREAD,
                "맞춤 정책 추천이 도착했어요",
                "/policies/3324"
        )).willReturn(false);

        service.createRecommendationDigestAlert(notification, recommendations);

        ArgumentCaptor<UserAlert> captor = ArgumentCaptor.forClass(UserAlert.class);
        then(userAlertRepository).should().save(captor.capture());
        UserAlert saved = captor.getValue();
        assertThat(saved.getUserKey()).isEqualTo("user-key-7");
        assertThat(saved.getEventKey()).isEqualTo("daily:user-key-7:2026-06-28");
        assertThat(saved.getKind()).isEqualTo(UserAlert.UserAlertKind.RECOMMENDATION_DIGEST);
        assertThat(saved.getStatus()).isEqualTo(UserAlert.UserAlertStatus.UNREAD);
        assertThat(saved.getDeeplinkUrl()).isEqualTo("/policies/3324");
    }

    @Test
    @DisplayName("event key가 이미 있으면 content build 없이 기존 idempotency 경계에서 멈춘다")
    void skipsExistingEventKeyBeforeBuildingContent() {
        UserAlertCommandService service = newService();
        Notification notification = notification("daily:user-key-7:2026-06-27");
        given(userAlertRepository.findByEventKey("daily:user-key-7:2026-06-27"))
                .willReturn(Optional.of(UserAlert.builder().eventKey("daily:user-key-7:2026-06-27").build()));

        service.createRecommendationDigestAlert(notification, List.of(recommendation(3324L)));

        then(recommendationDigestContentService).should(never()).build(any());
        then(userAlertRepository).should(never()).save(any(UserAlert.class));
    }

    private UserAlertCommandService newService() {
        return new UserAlertCommandService(
                userAlertRepository,
                recommendationDigestContentService,
                deadlineReminderContentService
        );
    }

    private Notification notification(String dispatchKey) {
        return Notification.builder()
                .id(10L)
                .userKey("user-key-7")
                .dispatchKey(dispatchKey)
                .channel(Notification.NotificationChannel.EMAIL)
                .periodType(Notification.NotificationPeriodType.DAILY)
                .status(Notification.NotificationStatus.SENT)
                .subject("subject")
                .build();
    }

    private UserRecommendation recommendation(Long serviceId) {
        WelfareService service = WelfareService.builder()
                .id(serviceId)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("SRC-" + serviceId)
                .title("청년 정책")
                .build();
        return UserRecommendation.builder()
                .service(service)
                .finalScore(new BigDecimal("0.9"))
                .build();
    }
}
