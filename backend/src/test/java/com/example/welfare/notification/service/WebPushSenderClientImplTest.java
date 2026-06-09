package com.example.welfare.notification.service;

import com.example.welfare.notification.entity.WebPushSubscription;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Security;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WebPushSenderClientImplTest {

    private static final String VALID_P256DH =
            "BPS33MOJ3Pl7w49gpeAX13m1MzkjuHYEH3IaQf-jJ-CAcTrD290RvOuukT55bCbkNBVnWlhB9-p7WoB9hyoeHgY";
    private static final String VALID_AUTH = "Z5wLdCDdx5x-UoxWMIvpSw";

    @BeforeAll
    static void ensureBouncyCastleProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

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

        WebPushSendResult result = client.send(validSubscription(), content());

        assertThat(result.success()).isFalse();
        assertThat(result.disableSubscription()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("web push sender is not configured");
    }

    @Test
    @DisplayName("factory 초기화 예외도 failure result로 흡수한다")
    void sendReturnsFailureWhenFactoryThrows() throws Exception {
        WebPushSenderClientImpl client = configuredClient();
        givenConfiguredSenderDependencies();
        given(webPushServiceFactory.create("public", "private", "mailto:test@example.com"))
                .willThrow(new RuntimeException("bootstrap failed"));

        WebPushSendResult result = client.send(validSubscription(), content());

        assertThat(result.success()).isFalse();
        assertThat(result.disableSubscription()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("web push sender initialization failed");
    }

    @Test
    @DisplayName("push service 2xx 응답은 sent result를 반환한다")
    void sendReturnsSentOnSuccessfulPushResponse() throws Exception {
        WebPushSenderClientImpl client = configuredClient();
        givenConfiguredSenderDependencies();
        givenPushServiceResponse(201);

        WebPushSendResult result = client.send(validSubscription(), content());

        assertThat(result.success()).isTrue();
        assertThat(result.disableSubscription()).isFalse();
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    @DisplayName("push service 410 응답은 만료 구독 disable result를 반환한다")
    void sendReturnsDisableOnExpiredPushResponse() throws Exception {
        WebPushSenderClientImpl client = configuredClient();
        givenConfiguredSenderDependencies();
        givenPushServiceResponse(410);

        WebPushSendResult result = client.send(validSubscription(), content());

        assertThat(result.success()).isFalse();
        assertThat(result.disableSubscription()).isTrue();
        assertThat(result.errorMessage()).isEqualTo("web push subscription expired: 410");
    }

    @Test
    @DisplayName("push service 5xx 응답은 disable하지 않고 failure result를 반환한다")
    void sendReturnsFailureOnPushServiceErrorResponse() throws Exception {
        WebPushSenderClientImpl client = configuredClient();
        givenConfiguredSenderDependencies();
        givenPushServiceResponse(503);

        WebPushSendResult result = client.send(validSubscription(), content());

        assertThat(result.success()).isFalse();
        assertThat(result.disableSubscription()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("web push send failed with status 503");
    }

    @Test
    @DisplayName("push service 호출이 timeout을 넘기면 future를 취소하고 failure result를 반환한다")
    void sendCancelsFutureAndReturnsFailureOnTimeout() throws Exception {
        WebPushSenderClientImpl client = configuredClient();
        givenConfiguredSenderDependencies();
        nl.martijndwars.webpush.PushService pushService = mock(nl.martijndwars.webpush.PushService.class);
        @SuppressWarnings("unchecked")
        Future<HttpResponse> responseFuture = mock(Future.class);
        given(webPushServiceFactory.create("public", "private", "mailto:test@example.com"))
                .willReturn(pushService);
        given(pushService.sendAsync(org.mockito.ArgumentMatchers.any(nl.martijndwars.webpush.Notification.class)))
                .willReturn(responseFuture);
        given(responseFuture.get(10L, TimeUnit.SECONDS)).willThrow(new TimeoutException("slow"));

        WebPushSendResult result = client.send(validSubscription(), content());

        assertThat(result.success()).isFalse();
        assertThat(result.disableSubscription()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("web push send timed out");
        verify(responseFuture).cancel(true);
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

    @Test
    @DisplayName("저장된 구독 키가 올바르지 않으면 push service 호출 전에 disable result를 반환한다")
    void sendDisablesInvalidStoredSubscriptionKeysBeforeSending() {
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
        given(webPushKeyValidator.isValidSubscriptionPublicKey("ignored")).willReturn(false);
        ReflectionTestUtils.setField(client, "publicKey", "public");
        ReflectionTestUtils.setField(client, "privateKey", "private");
        ReflectionTestUtils.setField(client, "subject", "mailto:test@example.com");

        WebPushSendResult result = client.send(subscription(), content());

        assertThat(result.success()).isFalse();
        assertThat(result.disableSubscription()).isTrue();
        assertThat(result.errorMessage()).isEqualTo("web push subscription keys rejected by policy");
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

    private WebPushSubscription validSubscription() {
        return WebPushSubscription.builder()
                .id(1L)
                .userKey("user-key-1")
                .endpoint("https://push.example/subscription")
                .p256dh(VALID_P256DH)
                .authSecret(VALID_AUTH)
                .enabled(true)
                .build();
    }

    private WebPushSenderClientImpl configuredClient() {
        WebPushSenderClientImpl client = new WebPushSenderClientImpl(
                new ObjectMapper(),
                webPushKeyValidator,
                webPushServiceFactory,
                webPushEndpointPolicyService
        );
        ReflectionTestUtils.setField(client, "publicKey", "public");
        ReflectionTestUtils.setField(client, "privateKey", "private");
        ReflectionTestUtils.setField(client, "subject", "mailto:test@example.com");
        ReflectionTestUtils.setField(client, "sendTimeoutSeconds", 10L);
        return client;
    }

    private void givenConfiguredSenderDependencies() {
        given(webPushKeyValidator.isValidPublicKey("public")).willReturn(true);
        given(webPushKeyValidator.isValidPrivateKey("private")).willReturn(true);
        given(webPushEndpointPolicyService.isAllowedSubscriptionEndpoint("https://push.example/subscription"))
                .willReturn(true);
        given(webPushKeyValidator.isValidSubscriptionPublicKey(VALID_P256DH)).willReturn(true);
        given(webPushKeyValidator.isValidAuthSecret(VALID_AUTH)).willReturn(true);
    }

    private void givenPushServiceResponse(int statusCode) throws Exception {
        nl.martijndwars.webpush.PushService pushService = mock(nl.martijndwars.webpush.PushService.class);
        @SuppressWarnings("unchecked")
        Future<HttpResponse> responseFuture = mock(Future.class);
        HttpResponse response = mock(HttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        given(webPushServiceFactory.create("public", "private", "mailto:test@example.com"))
                .willReturn(pushService);
        given(pushService.sendAsync(org.mockito.ArgumentMatchers.any(nl.martijndwars.webpush.Notification.class)))
                .willReturn(responseFuture);
        given(responseFuture.get(10L, TimeUnit.SECONDS)).willReturn(response);
        given(response.getStatusLine()).willReturn(statusLine);
        given(statusLine.getStatusCode()).willReturn(statusCode);
    }
}
