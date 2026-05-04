package com.example.welfare.notification.controller;

import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.global.util.JwtUtil;
import com.example.welfare.user.service.UserAccountCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final JwtUtil jwtUtil;
    private final UserAccountCommandService userAccountCommandService;

    @GetMapping("/unsubscribe")
    public ResponseEntity<ApiResponse<Void>> unsubscribe(@RequestParam String token) {
        String userKey = jwtUtil.getSubjectAllowExpired(token);
        userAccountCommandService.unsubscribeNotificationsByUserKey(userKey);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
