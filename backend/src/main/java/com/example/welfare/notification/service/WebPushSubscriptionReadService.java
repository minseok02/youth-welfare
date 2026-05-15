package com.example.welfare.notification.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.notification.dto.WebPushPublicKeyResponse;
import com.example.welfare.notification.dto.WebPushSubscriptionResponse;
import com.example.welfare.notification.repository.WebPushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WebPushSubscriptionReadService {

    private final WebPushSubscriptionRepository webPushSubscriptionRepository;

    @Value("${notification.web-push.public-key:}")
    private String webPushPublicKey;

    public WebPushPublicKeyResponse getPublicKey() {
        if (!StringUtils.hasText(webPushPublicKey)) {
            throw new CustomException(ErrorCode.NOTIFICATION_PUSH_PUBLIC_KEY_NOT_CONFIGURED);
        }
        if (!isValidWebPushPublicKey(webPushPublicKey)) {
            throw new CustomException(ErrorCode.NOTIFICATION_PUSH_PUBLIC_KEY_INVALID);
        }
        return new WebPushPublicKeyResponse(webPushPublicKey);
    }

    public List<WebPushSubscriptionResponse> getMySubscriptions(String userKey) {
        return webPushSubscriptionRepository.findByUserKeyAndEnabledTrueOrderByCreatedAtDesc(userKey).stream()
                .map(WebPushSubscriptionResponse::from)
                .toList();
    }

    private boolean isValidWebPushPublicKey(String publicKey) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(publicKey);
            return decoded.length == 65 && decoded[0] == 0x04;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
