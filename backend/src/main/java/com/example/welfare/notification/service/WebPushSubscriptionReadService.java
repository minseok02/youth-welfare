package com.example.welfare.notification.service;

import com.example.welfare.notification.dto.WebPushPublicKeyResponse;
import com.example.welfare.notification.dto.WebPushSubscriptionResponse;
import com.example.welfare.notification.repository.WebPushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WebPushSubscriptionReadService {

    private final WebPushSubscriptionRepository webPushSubscriptionRepository;

    @Value("${notification.web-push.public-key:}")
    private String webPushPublicKey;

    public WebPushPublicKeyResponse getPublicKey() {
        return new WebPushPublicKeyResponse(webPushPublicKey == null ? "" : webPushPublicKey);
    }

    public List<WebPushSubscriptionResponse> getMySubscriptions(String userKey) {
        return webPushSubscriptionRepository.findByUserKeyAndEnabledTrueOrderByCreatedAtDesc(userKey).stream()
                .map(WebPushSubscriptionResponse::from)
                .toList();
    }
}
