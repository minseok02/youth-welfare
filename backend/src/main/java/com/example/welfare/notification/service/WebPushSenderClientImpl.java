package com.example.welfare.notification.service;

import com.example.welfare.notification.entity.WebPushSubscription;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.jose4j.lang.JoseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebPushSenderClientImpl implements WebPushSenderClient {

    private final ObjectMapper objectMapper;
    private final WebPushKeyValidator webPushKeyValidator;
    private final WebPushServiceFactory webPushServiceFactory;
    private final WebPushEndpointPolicyService webPushEndpointPolicyService;

    @Value("${notification.web-push.public-key:}")
    private String publicKey;

    @Value("${notification.web-push.private-key:}")
    private String privateKey;

    @Value("${notification.web-push.subject:}")
    private String subject;

    @Value("${notification.web-push.send-timeout-seconds:10}")
    private long sendTimeoutSeconds;

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(subject)
                && webPushKeyValidator.isValidPublicKey(publicKey)
                && webPushKeyValidator.isValidPrivateKey(privateKey);
    }

    @Override
    public WebPushSendResult send(WebPushSubscription subscription, NotificationContent content) {
        if (!isConfigured()) {
            return WebPushSendResult.failure("web push sender is not configured");
        }
        if (!webPushEndpointPolicyService.isAllowedSubscriptionEndpoint(subscription.getEndpoint())) {
            return WebPushSendResult.disable("web push endpoint rejected by policy");
        }
        if (!webPushKeyValidator.isValidSubscriptionPublicKey(subscription.getP256dh())
                || !webPushKeyValidator.isValidAuthSecret(subscription.getAuthSecret())) {
            return WebPushSendResult.disable("web push subscription keys rejected by policy");
        }

        try {
            nl.martijndwars.webpush.PushService pushService =
                    webPushServiceFactory.create(publicKey, privateKey, subject);
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

            Future<HttpResponse> responseFuture = pushService.sendAsync(notification);
            HttpResponse response;
            try {
                response = responseFuture.get(Math.max(1L, sendTimeoutSeconds), TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                responseFuture.cancel(true);
                return WebPushSendResult.failure("web push send timed out");
            }
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode >= 200 && statusCode < 300) {
                return WebPushSendResult.sent();
            }
            if (statusCode == 404 || statusCode == 410) {
                return WebPushSendResult.disable("web push subscription expired: " + statusCode);
            }
            return WebPushSendResult.failure("web push send failed with status " + statusCode);
        } catch (GeneralSecurityException | JoseException | IOException | ExecutionException e) {
            log.warn("[WebPushSenderClient] web push send failed endpointHost={} errorType={}",
                    webPushEndpointPolicyService.describeEndpointForLog(subscription.getEndpoint()),
                    e.getClass().getSimpleName());
            return WebPushSendResult.failure("web push send failed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return WebPushSendResult.failure("web push send interrupted");
        } catch (RuntimeException | LinkageError e) {
            log.warn("[WebPushSenderClient] web push sender initialization failed endpointHost={} errorType={}",
                    webPushEndpointPolicyService.describeEndpointForLog(subscription.getEndpoint()),
                    e.getClass().getSimpleName());
            return WebPushSendResult.failure("web push sender initialization failed");
        }
    }
}
