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

    private final RedisTemplate<String, String> redisTemplate;
    private final int emailCheckMaxRequests;
    private final long emailCheckWindowSeconds;
    private final int emailVerificationSendMaxRequests;
    private final long emailVerificationSendWindowSeconds;

    public AuthRateLimitService(
            RedisTemplate<String, String> redisTemplate,
            @Value("${auth.rate-limit.email-check.max-requests:10}") int emailCheckMaxRequests,
            @Value("${auth.rate-limit.email-check.window-seconds:60}") long emailCheckWindowSeconds,
            @Value("${auth.rate-limit.email-verification-send.max-requests:5}") int emailVerificationSendMaxRequests,
            @Value("${auth.rate-limit.email-verification-send.window-seconds:300}") long emailVerificationSendWindowSeconds) {
        this.redisTemplate = redisTemplate;
        this.emailCheckMaxRequests = emailCheckMaxRequests;
        this.emailCheckWindowSeconds = emailCheckWindowSeconds;
        this.emailVerificationSendMaxRequests = emailVerificationSendMaxRequests;
        this.emailVerificationSendWindowSeconds = emailVerificationSendWindowSeconds;
    }

    public void checkEmailCheckLimit(String fingerprint) {
        checkLimit(EMAIL_CHECK_PREFIX + fingerprint, emailCheckMaxRequests, emailCheckWindowSeconds);
    }

    public void checkEmailVerificationSendLimit(String fingerprint) {
        checkLimit(EMAIL_VERIFICATION_SEND_PREFIX + fingerprint,
                emailVerificationSendMaxRequests,
                emailVerificationSendWindowSeconds);
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
