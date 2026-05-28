package com.example.welfare.policy.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
public class PolicyTrafficRateLimitService {

    private static final String SEARCH_RATE_LIMIT_PREFIX = "policy:rate-limit:search:";
    private static final String SUGGESTION_RATE_LIMIT_PREFIX = "policy:rate-limit:suggestion:";
    private static final String TRENDING_RATE_LIMIT_PREFIX = "policy:rate-limit:trending:";
    private static final String DETAIL_RATE_LIMIT_PREFIX = "policy:rate-limit:detail:";

    private final RedisTemplate<String, String> redisTemplate;
    private final int searchMaxRequests;
    private final long searchWindowSeconds;
    private final int suggestionMaxRequests;
    private final long suggestionWindowSeconds;
    private final int trendingMaxRequests;
    private final long trendingWindowSeconds;
    private final int detailMaxRequestsPerService;
    private final long detailWindowSeconds;

    public PolicyTrafficRateLimitService(
            RedisTemplate<String, String> redisTemplate,
            @Value("${policy.rate-limit.search.max-requests:60}") int searchMaxRequests,
            @Value("${policy.rate-limit.search.window-seconds:60}") long searchWindowSeconds,
            @Value("${policy.rate-limit.suggestion.max-requests:180}") int suggestionMaxRequests,
            @Value("${policy.rate-limit.suggestion.window-seconds:60}") long suggestionWindowSeconds,
            @Value("${policy.rate-limit.trending.max-requests:30}") int trendingMaxRequests,
            @Value("${policy.rate-limit.trending.window-seconds:60}") long trendingWindowSeconds,
            @Value("${policy.rate-limit.detail.max-requests-per-service:20}") int detailMaxRequestsPerService,
            @Value("${policy.rate-limit.detail.window-seconds:60}") long detailWindowSeconds) {
        this.redisTemplate = redisTemplate;
        this.searchMaxRequests = searchMaxRequests;
        this.searchWindowSeconds = searchWindowSeconds;
        this.suggestionMaxRequests = suggestionMaxRequests;
        this.suggestionWindowSeconds = suggestionWindowSeconds;
        this.trendingMaxRequests = trendingMaxRequests;
        this.trendingWindowSeconds = trendingWindowSeconds;
        this.detailMaxRequestsPerService = detailMaxRequestsPerService;
        this.detailWindowSeconds = detailWindowSeconds;
    }

    public void checkSearchLimit(String actorKey) {
        checkLimit(buildSearchRateLimitKey(actorKey), searchMaxRequests, searchWindowSeconds);
    }

    public void checkSuggestionLimit(String actorKey) {
        checkLimit(buildSuggestionRateLimitKey(actorKey), suggestionMaxRequests, suggestionWindowSeconds);
    }

    public void checkTrendingLimit(String actorKey) {
        checkLimit(buildTrendingRateLimitKey(actorKey), trendingMaxRequests, trendingWindowSeconds);
    }

    public void checkDetailLimit(String actorKey, Long serviceId) {
        checkLimit(buildDetailRateLimitKey(actorKey, serviceId), detailMaxRequestsPerService, detailWindowSeconds);
    }

    String buildSearchRateLimitKey(String actorKey) {
        return SEARCH_RATE_LIMIT_PREFIX + normalizeActorKey(actorKey);
    }

    String buildSuggestionRateLimitKey(String actorKey) {
        return SUGGESTION_RATE_LIMIT_PREFIX + normalizeActorKey(actorKey);
    }

    String buildTrendingRateLimitKey(String actorKey) {
        return TRENDING_RATE_LIMIT_PREFIX + normalizeActorKey(actorKey);
    }

    String buildDetailRateLimitKey(String actorKey, Long serviceId) {
        return DETAIL_RATE_LIMIT_PREFIX + normalizeActorKey(actorKey) + ":" + serviceId;
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
            throw new CustomException(ErrorCode.POLICY_RATE_LIMIT_EXCEEDED);
        }
    }

    private String normalizeActorKey(String actorKey) {
        if (!StringUtils.hasText(actorKey)) {
            return "anonymous";
        }
        return actorKey.trim();
    }

    private boolean hasNoExpiry(String key) {
        Long ttl = redisTemplate.getExpire(key);
        return ttl == null || ttl < 0;
    }
}
