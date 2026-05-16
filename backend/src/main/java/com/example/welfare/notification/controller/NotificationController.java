package com.example.welfare.notification.controller;

import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.global.util.JwtUtil;
import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.notification.dto.UserAlertResponse;
import com.example.welfare.notification.dto.UserAlertUnreadCountResponse;
import com.example.welfare.notification.dto.NotificationDigestTestDispatchResponse;
import com.example.welfare.notification.dto.WebPushPublicKeyResponse;
import com.example.welfare.notification.dto.WebPushSubscriptionRequest;
import com.example.welfare.notification.dto.WebPushSubscriptionResponse;
import com.example.welfare.notification.dto.WebPushTestSendRequest;
import com.example.welfare.notification.dto.WebPushTestSendResponse;
import com.example.welfare.notification.dto.NotificationTarget;
import com.example.welfare.notification.service.NotificationDispatchService;
import com.example.welfare.notification.service.UserAlertCommandService;
import com.example.welfare.notification.service.UserAlertReadService;
import com.example.welfare.notification.service.WebPushDispatchService;
import com.example.welfare.notification.service.WebPushSubscriptionCommandService;
import com.example.welfare.notification.service.WebPushSubscriptionReadService;
import com.example.welfare.user.service.ActiveUserReadService;
import com.example.welfare.user.service.UserAccountCommandService;
import com.example.welfare.user.service.UserNotificationReadService;
import com.example.welfare.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final JwtUtil jwtUtil;
    private final UserAccountCommandService userAccountCommandService;
    private final UserAlertReadService userAlertReadService;
    private final UserAlertCommandService userAlertCommandService;
    private final NotificationDispatchService notificationDispatchService;
    private final WebPushSubscriptionReadService webPushSubscriptionReadService;
    private final WebPushSubscriptionCommandService webPushSubscriptionCommandService;
    private final WebPushDispatchService webPushDispatchService;
    private final ActiveUserReadService activeUserReadService;
    private final UserNotificationReadService userNotificationReadService;

    @GetMapping("/unsubscribe")
    public ResponseEntity<ApiResponse<Void>> unsubscribe(@RequestParam String token) {
        String userKey = jwtUtil.getSubjectAllowExpired(token);
        userAccountCommandService.unsubscribeNotificationsByUserKey(userKey);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<UserAlertResponse>>> getMyAlerts(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(ApiResponse.success(
                userAlertReadService.getRecentAlerts(resolveUserKey(authenticatedUser))
        ));
    }

    @GetMapping("/me/unread-count")
    public ResponseEntity<ApiResponse<UserAlertUnreadCountResponse>> getMyUnreadCount(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(ApiResponse.success(
                userAlertReadService.getUnreadCount(resolveUserKey(authenticatedUser))
        ));
    }

    @GetMapping("/push-public-key")
    public ResponseEntity<ApiResponse<WebPushPublicKeyResponse>> getWebPushPublicKey() {
        return ResponseEntity.ok(ApiResponse.success(webPushSubscriptionReadService.getPublicKey()));
    }

    @GetMapping("/push-subscriptions/me")
    public ResponseEntity<ApiResponse<List<WebPushSubscriptionResponse>>> getMyPushSubscriptions(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(ApiResponse.success(
                webPushSubscriptionReadService.getMySubscriptions(resolveUserKey(authenticatedUser))
        ));
    }

    @PostMapping("/push-subscriptions")
    public ResponseEntity<ApiResponse<WebPushSubscriptionResponse>> registerPushSubscription(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody WebPushSubscriptionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                webPushSubscriptionCommandService.register(resolveUserKey(authenticatedUser), request)
        ));
    }

    @DeleteMapping("/push-subscriptions/{subscriptionId}")
    public ResponseEntity<ApiResponse<Void>> deletePushSubscription(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long subscriptionId) {
        webPushSubscriptionCommandService.delete(resolveUserKey(authenticatedUser), subscriptionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/push-test-send")
    public ResponseEntity<ApiResponse<WebPushTestSendResponse>> sendPushTestMessage(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody WebPushTestSendRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                webPushDispatchService.sendTestMessage(resolveUserKey(authenticatedUser), request)
        ));
    }

    @PostMapping("/digest-test-dispatch")
    public ResponseEntity<ApiResponse<NotificationDigestTestDispatchResponse>> sendDigestTestDispatch(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        String userKey = resolveUserKey(authenticatedUser);
        User user = activeUserReadService.getActiveUserByUserKey(userKey);
        String email = userNotificationReadService.getNotificationEmailByUserKey(userKey);
        NotificationTarget target = new NotificationTarget(
                user.getId(),
                userKey,
                email,
                User.NotificationPeriod.NONE,
                user.isNotificationEmailYn(),
                user.isNotificationInAppYn(),
                user.isNotificationWebPushYn(),
                user.getNotificationMinScore(),
                user.getDisplayCount()
        );
        NotificationDispatchService.NotificationDispatchResult result =
                notificationDispatchService.sendTopRecommendations(target);
        return ResponseEntity.ok(ApiResponse.success(new NotificationDigestTestDispatchResponse(
                userKey,
                email,
                target.notificationPeriod().name(),
                target.notificationMinScore(),
                target.displayCount(),
                result.status().name(),
                result.recommendationCount(),
                result.message()
        )));
    }

    @PatchMapping("/{alertId}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long alertId) {
        userAlertCommandService.markRead(resolveUserKey(authenticatedUser), alertId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/{alertId}/hide")
    public ResponseEntity<ApiResponse<Void>> hide(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long alertId) {
        userAlertCommandService.hide(resolveUserKey(authenticatedUser), alertId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private String resolveUserKey(AuthenticatedUser authenticatedUser) {
        if (authenticatedUser != null && StringUtils.hasText(authenticatedUser.userKey())) {
            return authenticatedUser.userKey();
        }
        Long userId = authenticatedUser != null ? authenticatedUser.userId() : null;
        return activeUserReadService.getActiveUserContext(userId).userKey();
    }
}
