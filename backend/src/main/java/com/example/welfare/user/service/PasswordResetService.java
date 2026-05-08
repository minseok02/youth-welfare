package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.notification.gateway.EmailClient;
import com.example.welfare.user.entity.AuthUser;
import com.example.welfare.user.util.EmailLookupKeyGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final String PASSWORD_RESET_TOKEN_PREFIX = "password-reset:";
    private static final String PASSWORD_RESET_USER_PREFIX = "password-reset:user:";
    private static final String PASSWORD_RESET_REQUEST_COOLDOWN_PREFIX = "password-reset:cooldown:";
    private static final String PASSWORD_RESET_SUBJECT = "[청년복지] 비밀번호 재설정 안내";

    private final AuthIdentityReadService authIdentityReadService;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, String> redisTemplate;
    private final EmailClient emailClient;
    private final UserCoreSyncService userCoreSyncService;
    private final ActiveUserReadService activeUserReadService;
    private final UserNotificationReadService userNotificationReadService;
    private final UserSessionRevocationService userSessionRevocationService;

    @Value("${auth.password-reset.expiration-minutes:30}")
    private long passwordResetExpirationMinutes;

    @Value("${auth.password-reset.request-cooldown-seconds:60}")
    private long passwordResetRequestCooldownSeconds;

    @Value("${app.base-url:http://localhost:5173}")
    private String appBaseUrl;

    public void requestPasswordReset(String rawEmail) {
        String email = EmailLookupKeyGenerator.normalize(rawEmail);
        authIdentityReadService.findByEmail(email)
                .filter(AuthUser::isActive)
                .flatMap(authUser -> activeUserReadService.findOptionalActiveUserByUserKey(authUser.getUserKey())
                        .map(user -> authUser.getUserKey()))
                .ifPresent(userKey -> {
                    if (!acquireResetRequestCooldown(email)) {
                        return;
                    }

                    String recipientEmail = userNotificationReadService.getNotificationEmailByUserKey(userKey);
                    String token = UUID.randomUUID().toString();
                    savePasswordResetToken(userKey, token);

                    boolean sent = emailClient.send(
                            recipientEmail,
                            PASSWORD_RESET_SUBJECT,
                            buildPasswordResetText(token)
                    );
                    if (!sent) {
                        clearPasswordResetToken(userKey, token);
                        clearResetRequestCooldown(email);
                        throw new CustomException(ErrorCode.PASSWORD_RESET_EMAIL_SEND_FAILED);
                    }
                });
    }

    @Transactional
    public void confirmPasswordReset(String token, String newPassword) {
        String resetToken = token == null ? "" : token.trim();
        if (!StringUtils.hasText(resetToken)) {
            throw new CustomException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        }

        String userKey = redisTemplate.opsForValue().get(passwordResetTokenKey(resetToken));
        if (!StringUtils.hasText(userKey)) {
            throw new CustomException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        }

        var user = activeUserReadService.findOptionalActiveUserByUserKey(userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));

        String latestToken = redisTemplate.opsForValue().get(passwordResetUserKey(userKey));
        if (!resetToken.equals(latestToken)) {
            throw new CustomException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        }

        user.updatePassword(passwordEncoder.encode(newPassword));
        user.resetLoginFail();
        userCoreSyncService.syncFromUser(user);
        clearPasswordResetToken(userKey, resetToken);
        userSessionRevocationService.revokeUserSessions(userKey, System.currentTimeMillis());
    }

    private void savePasswordResetToken(String userKey, String token) {
        String previousToken = redisTemplate.opsForValue().get(passwordResetUserKey(userKey));
        if (StringUtils.hasText(previousToken)) {
            redisTemplate.delete(passwordResetTokenKey(previousToken));
        }

        redisTemplate.opsForValue().set(
                passwordResetTokenKey(token),
                userKey,
                passwordResetExpirationMinutes,
                TimeUnit.MINUTES
        );
        redisTemplate.opsForValue().set(
                passwordResetUserKey(userKey),
                token,
                passwordResetExpirationMinutes,
                TimeUnit.MINUTES
        );
    }

    private void clearPasswordResetToken(String userKey, String token) {
        redisTemplate.delete(passwordResetTokenKey(token));
        redisTemplate.delete(passwordResetUserKey(userKey));
    }

    private String buildPasswordResetText(String token) {
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String resetUrl = appBaseUrl + "/reset-password?token=" + encodedToken;
        return """
                비밀번호 재설정을 요청하셨다면 아래 링크에서 새 비밀번호를 설정해주세요.

                %s

                이 링크는 %d분 동안만 유효합니다.
                본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.
                """.formatted(resetUrl, passwordResetExpirationMinutes);
    }

    private String passwordResetTokenKey(String token) {
        return PASSWORD_RESET_TOKEN_PREFIX + token;
    }

    private String passwordResetUserKey(String userKey) {
        return PASSWORD_RESET_USER_PREFIX + userKey;
    }

    private boolean acquireResetRequestCooldown(String email) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                passwordResetRequestCooldownKey(email),
                "1",
                passwordResetRequestCooldownSeconds,
                TimeUnit.SECONDS
        );
        return Boolean.TRUE.equals(acquired);
    }

    private void clearResetRequestCooldown(String email) {
        redisTemplate.delete(passwordResetRequestCooldownKey(email));
    }

    private String passwordResetRequestCooldownKey(String email) {
        return PASSWORD_RESET_REQUEST_COOLDOWN_PREFIX + EmailLookupKeyGenerator.hash(email);
    }
}
