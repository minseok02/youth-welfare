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
        RecommendationDigestContent content = new RecommendationDigestContent(
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
        RecommendationDigestContent content = new RecommendationDigestContent(
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
        RecommendationDigestContent content = new RecommendationDigestContent(
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
}
