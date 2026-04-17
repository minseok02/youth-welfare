package com.example.welfare.notification.controller;

import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.global.util.JwtUtil;
import com.example.welfare.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final JwtUtil jwtUtil;
    private final UserService userService;

    @GetMapping("/unsubscribe")
    public ResponseEntity<ApiResponse<Void>> unsubscribe(@RequestParam String token) {
        Long userId = jwtUtil.getUserIdAllowExpired(token);
        userService.unsubscribeNotifications(userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
