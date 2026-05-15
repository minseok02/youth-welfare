package com.example.welfare.notification.controller;

import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.global.util.JwtUtil;
import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.notification.dto.UserAlertResponse;
import com.example.welfare.notification.dto.UserAlertUnreadCountResponse;
import com.example.welfare.notification.service.UserAlertCommandService;
import com.example.welfare.notification.service.UserAlertReadService;
import com.example.welfare.user.service.ActiveUserReadService;
import com.example.welfare.user.service.UserAccountCommandService;
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
    private final ActiveUserReadService activeUserReadService;

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
