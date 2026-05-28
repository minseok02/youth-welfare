package com.example.welfare.user.controller;

import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.user.dto.request.ChangePasswordRequest;
import com.example.welfare.user.dto.request.UpdatePrioritiesRequest;
import com.example.welfare.user.dto.request.UpdateProfileRequest;
import com.example.welfare.user.dto.request.WithdrawRequest;
import com.example.welfare.user.dto.response.ProfileResponse;
import com.example.welfare.user.service.UserAccountCommandService;
import com.example.welfare.user.service.UserBookmarkReadService;
import com.example.welfare.user.service.UserProfileCommandService;
import com.example.welfare.user.service.UserProfileReadService;
import com.example.welfare.user.service.UserRecentViewedPolicyReadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileReadService userProfileReadService;
    private final UserBookmarkReadService userBookmarkReadService;
    private final UserRecentViewedPolicyReadService userRecentViewedPolicyReadService;
    private final UserProfileCommandService userProfileCommandService;
    private final UserAccountCommandService userAccountCommandService;

    @GetMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(ApiResponse.success(userProfileReadService.getProfile(resolveUserId(authenticatedUser))));
    }

    @GetMapping("/bookmarks")
    public ResponseEntity<ApiResponse<List<PolicySummaryResponse>>> getBookmarks(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(ApiResponse.success(userBookmarkReadService.getBookmarks(resolveUserId(authenticatedUser))));
    }

    @GetMapping("/recent-viewed-policies")
    public ResponseEntity<ApiResponse<List<PolicySummaryResponse>>> getRecentViewedPolicies(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.success(
                userRecentViewedPolicyReadService.getRecentViewedPolicies(resolveUserId(authenticatedUser), limit)
        ));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<Void>> updateProfile(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody UpdateProfileRequest request) {
        userProfileCommandService.updateProfile(resolveUserId(authenticatedUser), request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/priorities")
    public ResponseEntity<ApiResponse<Void>> updatePriorities(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody UpdatePrioritiesRequest request) {
        userProfileCommandService.updatePriorities(resolveUserId(authenticatedUser), request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody ChangePasswordRequest request) {
        userAccountCommandService.changePassword(
                resolveUserId(authenticatedUser),
                request.getCurrentPassword(),
                request.getNewPassword()
        );
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/notifications/unsubscribe")
    public ResponseEntity<ApiResponse<Void>> unsubscribeNotifications(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        userAccountCommandService.unsubscribeNotifications(resolveUserId(authenticatedUser));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @Valid @RequestBody WithdrawRequest request) {
        userAccountCommandService.withdraw(
                resolveUserId(authenticatedUser),
                request.getPassword(),
                extractBearerToken(authorizationHeader)
        );
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private String extractBearerToken(String authorizationHeader) {
        if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }
        return null;
    }

    private Long resolveUserId(AuthenticatedUser authenticatedUser) {
        return authenticatedUser != null ? authenticatedUser.userId() : null;
    }
}
