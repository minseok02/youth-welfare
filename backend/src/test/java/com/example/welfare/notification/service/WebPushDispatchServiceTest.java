package com.example.welfare.notification.service;

import com.example.welfare.notification.entity.WebPushSubscription;
import com.example.welfare.notification.repository.WebPushSubscriptionRepository;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.entity.UserRecommendation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    @InjectMocks
    private WebPushDispatchService webPushDispatchService;

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
        assertThat(subscription.getLastErrorMessage()).isEqualTo("sender bootstrap failed");
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
        request.setUrl("https://example.com/test");
        return request;
    }
}
