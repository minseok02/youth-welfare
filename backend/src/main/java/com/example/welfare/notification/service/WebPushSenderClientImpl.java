package com.example.welfare.notification.service;

import com.example.welfare.notification.entity.WebPushSubscription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.jose4j.lang.JoseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebPushSenderClientImpl implements WebPushSenderClient {

    private final ObjectMapper objectMapper;
    private final WebPushKeyValidator webPushKeyValidator;

    @Value("${notification.web-push.public-key:}")
    private String publicKey;

    @Value("${notification.web-push.private-key:}")
    private String privateKey;

    @Value("${notification.web-push.subject:}")
    private String subject;

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(subject)
                && webPushKeyValidator.isValidPublicKey(publicKey)
                && webPushKeyValidator.isValidPrivateKey(privateKey);
    }

    @Override
    public WebPushSendResult send(WebPushSubscription subscription, RecommendationDigestContent content) {
        if (!isConfigured()) {
            return WebPushSendResult.failure("web push sender is not configured");
        }

        try {
            PushService pushService = new PushService(publicKey, privateKey, subject);
            String payload = objectMapper.writeValueAsString(Map.of(
                    "title", content.title(),
                    "body", content.body(),
                    "url", content.absoluteUrl()
            ));

            nl.martijndwars.webpush.Notification notification =
                    new nl.martijndwars.webpush.Notification(
                            subscription.getEndpoint(),
                            subscription.getP256dh(),
                            subscription.getAuthSecret(),
                            payload
                    );

            HttpResponse response = pushService.send(notification);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode >= 200 && statusCode < 300) {
                return WebPushSendResult.sent();
            }
            if (statusCode == 404 || statusCode == 410) {
                return WebPushSendResult.disable("web push subscription expired: " + statusCode);
            }
            return WebPushSendResult.failure("web push send failed with status " + statusCode);
        } catch (GeneralSecurityException | JoseException | IOException | ExecutionException e) {
            log.warn("[WebPushSenderClient] web push send failed endpoint={}: {}", subscription.getEndpoint(), e.getMessage());
            return WebPushSendResult.failure(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return WebPushSendResult.failure(e.getMessage());
        }
    }
}
