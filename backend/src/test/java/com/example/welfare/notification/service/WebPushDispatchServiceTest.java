package com.example.welfare.notification.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.notification.entity.WebPushSubscription;
import com.example.welfare.notification.repository.WebPushSubscriptionRepository;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.entity.UserRecommendation;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WebPushDispatchServiceTest {

    @Mock
    private WebPushSubscriptionRepository webPushSubscriptionRepository;
    @Mock
    private WebPushSenderClient webPushSenderClient;
    @Mock
    private RecommendationDigestContentService recommendationDigestContentService;
    @Mock
    private DeadlineReminderContentService deadlineReminderContentService;
    @Mock
    private WebPushEndpointPolicyService webPushEndpointPolicyService;
    @Mock
    private NotificationAttemptLogService notificationAttemptLogService;

    @InjectMocks
    private WebPushDispatchService webPushDispatchService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(webPushDispatchService, "appBaseUrl", "https://youth-welfare.kr");
    }

    @Test
    @DisplayName("sender 미구성 상태면 구독이 있어도 웹푸시를 보내지 않는다")
    void sendRecommendationDigestSkipsWhenSenderNotConfigured() {
        UserRecommendation recommendation = sampleRecommendation();
        given(webPushSubscriptionRepository.findByUserKeyAndEnabledTrueOrderByCreatedAtDesc("user-key-1"))
                .willReturn(List.of(sampleSubscription()));
        given(webPushSenderClient.isConfigured()).willReturn(false);

        webPushDispatchService.sendRecommendationDigest("user-key-1", List.of(recommendation));

        verify(webPushSenderClient, never()).send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("웹푸시 성공 후 subscription은 enabled 상태와 lastSentAt을 갱신한다")
    void sendRecommendationDigestMarksSubscriptionSentOnSuccess() {
        WebPushSubscription subscription = sampleSubscription();
        UserRecommendation recommendation = sampleRecommendation();
        NotificationContent content = new NotificationContent(
                "title",
                "body",
                "/policies/1",
                "https://example.com/policies/1"
        );
        given(webPushSubscriptionRepository.findByUserKeyAndEnabledTrueOrderByCreatedAtDesc("user-key-1"))
                .willReturn(List.of(subscription));
        given(webPushSenderClient.isConfigured()).willReturn(true);
        given(recommendationDigestContentService.build(List.of(recommendation)))
                .willReturn(content);
        given(webPushSenderClient.send(subscription, content)).willReturn(WebPushSendResult.sent());

        webPushDispatchService.sendRecommendationDigest("user-key-1", List.of(recommendation));

        assertThat(subscription.isEnabled()).isTrue();
        assertThat(subscription.getLastSentAt()).isNotNull();
        assertThat(subscription.getLastErrorAt()).isNull();
    }

    @Test
    @DisplayName("만료된 subscription 응답은 disable 처리한다")
    void sendRecommendationDigestDisablesExpiredSubscription() {
        WebPushSubscription subscription = sampleSubscription();
        UserRecommendation recommendation = sampleRecommendation();
        NotificationContent content = new NotificationContent(
                "title",
                "body",
                "/policies/1",
                "https://example.com/policies/1"
        );
        given(webPushSubscriptionRepository.findByUserKeyAndEnabledTrueOrderByCreatedAtDesc("user-key-1"))
                .willReturn(List.of(subscription));
        given(webPushSenderClient.isConfigured()).willReturn(true);
        given(recommendationDigestContentService.build(List.of(recommendation)))
                .willReturn(content);
        given(webPushSenderClient.send(subscription, content))
                .willReturn(WebPushSendResult.disable("expired"));

        webPushDispatchService.sendRecommendationDigest("user-key-1", List.of(recommendation));

        assertThat(subscription.isEnabled()).isFalse();
        assertThat(subscription.getLastErrorAt()).isNotNull();
        assertThat(subscription.getLastErrorMessage()).isEqualTo("expired");
    }

    @Test
    @DisplayName("sender가 runtime exception을 던져도 subscription에 error를 남긴다")
    void sendRecommendationDigestMarksErrorWhenSenderThrowsRuntimeException() {
        WebPushSubscription subscription = sampleSubscription();
        UserRecommendation recommendation = sampleRecommendation();
        NotificationContent content = new NotificationContent(
                "title",
                "body",
                "/policies/1",
                "https://example.com/policies/1"
        );
        given(webPushSubscriptionRepository.findByUserKeyAndEnabledTrueOrderByCreatedAtDesc("user-key-1"))
                .willReturn(List.of(subscription));
        given(webPushSenderClient.isConfigured()).willReturn(true);
        given(recommendationDigestContentService.build(List.of(recommendation)))
                .willReturn(content);
        given(webPushSenderClient.send(subscription, content))
                .willThrow(new RuntimeException("sender bootstrap failed"));

        webPushDispatchService.sendRecommendationDigest("user-key-1", List.of(recommendation));

        assertThat(subscription.isEnabled()).isTrue();
        assertThat(subscription.getLastErrorAt()).isNotNull();
        assertThat(subscription.getLastErrorMessage()).isEqualTo("web push send failed (RuntimeException)");
    }

    @Test
    @DisplayName("test send는 현재 사용자 활성 subscription에 대해 결과 집계를 반환한다")
    void sendTestMessageReturnsCounts() {
        WebPushSubscription success = sampleSubscription();
        WebPushSubscription disabled = WebPushSubscription.builder()
                .id(2L)
                .userKey("user-key-1")
                .endpoint("https://push.example/2")
                .p256dh("p256dh2")
                .authSecret("auth2")
                .enabled(true)
                .build();
        given(webPushSubscriptionRepository.findByUserKeyAndEnabledTrueOrderByCreatedAtDesc("user-key-1"))
                .willReturn(List.of(success, disabled));
        given(webPushSenderClient.isConfigured()).willReturn(true);
        given(webPushSenderClient.send(org.mockito.ArgumentMatchers.eq(success), org.mockito.ArgumentMatchers.any()))
                .willReturn(WebPushSendResult.sent());
        given(webPushSenderClient.send(org.mockito.ArgumentMatchers.eq(disabled), org.mockito.ArgumentMatchers.any()))
                .willReturn(WebPushSendResult.disable("expired"));

        var response = webPushDispatchService.sendTestMessage("user-key-1", request());

        assertThat(response.attemptedCount()).isEqualTo(2);
        assertThat(response.sentCount()).isEqualTo(1);
        assertThat(response.disabledCount()).isEqualTo(1);
        assertThat(response.failedCount()).isZero();
        assertThat(success.getLastSentAt()).isNotNull();
        assertThat(disabled.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("test send는 내부 path URL을 앱 base-url 기준 absolute URL로 정규화해 보낸다")
    void sendTestMessageNormalizesInternalPathUrl() {
        WebPushSubscription success = sampleSubscription();
        given(webPushSubscriptionRepository.findByUserKeyAndEnabledTrueOrderByCreatedAtDesc("user-key-1"))
                .willReturn(List.of(success));
        given(webPushSenderClient.isConfigured()).willReturn(true);
        given(webPushSenderClient.send(org.mockito.ArgumentMatchers.eq(success), org.mockito.ArgumentMatchers.any()))
                .willReturn(WebPushSendResult.sent());
        var request = request();
        request.setUrl("/policies/99?from=push");

        webPushDispatchService.sendTestMessage("user-key-1", request);

        ArgumentCaptor<NotificationContent> contentCaptor = ArgumentCaptor.forClass(NotificationContent.class);
        verify(webPushSenderClient).send(org.mockito.ArgumentMatchers.eq(success), contentCaptor.capture());
        assertThat(contentCaptor.getValue().deeplinkUrl()).isEqualTo("/policies/99?from=push");
        assertThat(contentCaptor.getValue().absoluteUrl()).isEqualTo("https://youth-welfare.kr/policies/99?from=push");
    }

    @Test
    @DisplayName("test send는 같은 origin absolute URL을 내부 path로 받아들인다")
    void sendTestMessageAcceptsSameOriginAbsoluteUrl() {
        WebPushSubscription success = sampleSubscription();
        given(webPushSubscriptionRepository.findByUserKeyAndEnabledTrueOrderByCreatedAtDesc("user-key-1"))
                .willReturn(List.of(success));
        given(webPushSenderClient.isConfigured()).willReturn(true);
        given(webPushSenderClient.send(org.mockito.ArgumentMatchers.eq(success), org.mockito.ArgumentMatchers.any()))
                .willReturn(WebPushSendResult.sent());
        var request = request();
        request.setUrl("https://youth-welfare.kr/mypage?tab=3");

        webPushDispatchService.sendTestMessage("user-key-1", request);

        ArgumentCaptor<NotificationContent> contentCaptor = ArgumentCaptor.forClass(NotificationContent.class);
        verify(webPushSenderClient).send(org.mockito.ArgumentMatchers.eq(success), contentCaptor.capture());
        assertThat(contentCaptor.getValue().deeplinkUrl()).isEqualTo("/mypage?tab=3");
        assertThat(contentCaptor.getValue().absoluteUrl()).isEqualTo("https://youth-welfare.kr/mypage?tab=3");
    }

    @Test
    @DisplayName("test send는 외부 origin URL을 거부한다")
    void sendTestMessageRejectsExternalOriginUrl() {
        given(webPushSubscriptionRepository.findByUserKeyAndEnabledTrueOrderByCreatedAtDesc("user-key-1"))
                .willReturn(List.of(sampleSubscription()));
        given(webPushSenderClient.isConfigured()).willReturn(true);
        var request = request();
        request.setUrl("https://evil.example/phish");

        assertThatThrownBy(() -> webPushDispatchService.sendTestMessage("user-key-1", request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(webPushSenderClient, never()).send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private WebPushSubscription sampleSubscription() {
        return WebPushSubscription.builder()
                .id(1L)
                .userKey("user-key-1")
                .endpoint("https://push.example/1")
                .p256dh("p256dh")
                .authSecret("auth")
                .enabled(true)
                .build();
    }

    private UserRecommendation sampleRecommendation() {
        WelfareService service = WelfareService.builder()
                .id(1L)
                .title("청년 월세 지원")
                .build();
        return UserRecommendation.builder()
                .service(service)
                .finalScore(new BigDecimal("0.91"))
                .build();
    }

    private com.example.welfare.notification.dto.WebPushTestSendRequest request() {
        var request = new com.example.welfare.notification.dto.WebPushTestSendRequest();
        request.setTitle("test");
        request.setBody("body");
        request.setUrl("/mypage?tab=3");
        return request;
    }
}
