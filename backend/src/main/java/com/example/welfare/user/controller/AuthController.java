package com.example.welfare.user.controller;

import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.user.dto.request.LoginRequest;
import com.example.welfare.user.dto.request.PasswordResetConfirmRequest;
import com.example.welfare.user.dto.request.PasswordResetRequest;
import com.example.welfare.user.dto.request.SignupRequest;
import com.example.welfare.user.dto.response.EmailAvailabilityResponse;
import com.example.welfare.user.dto.response.TokenResponse;
import com.example.welfare.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private final AuthService authService;

    @Value("${auth.refresh.cookie-secure:false}")
    private boolean cookieSecure;

    @GetMapping("/check-email")
    public ResponseEntity<ApiResponse<EmailAvailabilityResponse>> checkEmailAvailability(@RequestParam String email) {
        return ResponseEntity.ok(ApiResponse.success(authService.checkEmailAvailability(email)));
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Void>> signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<ApiResponse<Void>> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        authService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.confirmPasswordReset(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse token = authService.login(request);

        ResponseCookie refreshCookie = buildRefreshCookie(token.getRefreshToken(), 7 * 24 * 60 * 60L);
        TokenResponse body = TokenResponse.of(token.getAccessToken(), null);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success(body));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @RequestHeader(value = "X-Refresh-Token", required = false) String refreshTokenHeader,
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshTokenCookie) {

        String refreshToken = StringUtils.hasText(refreshTokenHeader) ? refreshTokenHeader : refreshTokenCookie;
        if (!StringUtils.hasText(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        TokenResponse token = authService.refresh(refreshToken);

        ResponseCookie refreshCookie = buildRefreshCookie(token.getRefreshToken(), 7 * 24 * 60 * 60L);
        TokenResponse body = TokenResponse.of(token.getAccessToken(), null);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success(body));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestHeader(value = "X-Refresh-Token", required = false) String refreshTokenHeader,
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshTokenCookie) {
        String refreshToken = StringUtils.hasText(refreshTokenHeader) ? refreshTokenHeader : refreshTokenCookie;

        if (StringUtils.hasText(refreshToken)) {
            authService.logoutByRefreshToken(refreshToken);
        } else if (authenticatedUser != null && authenticatedUser.hasUserKey()) {
            authService.logoutByUserKey(authenticatedUser.userKey());
        } else if (authenticatedUser != null && authenticatedUser.hasUserId()) {
            authService.logout(authenticatedUser.userId());
        }

        ResponseCookie clearCookie = buildRefreshCookie("", 0);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .body(ApiResponse.success(null));
    }

    private ResponseCookie buildRefreshCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/auth")
                .sameSite("Lax")
                .maxAge(maxAgeSeconds)
                .build();
    }
}
