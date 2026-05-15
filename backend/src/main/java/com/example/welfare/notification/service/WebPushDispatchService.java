package com.example.welfare.notification.service;

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
            WebPushSendResult result = webPushSenderClient.send(subscription, content);
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
}
