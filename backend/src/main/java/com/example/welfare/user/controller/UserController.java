package com.example.welfare.user.controller;

import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.user.dto.request.ChangePasswordRequest;
import com.example.welfare.user.dto.request.UpdatePrioritiesRequest;
import com.example.welfare.user.dto.request.UpdateProfileRequest;
import com.example.welfare.user.dto.request.WithdrawRequest;
import com.example.welfare.user.dto.response.ProfileResponse;
import com.example.welfare.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getProfile(userId)));
    }

    @GetMapping("/bookmarks")
    public ResponseEntity<ApiResponse<List<PolicySummaryResponse>>> getBookmarks(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getBookmarks(userId)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<Void>> updateProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        userService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/priorities")
    public ResponseEntity<ApiResponse<Void>> updatePriorities(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdatePrioritiesRequest request) {
        userService.updatePriorities(userId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(userId, request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/notifications/unsubscribe")
    public ResponseEntity<ApiResponse<Void>> unsubscribeNotifications(@AuthenticationPrincipal Long userId) {
        userService.unsubscribeNotifications(userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody WithdrawRequest request) {
        userService.withdraw(userId, request.getPassword());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
