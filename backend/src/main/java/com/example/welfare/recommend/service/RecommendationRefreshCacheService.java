package com.example.welfare.recommend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * 추천 payload 전체를 캐시하지 않고, userKey 기준 "최근 non-personal refresh 완료" 마커만 저장한다.
 * 실제 추천 row 는 계속 user_recommendations 테이블에서 읽는다.
 */
@Slf4j
@Service
public class RecommendationRefreshCacheService {

    private static final String REFRESH_CACHE_PREFIX = "recommend:refresh:user:";

    private final RedisTemplate<String, String> redisTemplate;
    private final long ttlMinutes;
    private final boolean educationCanonicalBonusEnabled;

    public RecommendationRefreshCacheService(
            RedisTemplate<String, String> redisTemplate,
            @Value("${recommend.refresh-cache-ttl-minutes:15}") long ttlMinutes,
            @Value("${recommend.priority.education-canonical-bonus.enabled:false}") boolean educationCanonicalBonusEnabled
    ) {
        this.redisTemplate = redisTemplate;
        this.ttlMinutes = ttlMinutes;
        this.educationCanonicalBonusEnabled = educationCanonicalBonusEnabled;
    }

    public boolean canReuse(String userKey) {
        if (!StringUtils.hasText(userKey)) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey(userKey)));
    }

    public void markReusable(String userKey) {
        if (!StringUtils.hasText(userKey)) {
            return;
        }
        redisTemplate.opsForValue().set(
                cacheKey(userKey),
                "1",
                ttlMinutes,
                TimeUnit.MINUTES
        );
    }

    public void evict(String userKey) {
        if (!StringUtils.hasText(userKey)) {
            return;
        }
        redisTemplate.delete(cacheKey(userKey));
    }

    String cacheKey(String userKey) {
        return REFRESH_CACHE_PREFIX + userKey + ":education-bonus:" + educationCanonicalBonusEnabled;
    }
}
