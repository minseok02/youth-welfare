package com.example.welfare.notification.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.RedisKeyHash;
import com.example.welfare.notification.dto.WebPushTestSendRequest;
import com.example.welfare.notification.dto.WebPushTestSendResponse;
import com.example.welfare.notification.entity.WebPushSubscription;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.notification.repository.WebPushSubscriptionRepository;
import com.example.welfare.recommend.entity.UserRecommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebPushDispatchService {

    private static final String DEFAULT_NOTIFICATION_PATH = "/mypage?tab=3";

    private final WebPushSubscriptionRepository webPushSubscriptionRepository;
    private final WebPushSenderClient webPushSenderClient;
    private final RecommendationDigestContentService recommendationDigestContentService;
    private final DeadlineReminderContentService deadlineReminderContentService;
    private final WebPushEndpointPolicyService webPushEndpointPolicyService;
    private final NotificationAttemptLogService notificationAttemptLogService;

    @Value("${app.base-url:http://localhost:5173}")
    private String appBaseUrl;

    @Transactional
    public void sendRecommendationDigest(String userKey, List<UserRecommendation> recommendations) {
        dispatchContent(userKey, "recommendation_digest", recommendationDigestContentService.build(recommendations));
    }

    @Transactional
    public void sendDeadlineReminder(String userKey, List<WelfareService> services, int days) {
        dispatchContent(userKey, "deadline_reminder", deadlineReminderContentService.build(services, days));
    }

    @Transactional
    public WebPushTestSendResponse sendTestMessage(String userKey, WebPushTestSendRequest request) {
        List<WebPushSubscription> subscriptions =
                webPushSubscriptionRepository.findByUserKeyAndEnabledTrueOrderByCreatedAtDesc(userKey);
        if (subscriptions.isEmpty()) {
            return new WebPushTestSendResponse(0, 0, 0, 0);
        }
        if (!webPushSenderClient.isConfigured()) {
            log.info("[WebPushDispatchService] web push sender not configured. skip test send userKeyHash={} subscriptions={}",
                    RedisKeyHash.sha256Hex(userKey), subscriptions.size());
            return new WebPushTestSendResponse(subscriptions.size(), 0, 0, subscriptions.size());
        }

        NotificationContent content = buildTestContent(request);

        int sentCount = 0;
        int disabledCount = 0;
        int failedCount = 0;
        for (WebPushSubscription subscription : subscriptions) {
            WebPushSendResult result;
            long startedNanos = System.nanoTime();
            String endpointHost = webPushEndpointPolicyService.describeEndpointForLog(subscription.getEndpoint());
            try {
                result = webPushSenderClient.send(subscription, content);
            } catch (RuntimeException | LinkageError e) {
                String errorType = e.getClass().getSimpleName();
                log.warn("[WebPushDispatchService] unexpected web push test send failure endpointHost={} errorType={}",
                        endpointHost, errorType);
                recordAttempt("test", "exception", userKey, 1, endpointHost, errorType, elapsedMs(startedNanos));
                subscription.markError("web push test send failed (" + errorType + ")");
                failedCount++;
                continue;
            }

            if (result.success()) {
                subscription.markSent();
                recordAttempt("test", "sent", userKey, 1, endpointHost, null, elapsedMs(startedNanos));
                sentCount++;
                continue;
            }
            if (result.disableSubscription()) {
                subscription.disable(result.errorMessage());
                recordAttempt("test", "disabled", userKey, 1, endpointHost, safeError(result.errorMessage()), elapsedMs(startedNanos));
                disabledCount++;
                continue;
            }
            subscription.markError(result.errorMessage());
            recordAttempt("test", "failed", userKey, 1, endpointHost, safeError(result.errorMessage()), elapsedMs(startedNanos));
            failedCount++;
        }

        return new WebPushTestSendResponse(subscriptions.size(), sentCount, disabledCount, failedCount);
    }

    private NotificationContent buildTestContent(WebPushTestSendRequest request) {
        String deeplinkUrl = sanitizeTestPushUrl(request.getUrl());
        return new NotificationContent(
                request.getTitle(),
                request.getBody(),
                deeplinkUrl,
                toAbsoluteUrl(deeplinkUrl)
        );
    }

    private void dispatchContent(String userKey, String kind, NotificationContent content) {
        List<WebPushSubscription> subscriptions =
                webPushSubscriptionRepository.findByUserKeyAndEnabledTrueOrderByCreatedAtDesc(userKey);
        if (subscriptions.isEmpty()) {
            return;
        }
        if (!webPushSenderClient.isConfigured()) {
            log.info("[WebPushDispatchService] web push sender not configured. skip userKeyHash={} subscriptions={}",
                    RedisKeyHash.sha256Hex(userKey), subscriptions.size());
            return;
        }

        for (WebPushSubscription subscription : subscriptions) {
            WebPushSendResult result;
            long startedNanos = System.nanoTime();
            String endpointHost = webPushEndpointPolicyService.describeEndpointForLog(subscription.getEndpoint());
            try {
                result = webPushSenderClient.send(subscription, content);
            } catch (RuntimeException | LinkageError e) {
                String errorType = e.getClass().getSimpleName();
                log.warn("[WebPushDispatchService] unexpected web push send failure endpointHost={} errorType={}",
                        endpointHost, errorType);
                recordAttempt(kind, "exception", userKey, 1, endpointHost, errorType, elapsedMs(startedNanos));
                subscription.markError("web push send failed (" + errorType + ")");
                continue;
            }
            if (result.success()) {
                subscription.markSent();
                recordAttempt(kind, "sent", userKey, 1, endpointHost, null, elapsedMs(startedNanos));
                continue;
            }
            if (result.disableSubscription()) {
                subscription.disable(result.errorMessage());
                recordAttempt(kind, "disabled", userKey, 1, endpointHost, safeError(result.errorMessage()), elapsedMs(startedNanos));
                continue;
            }
            subscription.markError(result.errorMessage());
            recordAttempt(kind, "failed", userKey, 1, endpointHost, safeError(result.errorMessage()), elapsedMs(startedNanos));
        }
    }

    private long elapsedMs(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private void recordAttempt(String kind,
                               String outcome,
                               String userKey,
                               int itemCount,
                               String endpointHost,
                               String errorType,
                               long durationMs) {
        String userKeyHash = RedisKeyHash.sha256Hex(userKey);
        if (errorType == null) {
            log.info("[NotificationAttempt] channel=web_push kind={} outcome={} userKeyHash={} endpointHost={} durationMs={}",
                    kind, outcome, userKeyHash, endpointHost, durationMs);
        } else {
            log.warn("[NotificationAttempt] channel=web_push kind={} outcome={} userKeyHash={} endpointHost={} errorType={} durationMs={}",
                    kind, outcome, userKeyHash, endpointHost, errorType, durationMs);
        }
        notificationAttemptLogService.record(new NotificationAttemptLogCommand(
                userKeyHash,
                "web_push",
                kind,
                outcome,
                itemCount,
                endpointHost,
                errorType,
                durationMs
        ));
    }

    private String safeError(String errorMessage) {
        if (!StringUtils.hasText(errorMessage)) {
            return "unknown";
        }
        return errorMessage.trim().replaceAll("[^A-Za-z0-9._:-]", "_");
    }

    private String sanitizeTestPushUrl(String rawUrl) {
        String normalized = StringUtils.trimWhitespace(rawUrl);
        if (!StringUtils.hasText(normalized)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        if (normalized.startsWith("/")) {
            return normalizeInternalPath(normalized);
        }

        try {
            URI absoluteUri = new URI(normalized);
            String scheme = absoluteUri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new CustomException(ErrorCode.INVALID_INPUT);
            }
            URI appBaseUri = normalizeAppBaseUri();
            if (!sameOrigin(appBaseUri, absoluteUri)) {
                throw new CustomException(ErrorCode.INVALID_INPUT);
            }
            return buildPathWithQueryAndFragment(absoluteUri);
        } catch (URISyntaxException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private String normalizeInternalPath(String rawPath) {
        if (rawPath.startsWith("//")) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        try {
            URI pathUri = new URI(rawPath);
            if (pathUri.isAbsolute() || StringUtils.hasText(pathUri.getHost())) {
                throw new CustomException(ErrorCode.INVALID_INPUT);
            }
            String path = pathUri.getRawPath();
            if (!StringUtils.hasText(path) || !path.startsWith("/")) {
                throw new CustomException(ErrorCode.INVALID_INPUT);
            }
            return buildPathWithQueryAndFragment(pathUri);
        } catch (URISyntaxException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private URI normalizeAppBaseUri() {
        try {
            URI baseUri = new URI(appBaseUrl);
            String scheme = baseUri.getScheme();
            String host = baseUri.getHost();
            if (!StringUtils.hasText(scheme) || !StringUtils.hasText(host)) {
                throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
            return baseUri;
        } catch (URISyntaxException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private boolean sameOrigin(URI left, URI right) {
        int leftPort = left.getPort() != -1 ? left.getPort() : defaultPort(left.getScheme());
        int rightPort = right.getPort() != -1 ? right.getPort() : defaultPort(right.getScheme());
        return StringUtils.hasText(left.getScheme())
                && left.getScheme().equalsIgnoreCase(right.getScheme())
                && StringUtils.hasText(left.getHost())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && leftPort == rightPort;
    }

    private int defaultPort(String scheme) {
        if ("https".equalsIgnoreCase(scheme)) {
            return 443;
        }
        if ("http".equalsIgnoreCase(scheme)) {
            return 80;
        }
        return -1;
    }

    private String buildPathWithQueryAndFragment(URI uri) {
        String path = uri.getRawPath();
        if (!StringUtils.hasText(path) || !path.startsWith("/")) {
            return DEFAULT_NOTIFICATION_PATH;
        }
        StringBuilder builder = new StringBuilder(path);
        if (StringUtils.hasText(uri.getRawQuery())) {
            builder.append('?').append(uri.getRawQuery());
        }
        if (StringUtils.hasText(uri.getRawFragment())) {
            builder.append('#').append(uri.getRawFragment());
        }
        return builder.toString();
    }

    private String toAbsoluteUrl(String deeplinkUrl) {
        URI appBaseUri = normalizeAppBaseUri();
        String safePath = StringUtils.hasText(deeplinkUrl) ? deeplinkUrl : DEFAULT_NOTIFICATION_PATH;
        if (safePath.startsWith("http://") || safePath.startsWith("https://")) {
            return safePath;
        }
        String base = appBaseUri.toString();
        if (base.endsWith("/") && safePath.startsWith("/")) {
            return base.substring(0, base.length() - 1) + safePath;
        }
        if (!base.endsWith("/") && !safePath.startsWith("/")) {
            return base + "/" + safePath;
        }
        return base + safePath;
    }
}
