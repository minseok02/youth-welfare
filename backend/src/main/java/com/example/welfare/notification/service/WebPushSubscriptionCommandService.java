package com.example.welfare.notification.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.notification.dto.WebPushSubscriptionRequest;
import com.example.welfare.notification.dto.WebPushSubscriptionResponse;
import com.example.welfare.notification.entity.WebPushSubscription;
import com.example.welfare.notification.repository.WebPushSubscriptionCleanupCommandRepository;
import com.example.welfare.notification.repository.WebPushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class WebPushSubscriptionCommandService {

    private final WebPushSubscriptionRepository webPushSubscriptionRepository;
    private final WebPushSubscriptionCleanupCommandRepository webPushSubscriptionCleanupCommandRepository;
    private final WebPushEndpointPolicyService webPushEndpointPolicyService;

    @Transactional
    public WebPushSubscriptionResponse register(String userKey, WebPushSubscriptionRequest request) {
        webPushEndpointPolicyService.validateSubscriptionEndpoint(request.getEndpoint());

        WebPushSubscription subscription = webPushSubscriptionRepository.findByEndpoint(request.getEndpoint())
                .orElseGet(() -> WebPushSubscription.builder()
                        .userKey(userKey)
                        .endpoint(request.getEndpoint())
                        .p256dh(request.getP256dh())
                        .authSecret(request.getAuth())
                        .userAgent(request.getUserAgent())
                        .deviceLabel(request.getDeviceLabel())
                        .enabled(true)
                        .lastSeenAt(LocalDateTime.now())
                        .build());

        subscription.refresh(
                userKey,
                request.getP256dh(),
                request.getAuth(),
                request.getUserAgent(),
                request.getDeviceLabel()
        );

        return WebPushSubscriptionResponse.from(webPushSubscriptionRepository.save(subscription));
    }

    @Transactional
    public void delete(String userKey, Long subscriptionId) {
        WebPushSubscription subscription = webPushSubscriptionRepository.findByIdAndUserKey(subscriptionId, userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_PUSH_SUBSCRIPTION_NOT_FOUND));
        webPushSubscriptionCleanupCommandRepository.deleteByIdAndUserKey(subscription.getId(), subscription.getUserKey());
    }
}
