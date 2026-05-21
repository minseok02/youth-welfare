package com.example.welfare.notification.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.notification.entity.WebPushSubscription;
import com.example.welfare.notification.repository.WebPushSubscriptionCleanupCommandRepository;
import com.example.welfare.notification.repository.WebPushSubscriptionRepository;
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

@ExtendWith(MockitoExtension.class)
class WebPushSubscriptionCommandServiceTest {

    @Mock
    private WebPushSubscriptionRepository webPushSubscriptionRepository;

    @Mock
    private WebPushSubscriptionCleanupCommandRepository webPushSubscriptionCleanupCommandRepository;

    @InjectMocks
    private WebPushSubscriptionCommandService webPushSubscriptionCommandService;

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
}
