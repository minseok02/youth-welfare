package com.example.welfare.recommend.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
public class RecommendationRefreshRateLimitService {

    private static final String PERSONAL_REFRESH_RATE_LIMIT_PREFIX = "recommend:rate-limit:refresh:personal:";
    private static final String SHARED_REFRESH_RATE_LIMIT_PREFIX = "recommend:rate-limit:refresh:shared:";

    private final RedisTemplate<String, String> redisTemplate;
    private final int personalMaxRequests;
    private final long personalWindowSeconds;
    private final int sharedMaxRequests;
    private final long sharedWindowSeconds;

    public RecommendationRefreshRateLimitService(
            RedisTemplate<String, String> redisTemplate,
            @Value("${recommend.rate-limit.personal-refresh.max-requests:1}") int personalMaxRequests,
            @Value("${recommend.rate-limit.personal-refresh.window-seconds:600}") long personalWindowSeconds,
            @Value("${recommend.rate-limit.shared-refresh.max-requests:3}") int sharedMaxRequests,
            @Value("${recommend.rate-limit.shared-refresh.window-seconds:60}") long sharedWindowSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.personalMaxRequests = personalMaxRequests;
        this.personalWindowSeconds = personalWindowSeconds;
        this.sharedMaxRequests = sharedMaxRequests;
        this.sharedWindowSeconds = sharedWindowSeconds;
    }

    public void checkRefreshLimit(String userKey, boolean personal) {
        if (personal) {
            checkLimit(buildPersonalRefreshRateLimitKey(userKey), personalMaxRequests, personalWindowSeconds);
            return;
        }
        checkLimit(buildSharedRefreshRateLimitKey(userKey), sharedMaxRequests, sharedWindowSeconds);
    }

    String buildPersonalRefreshRateLimitKey(String userKey) {
        return PERSONAL_REFRESH_RATE_LIMIT_PREFIX + normalizeUserKey(userKey);
    }

    String buildSharedRefreshRateLimitKey(String userKey) {
        return SHARED_REFRESH_RATE_LIMIT_PREFIX + normalizeUserKey(userKey);
    }

    private void checkLimit(String key, int maxRequests, long windowSeconds) {
        Long requestCount = redisTemplate.opsForValue().increment(key);
        if (requestCount == null) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        if (requestCount == 1L || hasNoExpiry(key)) {
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }

        if (requestCount > maxRequests) {
            throw new CustomException(ErrorCode.RECOMMENDATION_REFRESH_RATE_LIMIT_EXCEEDED);
        }
    }

    private String normalizeUserKey(String userKey) {
        if (!StringUtils.hasText(userKey)) {
            return "anonymous";
        }
        return userKey.trim();
    }

    private boolean hasNoExpiry(String key) {
        Long ttl = redisTemplate.getExpire(key);
        return ttl == null || ttl < 0;
    }
}
