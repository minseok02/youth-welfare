package com.example.welfare.recommend.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.RedisKeyHash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationRefreshRateLimitServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RecommendationRefreshRateLimitService recommendationRefreshRateLimitService;

    @BeforeEach
    void setUp() {
        recommendationRefreshRateLimitService = new RecommendationRefreshRateLimitService(redisTemplate, 3, 600, 3, 60);
    }

    @Test
    @DisplayName("첫 personal refresh 요청이면 rate limit 키에 만료시간을 설정한다")
    void checkRefreshLimitSetsExpiryForFirstPersonalRequest() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String key = personalKey("user-key-1");
        when(valueOperations.increment(key)).thenReturn(1L);

        recommendationRefreshRateLimitService.checkRefreshLimit("user-key-1", true);

        verify(redisTemplate).expire(key, Duration.ofSeconds(600));
    }

    @Test
    @DisplayName("shared refresh 키에 TTL이 남아 있으면 만료시간을 다시 설정하지 않는다")
    void checkRefreshLimitSkipsExpireWhenSharedTtlExists() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String key = sharedKey("user-key-1");
        when(valueOperations.increment(key)).thenReturn(2L);
        when(redisTemplate.getExpire(key)).thenReturn(30L);

        recommendationRefreshRateLimitService.checkRefreshLimit("user-key-1", false);

        verify(redisTemplate, never()).expire(key, Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("personal refresh 제한을 초과하면 429 추천 refresh rate limit 오류를 반환한다")
    void checkRefreshLimitThrowsWhenPersonalLimitExceeded() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String key = personalKey("user-key-1");
        when(valueOperations.increment(key)).thenReturn(4L);
        when(redisTemplate.getExpire(key)).thenReturn(120L);

        assertThatThrownBy(() -> recommendationRefreshRateLimitService.checkRefreshLimit("user-key-1", true))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RECOMMENDATION_REFRESH_RATE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("사용자 식별자가 없으면 공유 refresh rate limit 키를 만들지 않는다")
    void checkRefreshLimitRejectsBlankUserKey() {
        assertThatThrownBy(() -> recommendationRefreshRateLimitService.checkRefreshLimit(" ", true))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(redisTemplate, never()).opsForValue();
    }

    private String personalKey(String userKey) {
        return "recommend:rate-limit:refresh:personal:" + RedisKeyHash.sha256Hex(userKey);
    }

    private String sharedKey(String userKey) {
        return "recommend:rate-limit:refresh:shared:" + RedisKeyHash.sha256Hex(userKey);
    }
}
