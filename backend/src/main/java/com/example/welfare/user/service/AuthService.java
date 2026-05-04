package com.example.welfare.user.service;

import com.example.welfare.chat.service.ChatSessionCleanupService;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.user.dto.request.LoginRequest;
import com.example.welfare.user.dto.request.SignupRequest;
import com.example.welfare.user.dto.response.EmailAvailabilityResponse;
import com.example.welfare.user.dto.response.TokenResponse;
import com.example.welfare.user.entity.AuthUser;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.util.EmailLookupKeyGenerator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_LOGIN_FAIL = 5;
    private static final int LOCK_MINUTES = 30;

    private final AuthIdentityReadService authIdentityReadService;
    private final PasswordEncoder passwordEncoder;
    private final UserRegistrationService userRegistrationService;
    private final UserCoreSyncService userCoreSyncService;
    private final AuthTokenService authTokenService;
    private final PasswordResetService passwordResetService;
    private final UserReadService userReadService;

    @Value("${security.admin-emails:}")
    private String adminEmailsProperty;

    private Set<String> adminEmails = Set.of();

    @PostConstruct
    void initAdminEmails() {
        adminEmails = Arrays.stream(adminEmailsProperty.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(email -> email.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional(readOnly = true)
    public EmailAvailabilityResponse checkEmailAvailability(String email) {
        return new EmailAvailabilityResponse(!authIdentityReadService.existsByEmail(email));
    }

    @Transactional
    public void signup(SignupRequest request) {
        if (isAdminEmail(request.getEmail())) {
            throw new CustomException(ErrorCode.ADMIN_EMAIL_SIGNUP_FORBIDDEN);
        }

        if (authIdentityReadService.existsByEmail(request.getEmail())) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }

        userRegistrationService.register(request, passwordEncoder.encode(request.getPassword()));
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String normalizedEmail = EmailLookupKeyGenerator.normalize(request.getEmail());
        AuthUser authUser = authIdentityReadService.findByEmail(normalizedEmail)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        if (!authUser.isActive()) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }

        User user = userReadService.getActiveUserByUserKey(authUser.getUserKey());

        if (authUser.getLockedUntil() != null && LocalDateTime.now().isBefore(authUser.getLockedUntil())) {
            throw new CustomException(ErrorCode.ACCOUNT_LOCKED);
        }

        if (!passwordEncoder.matches(request.getPassword(), authUser.getPasswordHash())) {
            user.increaseLoginFailCount();
            if (user.getLoginFailCount() >= MAX_LOGIN_FAIL) {
                user.lock(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
                log.warn("Account locked: userId={}", user.getId());
            }
            userCoreSyncService.syncFromUser(user);
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.resetLoginFail();
        userCoreSyncService.syncFromUser(user);

        return authTokenService.issueTokens(authUser.getUserKey(), user.getId(), resolveRoles(normalizedEmail));
    }

    @Transactional
    public void requestPasswordReset(String rawEmail) {
        passwordResetService.requestPasswordReset(rawEmail);
    }

    @Transactional
    public void confirmPasswordReset(String token, String newPassword) {
        passwordResetService.confirmPasswordReset(token, newPassword);
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        return authTokenService.refresh(refreshToken, this::resolveRoles);
    }

    @Transactional
    public void logout(Long userId, String accessToken) {
        authTokenService.logout(userId, accessToken);
    }

    @Transactional
    public void logoutByUserKey(String userKey, String accessToken) {
        authTokenService.logoutByUserKey(userKey, accessToken);
    }

    @Transactional
    public void logoutByRefreshToken(String refreshToken, String accessToken) {
        authTokenService.logoutByRefreshToken(refreshToken, accessToken);
    }

    private boolean isAdminEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return false;
        }
        return adminEmails.contains(EmailLookupKeyGenerator.normalize(email));
    }

    private List<String> resolveRoles(String email) {
        if (isAdminEmail(email)) {
            return List.of("ROLE_USER", "ROLE_ADMIN");
        }
        return List.of("ROLE_USER");
    }
}
