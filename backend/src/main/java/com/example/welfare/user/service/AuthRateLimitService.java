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

    private final RedisTemplate<String, String> redisTemplate;
    private final int maxRequests;
    private final long windowSeconds;

    public AuthRateLimitService(
            RedisTemplate<String, String> redisTemplate,
            @Value("${auth.rate-limit.email-check.max-requests:10}") int maxRequests,
            @Value("${auth.rate-limit.email-check.window-seconds:60}") long windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
    }

    public void checkEmailCheckLimit(String fingerprint) {
        String key = EMAIL_CHECK_PREFIX + fingerprint;
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
