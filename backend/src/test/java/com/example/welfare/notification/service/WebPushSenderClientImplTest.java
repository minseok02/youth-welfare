package com.example.welfare.notification.service;

import com.example.welfare.notification.entity.WebPushSubscription;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class WebPushSenderClientImplTest {

    @Mock
    private WebPushServiceFactory webPushServiceFactory;
    @Mock
    private WebPushKeyValidator webPushKeyValidator;
    @Mock
    private WebPushEndpointPolicyService webPushEndpointPolicyService;

    @Test
    @DisplayName("미구성 상태면 sender not configured 실패를 반환한다")
    void sendReturnsFailureWhenNotConfigured() {
        WebPushSenderClientImpl client = new WebPushSenderClientImpl(
                new ObjectMapper(),
                webPushKeyValidator,
                webPushServiceFactory,
                webPushEndpointPolicyService
        );
        ReflectionTestUtils.setField(client, "publicKey", "");
        ReflectionTestUtils.setField(client, "privateKey", "");
        ReflectionTestUtils.setField(client, "subject", "");

        WebPushSendResult result = client.send(subscription(), content());

        assertThat(result.success()).isFalse();
        assertThat(result.disableSubscription()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("web push sender is not configured");
    }

    @Test
    @DisplayName("factory 초기화 예외도 failure result로 흡수한다")
    void sendReturnsFailureWhenFactoryThrows() throws Exception {
        WebPushSenderClientImpl client = new WebPushSenderClientImpl(
                new ObjectMapper(),
                webPushKeyValidator,
                webPushServiceFactory,
                webPushEndpointPolicyService
        );
        given(webPushKeyValidator.isValidPublicKey("public")).willReturn(true);
        given(webPushKeyValidator.isValidPrivateKey("private")).willReturn(true);
        given(webPushEndpointPolicyService.isAllowedSubscriptionEndpoint("https://push.example/subscription"))
                .willReturn(true);
        given(webPushServiceFactory.create("public", "private", "mailto:test@example.com"))
                .willThrow(new RuntimeException("bootstrap failed"));
        ReflectionTestUtils.setField(client, "publicKey", "public");
        ReflectionTestUtils.setField(client, "privateKey", "private");
        ReflectionTestUtils.setField(client, "subject", "mailto:test@example.com");

        WebPushSendResult result = client.send(subscription(), content());

        assertThat(result.success()).isFalse();
        assertThat(result.disableSubscription()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("bootstrap failed");
    }

    @Test
    @DisplayName("unsafe endpoint는 sender 초기화 전이라도 disable result로 거부한다")
    void sendDisablesUnsafeEndpointBeforeSending() {
        WebPushSenderClientImpl client = new WebPushSenderClientImpl(
                new ObjectMapper(),
                webPushKeyValidator,
                webPushServiceFactory,
                webPushEndpointPolicyService
        );
        given(webPushKeyValidator.isValidPublicKey("public")).willReturn(true);
        given(webPushKeyValidator.isValidPrivateKey("private")).willReturn(true);
        given(webPushEndpointPolicyService.isAllowedSubscriptionEndpoint("https://push.example/subscription"))
                .willReturn(false);
        ReflectionTestUtils.setField(client, "publicKey", "public");
        ReflectionTestUtils.setField(client, "privateKey", "private");
        ReflectionTestUtils.setField(client, "subject", "mailto:test@example.com");

        WebPushSendResult result = client.send(subscription(), content());

        assertThat(result.success()).isFalse();
        assertThat(result.disableSubscription()).isTrue();
        assertThat(result.errorMessage()).isEqualTo("web push endpoint rejected by policy");
    }

    private NotificationContent content() {
        return new NotificationContent(
                "title", "body", "/policies/1", "https://example.com/policies/1"
        );
    }

    private WebPushSubscription subscription() {
        return WebPushSubscription.builder()
                .id(1L)
                .userKey("user-key-1")
                .endpoint("https://push.example/subscription")
                .p256dh("ignored")
                .authSecret("ignored")
                .enabled(true)
                .build();
    }
}
