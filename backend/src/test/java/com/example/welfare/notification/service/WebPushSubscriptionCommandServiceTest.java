package com.example.welfare.notification.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.notification.entity.WebPushSubscription;
import com.example.welfare.notification.repository.WebPushSubscriptionCleanupCommandRepository;
import com.example.welfare.notification.repository.WebPushSubscriptionRepository;
import com.example.welfare.notification.dto.WebPushSubscriptionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class WebPushSubscriptionCommandServiceTest {

    @Mock
    private WebPushSubscriptionRepository webPushSubscriptionRepository;

    @Mock
    private WebPushSubscriptionCleanupCommandRepository webPushSubscriptionCleanupCommandRepository;
    @Mock
    private WebPushEndpointPolicyService webPushEndpointPolicyService;
    @Mock
    private WebPushKeyValidator webPushKeyValidator;

    @InjectMocks
    private WebPushSubscriptionCommandService webPushSubscriptionCommandService;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(
                webPushSubscriptionCommandService,
                "maxSubscriptionsPerUser",
                10
        );
    }

    @Test
    @DisplayName("웹푸시 구독 삭제는 소유 구독을 확인한 뒤 cleanup command로 삭제한다")
    void deleteOwnedSubscription() {
        WebPushSubscription subscription = WebPushSubscription.builder()
                .id(7L)
                .userKey("user-key-7")
                .endpoint("https://example.test/push")
                .p256dh("p256")
                .authSecret("auth")
                .build();
        given(webPushSubscriptionRepository.findByIdAndUserKey(7L, "user-key-7"))
                .willReturn(Optional.of(subscription));

        webPushSubscriptionCommandService.delete("user-key-7", 7L);

        then(webPushSubscriptionCleanupCommandRepository).should().deleteByIdAndUserKey(7L, "user-key-7");
    }

    @Test
    @DisplayName("웹푸시 구독 삭제는 소유 구독이 없으면 예외를 던진다")
    void deleteMissingSubscriptionThrows() {
        given(webPushSubscriptionRepository.findByIdAndUserKey(7L, "user-key-7"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> webPushSubscriptionCommandService.delete("user-key-7", 7L))
                .isInstanceOf(CustomException.class);
        then(webPushSubscriptionCleanupCommandRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("웹푸시 구독 등록은 unsafe endpoint를 거부한다")
    void registerRejectsUnsafeEndpoint() {
        WebPushSubscriptionRequest request = new WebPushSubscriptionRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "endpoint", "http://127.0.0.1/push");
        org.mockito.BDDMockito.willThrow(new CustomException(ErrorCode.INVALID_INPUT))
                .given(webPushEndpointPolicyService)
                .validateSubscriptionEndpoint("http://127.0.0.1/push");

        assertThatThrownBy(() -> webPushSubscriptionCommandService.register("user-key-7", request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        then(webPushSubscriptionRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("웹푸시 구독 등록은 p256dh/auth 형식이 올바르지 않으면 저장하지 않는다")
    void registerRejectsInvalidSubscriptionKeys() {
        WebPushSubscriptionRequest request = request(
                "https://fcm.googleapis.com/fcm/send/example",
                "bad-p256dh",
                "bad-auth"
        );
        given(webPushKeyValidator.isValidSubscriptionPublicKey("bad-p256dh")).willReturn(false);

        assertThatThrownBy(() -> webPushSubscriptionCommandService.register("user-key-7", request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        then(webPushSubscriptionRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("다른 계정의 기존 endpoint는 저장된 키와 요청 키가 다르면 탈취성 재등록을 거부한다")
    void registerRejectsCrossAccountEndpointWithDifferentKeys() {
        WebPushSubscriptionRequest request = request(
                " https://fcm.googleapis.com/fcm/send/example ",
                "new-p256dh",
                "new-auth"
        );
        WebPushSubscription existing = WebPushSubscription.builder()
                .id(7L)
                .userKey("other-user-key")
                .endpoint("https://fcm.googleapis.com/fcm/send/example")
                .p256dh("old-p256dh")
                .authSecret("old-auth")
                .enabled(true)
                .build();
        given(webPushKeyValidator.isValidSubscriptionPublicKey("new-p256dh")).willReturn(true);
        given(webPushKeyValidator.isValidAuthSecret("new-auth")).willReturn(true);
        given(webPushSubscriptionRepository.findByEndpoint("https://fcm.googleapis.com/fcm/send/example"))
                .willReturn(Optional.of(existing));
        given(webPushSubscriptionRepository.countByUserKeyAndEnabledTrue("user-key-7")).willReturn(0L);

        assertThatThrownBy(() -> webPushSubscriptionCommandService.register("user-key-7", request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        then(webPushSubscriptionRepository).should(never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("같은 브라우저 endpoint는 저장된 키와 요청 키가 같으면 새 계정으로 이전 등록을 허용한다")
    void registerAllowsCrossAccountEndpointWhenKeysMatch() {
        WebPushSubscriptionRequest request = request(
                "https://fcm.googleapis.com/fcm/send/example",
                "same-p256dh",
                "same-auth"
        );
        WebPushSubscription existing = WebPushSubscription.builder()
                .id(7L)
                .userKey("old-user-key")
                .endpoint("https://fcm.googleapis.com/fcm/send/example")
                .p256dh("same-p256dh")
                .authSecret("same-auth")
                .enabled(true)
                .build();
        given(webPushKeyValidator.isValidSubscriptionPublicKey("same-p256dh")).willReturn(true);
        given(webPushKeyValidator.isValidAuthSecret("same-auth")).willReturn(true);
        given(webPushSubscriptionRepository.findByEndpoint("https://fcm.googleapis.com/fcm/send/example"))
                .willReturn(Optional.of(existing));
        given(webPushSubscriptionRepository.countByUserKeyAndEnabledTrue("new-user-key")).willReturn(0L);
        given(webPushSubscriptionRepository.save(existing)).willReturn(existing);

        webPushSubscriptionCommandService.register("new-user-key", request);

        then(webPushSubscriptionRepository).should().save(existing);
    }

    @Test
    @DisplayName("사용자 활성 웹푸시 구독 수가 상한에 도달하면 새 endpoint 등록을 거부한다")
    void registerRejectsWhenActiveSubscriptionLimitExceeded() {
        WebPushSubscriptionRequest request = request(
                "https://fcm.googleapis.com/fcm/send/new-endpoint",
                "p256dh",
                "auth"
        );
        given(webPushKeyValidator.isValidSubscriptionPublicKey("p256dh")).willReturn(true);
        given(webPushKeyValidator.isValidAuthSecret("auth")).willReturn(true);
        given(webPushSubscriptionRepository.findByEndpoint("https://fcm.googleapis.com/fcm/send/new-endpoint"))
                .willReturn(Optional.empty());
        given(webPushSubscriptionRepository.countByUserKeyAndEnabledTrue("user-key-7")).willReturn(10L);

        assertThatThrownBy(() -> webPushSubscriptionCommandService.register("user-key-7", request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOTIFICATION_PUSH_SUBSCRIPTION_LIMIT_EXCEEDED);

        then(webPushSubscriptionRepository).should(never()).save(org.mockito.ArgumentMatchers.any());
    }

    private WebPushSubscriptionRequest request(String endpoint, String p256dh, String auth) {
        WebPushSubscriptionRequest request = new WebPushSubscriptionRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "endpoint", endpoint);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "p256dh", p256dh);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "auth", auth);
        return request;
    }
}
