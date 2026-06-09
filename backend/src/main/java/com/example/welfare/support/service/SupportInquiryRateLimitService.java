package com.example.welfare.support.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.RedisKeyHash;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
public class SupportInquiryRateLimitService {

    private static final String INQUIRY_RATE_LIMIT_PREFIX = "support:rate-limit:inquiry:";

    private final RedisTemplate<String, String> redisTemplate;
    private final int maxRequests;
    private final long windowSeconds;

    public SupportInquiryRateLimitService(
            RedisTemplate<String, String> redisTemplate,
            @Value("${support.rate-limit.inquiry.max-requests:5}") int maxRequests,
            @Value("${support.rate-limit.inquiry.window-seconds:300}") long windowSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
    }

    public void checkInquiryLimit(String actorKey) {
        String key = INQUIRY_RATE_LIMIT_PREFIX + normalizeActorKey(actorKey);
        Long requestCount = redisTemplate.opsForValue().increment(key);
        if (requestCount == null) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        if (requestCount == 1L || hasNoExpiry(key)) {
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }
        if (requestCount > maxRequests) {
            throw new CustomException(ErrorCode.SUPPORT_RATE_LIMIT_EXCEEDED);
        }
    }

    String buildInquiryRateLimitKey(String actorKey) {
        return INQUIRY_RATE_LIMIT_PREFIX + normalizeActorKey(actorKey);
    }

    private String normalizeActorKey(String actorKey) {
        if (!StringUtils.hasText(actorKey)) {
            return "anonymous";
        }
        return RedisKeyHash.sha256Hex(actorKey);
    }

    private boolean hasNoExpiry(String key) {
        Long ttl = redisTemplate.getExpire(key);
        return ttl == null || ttl < 0;
    }
}
