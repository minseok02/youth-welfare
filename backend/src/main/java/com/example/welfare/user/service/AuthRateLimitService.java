package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AuthRateLimitService {

    private static final String EMAIL_CHECK_PREFIX = "auth:rate-limit:email-check:";
    private static final String EMAIL_VERIFICATION_SEND_PREFIX = "auth:rate-limit:email-verification-send:";
    private static final String LOGIN_PREFIX = "auth:rate-limit:login:";
    private static final String PASSWORD_RESET_REQUEST_PREFIX = "auth:rate-limit:password-reset-request:";

    private final RedisTemplate<String, String> redisTemplate;
    private final int emailCheckMaxRequests;
    private final long emailCheckWindowSeconds;
    private final int emailVerificationSendMaxRequests;
    private final long emailVerificationSendWindowSeconds;
    private final int loginMaxRequests;
    private final long loginWindowSeconds;
    private final int passwordResetRequestMaxRequests;
    private final long passwordResetRequestWindowSeconds;

    public AuthRateLimitService(
            RedisTemplate<String, String> redisTemplate,
            @Value("${auth.rate-limit.email-check.max-requests:10}") int emailCheckMaxRequests,
            @Value("${auth.rate-limit.email-check.window-seconds:60}") long emailCheckWindowSeconds,
            @Value("${auth.rate-limit.email-verification-send.max-requests:5}") int emailVerificationSendMaxRequests,
            @Value("${auth.rate-limit.email-verification-send.window-seconds:300}") long emailVerificationSendWindowSeconds,
            @Value("${auth.rate-limit.login.max-requests:20}") int loginMaxRequests,
            @Value("${auth.rate-limit.login.window-seconds:300}") long loginWindowSeconds,
            @Value("${auth.rate-limit.password-reset-request.max-requests:5}") int passwordResetRequestMaxRequests,
            @Value("${auth.rate-limit.password-reset-request.window-seconds:300}") long passwordResetRequestWindowSeconds) {
        this.redisTemplate = redisTemplate;
        this.emailCheckMaxRequests = emailCheckMaxRequests;
        this.emailCheckWindowSeconds = emailCheckWindowSeconds;
        this.emailVerificationSendMaxRequests = emailVerificationSendMaxRequests;
        this.emailVerificationSendWindowSeconds = emailVerificationSendWindowSeconds;
        this.loginMaxRequests = loginMaxRequests;
        this.loginWindowSeconds = loginWindowSeconds;
        this.passwordResetRequestMaxRequests = passwordResetRequestMaxRequests;
        this.passwordResetRequestWindowSeconds = passwordResetRequestWindowSeconds;
    }

    public void checkEmailCheckLimit(String fingerprint) {
        checkLimit(EMAIL_CHECK_PREFIX + fingerprint, emailCheckMaxRequests, emailCheckWindowSeconds);
    }

    public void checkEmailVerificationSendLimit(String fingerprint) {
        checkLimit(EMAIL_VERIFICATION_SEND_PREFIX + fingerprint,
                emailVerificationSendMaxRequests,
                emailVerificationSendWindowSeconds);
    }

    public void checkLoginLimit(String fingerprint) {
        checkLimit(LOGIN_PREFIX + fingerprint, loginMaxRequests, loginWindowSeconds);
    }

    public void checkPasswordResetRequestLimit(String fingerprint) {
        checkLimit(PASSWORD_RESET_REQUEST_PREFIX + fingerprint,
                passwordResetRequestMaxRequests,
                passwordResetRequestWindowSeconds);
    }

    private void checkLimit(String key, int maxRequests, long windowSeconds) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        if (count == 1L || hasNoExpiry(key)) {
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }
        if (count > maxRequests) {
            throw new CustomException(ErrorCode.AUTH_RATE_LIMIT_EXCEEDED);
        }
    }

    private boolean hasNoExpiry(String key) {
        Long ttl = redisTemplate.getExpire(key);
        return ttl == null || ttl < 0;
    }
}
