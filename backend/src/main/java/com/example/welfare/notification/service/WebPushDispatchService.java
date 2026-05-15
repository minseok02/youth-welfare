package com.example.welfare.notification.service;

import com.example.welfare.notification.dto.WebPushTestSendRequest;
import com.example.welfare.notification.dto.WebPushTestSendResponse;
import com.example.welfare.notification.entity.WebPushSubscription;
import com.example.welfare.notification.repository.WebPushSubscriptionRepository;
import com.example.welfare.recommend.entity.UserRecommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebPushDispatchService {

    private final WebPushSubscriptionRepository webPushSubscriptionRepository;
    private final WebPushSenderClient webPushSenderClient;
    private final RecommendationDigestContentService recommendationDigestContentService;

    @Transactional
    public void sendRecommendationDigest(String userKey, List<UserRecommendation> recommendations) {
        List<WebPushSubscription> subscriptions =
                webPushSubscriptionRepository.findByUserKeyAndEnabledTrueOrderByCreatedAtDesc(userKey);
        if (subscriptions.isEmpty()) {
            return;
        }
        if (!webPushSenderClient.isConfigured()) {
            log.info("[WebPushDispatchService] web push sender not configured. skip userKey={} subscriptions={}",
                    userKey, subscriptions.size());
            return;
        }

        RecommendationDigestContent content = recommendationDigestContentService.build(recommendations);
        for (WebPushSubscription subscription : subscriptions) {
            WebPushSendResult result;
            try {
                result = webPushSenderClient.send(subscription, content);
            } catch (RuntimeException | LinkageError e) {
                log.warn("[WebPushDispatchService] unexpected web push send failure endpoint={}: {}",
                        subscription.getEndpoint(), e.getMessage());
                subscription.markError(e.getMessage());
                continue;
            }
            if (result.success()) {
                subscription.markSent();
                continue;
            }
            if (result.disableSubscription()) {
                subscription.disable(result.errorMessage());
                continue;
            }
            subscription.markError(result.errorMessage());
        }
    }

    @Transactional
    public WebPushTestSendResponse sendTestMessage(String userKey, WebPushTestSendRequest request) {
        List<WebPushSubscription> subscriptions =
                webPushSubscriptionRepository.findByUserKeyAndEnabledTrueOrderByCreatedAtDesc(userKey);
        if (subscriptions.isEmpty()) {
            return new WebPushTestSendResponse(0, 0, 0, 0);
        }
        if (!webPushSenderClient.isConfigured()) {
            log.info("[WebPushDispatchService] web push sender not configured. skip test send userKey={} subscriptions={}",
                    userKey, subscriptions.size());
            return new WebPushTestSendResponse(subscriptions.size(), 0, 0, subscriptions.size());
        }

        RecommendationDigestContent content = new RecommendationDigestContent(
                request.getTitle(),
                request.getBody(),
                request.getUrl(),
                request.getUrl()
        );

        int sentCount = 0;
        int disabledCount = 0;
        int failedCount = 0;
        for (WebPushSubscription subscription : subscriptions) {
            WebPushSendResult result;
            try {
                result = webPushSenderClient.send(subscription, content);
            } catch (RuntimeException | LinkageError e) {
                log.warn("[WebPushDispatchService] unexpected web push test send failure endpoint={}: {}",
                        subscription.getEndpoint(), e.getMessage());
                subscription.markError(e.getMessage());
                failedCount++;
                continue;
            }

            if (result.success()) {
                subscription.markSent();
                sentCount++;
                continue;
            }
            if (result.disableSubscription()) {
                subscription.disable(result.errorMessage());
                disabledCount++;
                continue;
            }
            subscription.markError(result.errorMessage());
            failedCount++;
        }

        return new WebPushTestSendResponse(subscriptions.size(), sentCount, disabledCount, failedCount);
    }
}
