package com.example.welfare.notification.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
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

    @Value("${app.base-url:http://localhost:5173}")
    private String appBaseUrl;

    @Transactional
    public void sendRecommendationDigest(String userKey, List<UserRecommendation> recommendations) {
        dispatchContent(userKey, recommendationDigestContentService.build(recommendations));
    }

    @Transactional
    public void sendDeadlineReminder(String userKey, List<WelfareService> services, int days) {
        dispatchContent(userKey, deadlineReminderContentService.build(services, days));
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

        NotificationContent content = buildTestContent(request);

        int sentCount = 0;
        int disabledCount = 0;
        int failedCount = 0;
        for (WebPushSubscription subscription : subscriptions) {
            WebPushSendResult result;
            try {
                result = webPushSenderClient.send(subscription, content);
            } catch (RuntimeException | LinkageError e) {
                log.warn("[WebPushDispatchService] unexpected web push test send failure endpointHost={}: {}",
                        webPushEndpointPolicyService.describeEndpointForLog(subscription.getEndpoint()),
                        e.getMessage());
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

    private NotificationContent buildTestContent(WebPushTestSendRequest request) {
        String deeplinkUrl = sanitizeTestPushUrl(request.getUrl());
        return new NotificationContent(
                request.getTitle(),
                request.getBody(),
                deeplinkUrl,
                toAbsoluteUrl(deeplinkUrl)
        );
    }

    private void dispatchContent(String userKey, NotificationContent content) {
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

        for (WebPushSubscription subscription : subscriptions) {
            WebPushSendResult result;
            try {
                result = webPushSenderClient.send(subscription, content);
            } catch (RuntimeException | LinkageError e) {
                log.warn("[WebPushDispatchService] unexpected web push send failure endpointHost={}: {}",
                        webPushEndpointPolicyService.describeEndpointForLog(subscription.getEndpoint()),
                        e.getMessage());
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
