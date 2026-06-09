package com.example.welfare.policy.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.RedisKeyHash;
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
    private static final String LIST_RATE_LIMIT_PREFIX = "policy:rate-limit:list:";
    private static final String RANKING_RATE_LIMIT_PREFIX = "policy:rate-limit:ranking:";
    private static final String ERROR_REPORT_RATE_LIMIT_PREFIX = "policy:rate-limit:error-report:";

    private final RedisTemplate<String, String> redisTemplate;
    private final int searchMaxRequests;
    private final long searchWindowSeconds;
    private final int suggestionMaxRequests;
    private final long suggestionWindowSeconds;
    private final int trendingMaxRequests;
    private final long trendingWindowSeconds;
    private final int detailMaxRequestsPerService;
    private final long detailWindowSeconds;
    private final int listMaxRequests;
    private final long listWindowSeconds;
    private final int rankingMaxRequests;
    private final long rankingWindowSeconds;
    private final int errorReportMaxRequests;
    private final long errorReportWindowSeconds;

    public PolicyTrafficRateLimitService(
            RedisTemplate<String, String> redisTemplate,
            @Value("${policy.rate-limit.search.max-requests:60}") int searchMaxRequests,
            @Value("${policy.rate-limit.search.window-seconds:60}") long searchWindowSeconds,
            @Value("${policy.rate-limit.suggestion.max-requests:180}") int suggestionMaxRequests,
            @Value("${policy.rate-limit.suggestion.window-seconds:60}") long suggestionWindowSeconds,
            @Value("${policy.rate-limit.trending.max-requests:30}") int trendingMaxRequests,
            @Value("${policy.rate-limit.trending.window-seconds:60}") long trendingWindowSeconds,
            @Value("${policy.rate-limit.detail.max-requests-per-service:20}") int detailMaxRequestsPerService,
            @Value("${policy.rate-limit.detail.window-seconds:60}") long detailWindowSeconds,
            @Value("${policy.rate-limit.list.max-requests:60}") int listMaxRequests,
            @Value("${policy.rate-limit.list.window-seconds:60}") long listWindowSeconds,
            @Value("${policy.rate-limit.ranking.max-requests:30}") int rankingMaxRequests,
            @Value("${policy.rate-limit.ranking.window-seconds:60}") long rankingWindowSeconds,
            @Value("${policy.rate-limit.error-report.max-requests:5}") int errorReportMaxRequests,
            @Value("${policy.rate-limit.error-report.window-seconds:300}") long errorReportWindowSeconds) {
        this.redisTemplate = redisTemplate;
        this.searchMaxRequests = searchMaxRequests;
        this.searchWindowSeconds = searchWindowSeconds;
        this.suggestionMaxRequests = suggestionMaxRequests;
        this.suggestionWindowSeconds = suggestionWindowSeconds;
        this.trendingMaxRequests = trendingMaxRequests;
        this.trendingWindowSeconds = trendingWindowSeconds;
        this.detailMaxRequestsPerService = detailMaxRequestsPerService;
        this.detailWindowSeconds = detailWindowSeconds;
        this.listMaxRequests = listMaxRequests;
        this.listWindowSeconds = listWindowSeconds;
        this.rankingMaxRequests = rankingMaxRequests;
        this.rankingWindowSeconds = rankingWindowSeconds;
        this.errorReportMaxRequests = errorReportMaxRequests;
        this.errorReportWindowSeconds = errorReportWindowSeconds;
    }

    public void checkListLimit(String actorKey) {
        checkLimit(buildListRateLimitKey(actorKey), listMaxRequests, listWindowSeconds);
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

    public void checkRankingLimit(String actorKey) {
        checkLimit(buildRankingRateLimitKey(actorKey), rankingMaxRequests, rankingWindowSeconds);
    }

    public void checkErrorReportLimit(String actorKey, Long serviceId) {
        checkLimit(buildErrorReportRateLimitKey(actorKey, serviceId), errorReportMaxRequests, errorReportWindowSeconds);
    }

    String buildListRateLimitKey(String actorKey) {
        return LIST_RATE_LIMIT_PREFIX + normalizeActorKey(actorKey);
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

    String buildRankingRateLimitKey(String actorKey) {
        return RANKING_RATE_LIMIT_PREFIX + normalizeActorKey(actorKey);
    }

    String buildErrorReportRateLimitKey(String actorKey, Long serviceId) {
        return ERROR_REPORT_RATE_LIMIT_PREFIX + normalizeActorKey(actorKey) + ":" + serviceId;
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
        return RedisKeyHash.sha256Hex(actorKey);
    }

    private boolean hasNoExpiry(String key) {
        Long ttl = redisTemplate.getExpire(key);
        return ttl == null || ttl < 0;
    }
}
