package com.example.welfare.recommend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
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

    public Optional<LocalDateTime> findReusableRecommendedAt(String userKey) {
        if (!StringUtils.hasText(userKey)) {
            return Optional.empty();
        }
        String value = redisTemplate.opsForValue().get(cacheKey(userKey));
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.parse(value));
        } catch (DateTimeParseException e) {
            log.warn("[RecommendationRefreshCacheService] 잘못된 refresh cache token 형식으로 재사용 마커를 제거합니다. userKey={} value={}",
                    userKey, value);
            evict(userKey);
            return Optional.empty();
        }
    }

    public void markReusable(String userKey, LocalDateTime recommendedAt) {
        if (!StringUtils.hasText(userKey)) {
            return;
        }
        if (recommendedAt == null) {
            evict(userKey);
            return;
        }
        redisTemplate.opsForValue().set(
                cacheKey(userKey),
                recommendedAt.toString(),
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
