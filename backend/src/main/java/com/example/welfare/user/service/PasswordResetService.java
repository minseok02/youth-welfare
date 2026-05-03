package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.notification.gateway.EmailClient;
import com.example.welfare.user.entity.AuthUser;
import com.example.welfare.user.repository.AuthUserRepository;
import com.example.welfare.user.repository.UserPiiReadWriteRepository;
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
    private static final String PASSWORD_RESET_SUBJECT = "[청년복지] 비밀번호 재설정 안내";

    private final AuthUserRepository authUserRepository;
    private final UserPiiReadWriteRepository userPiiReadWriteRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, String> redisTemplate;
    private final EmailClient emailClient;
    private final UserCoreSyncService userCoreSyncService;
    private final AesEncryptUtil aesEncryptUtil;
    private final AuthTokenService authTokenService;
    private final UserReadService userReadService;

    @Value("${auth.password-reset.expiration-minutes:30}")
    private long passwordResetExpirationMinutes;

    @Value("${app.base-url:http://localhost:5173}")
    private String appBaseUrl;

    @Transactional
    public void requestPasswordReset(String rawEmail) {
        String email = EmailLookupKeyGenerator.normalize(rawEmail);
        authUserRepository.findByEmailLookupHash(EmailLookupKeyGenerator.hash(email))
                .filter(AuthUser::isActive)
                .flatMap(authUser -> userReadService.findOptionalActiveUserByUserKey(authUser.getUserKey())
                        .map(user -> authUser.getUserKey()))
                .ifPresent(userKey -> {
                    String recipientEmail = resolvePasswordResetRecipient(userKey);
                    String token = UUID.randomUUID().toString();
                    savePasswordResetToken(userKey, token);

                    boolean sent = emailClient.send(
                            recipientEmail,
                            PASSWORD_RESET_SUBJECT,
                            buildPasswordResetText(token)
                    );
                    if (!sent) {
                        clearPasswordResetToken(userKey, token);
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

        var user = userReadService.findOptionalActiveUserByUserKey(userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));

        String latestToken = redisTemplate.opsForValue().get(passwordResetUserKey(userKey));
        if (!resetToken.equals(latestToken)) {
            throw new CustomException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        }

        user.updatePassword(passwordEncoder.encode(newPassword));
        user.resetLoginFail();
        userCoreSyncService.syncFromUser(user);
        clearPasswordResetToken(userKey, resetToken);
        authTokenService.invalidateRefreshToken(userKey);
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

    private String resolvePasswordResetRecipient(String userKey) {
        var userPii = userPiiReadWriteRepository.findByUserKey(userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.PASSWORD_RESET_EMAIL_SEND_FAILED));
        String recipientEmail = aesEncryptUtil.decrypt(userPii.emailEnc());
        if (!StringUtils.hasText(recipientEmail)) {
            throw new CustomException(ErrorCode.PASSWORD_RESET_EMAIL_SEND_FAILED);
        }
        return recipientEmail;
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
}
