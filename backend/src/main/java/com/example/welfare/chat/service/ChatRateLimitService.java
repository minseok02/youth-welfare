package com.example.welfare.chat.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class ChatRateLimitService {

    private static final String MESSAGE_RATE_LIMIT_PREFIX = "chat:rate-limit:message:";

    private final RedisTemplate<String, String> redisTemplate;
    private final int maxRequests;
    private final long windowSeconds;

    public ChatRateLimitService(
            RedisTemplate<String, String> redisTemplate,
            @Value("${chat.rate-limit.max-requests:5}") int maxRequests,
            @Value("${chat.rate-limit.window-seconds:60}") long windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
    }

    public void checkMessageSendLimit(Long userId) {
        Long requestCount = redisTemplate.opsForValue().increment(buildMessageRateLimitKey(userId));
        if (requestCount == null) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        String key = buildMessageRateLimitKey(userId);
        if (requestCount == 1L || hasNoExpiry(key)) {
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }

        if (requestCount > maxRequests) {
            throw new CustomException(ErrorCode.CHAT_RATE_LIMIT_EXCEEDED);
        }
    }

    String buildMessageRateLimitKey(Long userId) {
        return MESSAGE_RATE_LIMIT_PREFIX + userId;
    }

    private boolean hasNoExpiry(String key) {
        Long ttl = redisTemplate.getExpire(key);
        return ttl == null || ttl < 0;
    }
}
