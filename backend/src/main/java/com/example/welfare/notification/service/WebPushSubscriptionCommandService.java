package com.example.welfare.notification.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.notification.dto.WebPushSubscriptionRequest;
import com.example.welfare.notification.dto.WebPushSubscriptionResponse;
import com.example.welfare.notification.entity.WebPushSubscription;
import com.example.welfare.notification.repository.WebPushSubscriptionCleanupCommandRepository;
import com.example.welfare.notification.repository.WebPushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class WebPushSubscriptionCommandService {

    private final WebPushSubscriptionRepository webPushSubscriptionRepository;
    private final WebPushSubscriptionCleanupCommandRepository webPushSubscriptionCleanupCommandRepository;
    private final WebPushEndpointPolicyService webPushEndpointPolicyService;
    private final WebPushKeyValidator webPushKeyValidator;

    @Value("${notification.web-push.max-subscriptions-per-user:10}")
    private int maxSubscriptionsPerUser;

    @Transactional
    public WebPushSubscriptionResponse register(String userKey, WebPushSubscriptionRequest request) {
        String endpoint = StringUtils.trimWhitespace(request.getEndpoint());
        String p256dh = StringUtils.trimWhitespace(request.getP256dh());
        String authSecret = StringUtils.trimWhitespace(request.getAuth());
        String userAgent = StringUtils.trimWhitespace(request.getUserAgent());
        String deviceLabel = StringUtils.trimWhitespace(request.getDeviceLabel());

        webPushEndpointPolicyService.validateSubscriptionEndpoint(endpoint);
        validateSubscriptionKeys(p256dh, authSecret);

        WebPushSubscription subscription = webPushSubscriptionRepository.findByEndpoint(endpoint)
                .orElse(null);
        if (requiresNewUserSlot(userKey, subscription)) {
            enforceSubscriptionLimit(userKey);
        }
        if (subscription == null) {
            subscription = WebPushSubscription.builder()
                    .userKey(userKey)
                    .endpoint(endpoint)
                    .p256dh(p256dh)
                    .authSecret(authSecret)
                    .userAgent(userAgent)
                    .deviceLabel(deviceLabel)
                    .enabled(true)
                    .lastSeenAt(LocalDateTime.now())
                    .build();
        }

        rejectCrossAccountEndpointTakeover(userKey, p256dh, authSecret, subscription);
        subscription.refresh(
                userKey,
                p256dh,
                authSecret,
                userAgent,
                deviceLabel
        );

        return WebPushSubscriptionResponse.from(webPushSubscriptionRepository.save(subscription));
    }

    @Transactional
    public void delete(String userKey, Long subscriptionId) {
        WebPushSubscription subscription = webPushSubscriptionRepository.findByIdAndUserKey(subscriptionId, userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_PUSH_SUBSCRIPTION_NOT_FOUND));
        webPushSubscriptionCleanupCommandRepository.deleteByIdAndUserKey(subscription.getId(), subscription.getUserKey());
    }

    private void validateSubscriptionKeys(String p256dh, String authSecret) {
        if (!webPushKeyValidator.isValidSubscriptionPublicKey(p256dh)
                || !webPushKeyValidator.isValidAuthSecret(authSecret)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private boolean requiresNewUserSlot(String userKey, WebPushSubscription subscription) {
        return subscription == null
                || !Objects.equals(userKey, subscription.getUserKey())
                || !subscription.isEnabled();
    }

    private void enforceSubscriptionLimit(String userKey) {
        if (maxSubscriptionsPerUser < 1) {
            throw new CustomException(ErrorCode.NOTIFICATION_PUSH_SUBSCRIPTION_LIMIT_EXCEEDED);
        }
        if (webPushSubscriptionRepository.countByUserKeyAndEnabledTrue(userKey) >= maxSubscriptionsPerUser) {
            throw new CustomException(ErrorCode.NOTIFICATION_PUSH_SUBSCRIPTION_LIMIT_EXCEEDED);
        }
    }

    private void rejectCrossAccountEndpointTakeover(String userKey,
                                                    String p256dh,
                                                    String authSecret,
                                                    WebPushSubscription subscription) {
        if (Objects.equals(userKey, subscription.getUserKey())) {
            return;
        }
        if (!Objects.equals(p256dh, subscription.getP256dh())
                || !Objects.equals(authSecret, subscription.getAuthSecret())) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }
}
