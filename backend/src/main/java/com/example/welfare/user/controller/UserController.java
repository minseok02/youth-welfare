package com.example.welfare.user.controller;

import com.example.welfare.global.auth.AuthenticatedUser;
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
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(ApiResponse.success(userService.getProfile(resolveUserId(authenticatedUser))));
    }

    @GetMapping("/bookmarks")
    public ResponseEntity<ApiResponse<List<PolicySummaryResponse>>> getBookmarks(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(ApiResponse.success(userService.getBookmarks(resolveUserId(authenticatedUser))));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<Void>> updateProfile(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody UpdateProfileRequest request) {
        userService.updateProfile(resolveUserId(authenticatedUser), request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/priorities")
    public ResponseEntity<ApiResponse<Void>> updatePriorities(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody UpdatePrioritiesRequest request) {
        userService.updatePriorities(resolveUserId(authenticatedUser), request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(resolveUserId(authenticatedUser), request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/notifications/unsubscribe")
    public ResponseEntity<ApiResponse<Void>> unsubscribeNotifications(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        userService.unsubscribeNotifications(resolveUserId(authenticatedUser));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody WithdrawRequest request) {
        userService.withdraw(resolveUserId(authenticatedUser), request.getPassword());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private Long resolveUserId(AuthenticatedUser authenticatedUser) {
        return authenticatedUser != null ? authenticatedUser.userId() : null;
    }
}
