package com.example.welfare.user.controller;

import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.web.ClientFingerprintService;
import com.example.welfare.user.dto.request.LoginRequest;
import com.example.welfare.user.dto.request.PasswordResetConfirmRequest;
import com.example.welfare.user.dto.request.PasswordResetRequest;
import com.example.welfare.user.dto.request.SignupRequest;
import com.example.welfare.user.dto.response.EmailAvailabilityResponse;
import com.example.welfare.user.dto.response.TokenResponse;
import com.example.welfare.user.service.AuthAvailabilityService;
import com.example.welfare.user.service.AuthLoginService;
import com.example.welfare.user.service.AuthRateLimitService;
import com.example.welfare.user.service.AuthSessionService;
import com.example.welfare.user.service.AuthSignupService;
import com.example.welfare.user.service.EmailVerificationService;
import com.example.welfare.user.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private final AuthAvailabilityService authAvailabilityService;
    private final AuthRateLimitService authRateLimitService;
    private final ClientFingerprintService clientFingerprintService;
    private final EmailVerificationService emailVerificationService;
    private final AuthSignupService authSignupService;
    private final AuthLoginService authLoginService;
    private final AuthSessionService authSessionService;
    private final PasswordResetService passwordResetService;

    @Value("${auth.refresh.cookie-secure:true}")
    private boolean cookieSecure;

    @GetMapping("/check-email")
    public ResponseEntity<ApiResponse<EmailAvailabilityResponse>> checkEmailAvailability(
            @RequestParam String email,
            HttpServletRequest request) {
        authRateLimitService.checkEmailCheckLimit(clientFingerprintService.build(request));
        return ResponseEntity.ok(ApiResponse.success(authAvailabilityService.checkEmailAvailability(email)));
    }

    @PostMapping("/email-verification/send")
    public ResponseEntity<ApiResponse<Void>> sendEmailVerificationCode(
            @RequestParam @Email String email,
            HttpServletRequest request) {
        authRateLimitService.checkEmailVerificationSendLimit(clientFingerprintService.build(request));
        emailVerificationService.sendCode(email);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/email-verification/verify")
    public ResponseEntity<ApiResponse<Void>> verifyEmailCode(
            @RequestParam String email,
            @RequestParam String code) {
        emailVerificationService.verifyCode(email, code);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Void>> signup(@Valid @RequestBody SignupRequest request) {
        authSignupService.signup(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<ApiResponse<Void>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest httpServletRequest) {
        authRateLimitService.checkPasswordResetRequestLimit(clientFingerprintService.build(httpServletRequest));
        passwordResetService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmPasswordReset(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpServletRequest) {
        authRateLimitService.checkLoginLimit(clientFingerprintService.build(httpServletRequest));
        TokenResponse token = authLoginService.login(request);

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

        TokenResponse token = authSessionService.refresh(refreshToken);

        ResponseCookie refreshCookie = buildRefreshCookie(token.getRefreshToken(), 7 * 24 * 60 * 60L);
        TokenResponse body = TokenResponse.of(token.getAccessToken(), null);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success(body));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestHeader(value = "X-Refresh-Token", required = false) String refreshTokenHeader,
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshTokenCookie) {
        String refreshToken = StringUtils.hasText(refreshTokenHeader) ? refreshTokenHeader : refreshTokenCookie;
        String accessToken = extractBearerToken(authorizationHeader);

        if (StringUtils.hasText(refreshToken)) {
            authSessionService.logoutByRefreshToken(refreshToken, accessToken);
        } else if (authenticatedUser != null && authenticatedUser.hasUserKey()) {
            authSessionService.logoutByUserKey(authenticatedUser.userKey(), accessToken);
        } else if (authenticatedUser != null && authenticatedUser.hasUserId()) {
            authSessionService.logout(authenticatedUser.userId(), accessToken);
        }

        ResponseCookie clearCookie = buildRefreshCookie("", 0);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .body(ApiResponse.success(null));
    }

    private String extractBearerToken(String authorizationHeader) {
        if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }
        return null;
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
