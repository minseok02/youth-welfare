package com.example.welfare.admin.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.RedisKeyHash;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdminOperationRateLimitServiceTest {

    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

    @Test
    @DisplayName("관리자 mutation 첫 요청이면 Redis 카운터와 TTL을 설정한다")
    void checkMutationLimitSetsTtlOnFirstRequest() {
        AdminOperationRateLimitService service = new AdminOperationRateLimitService(redisTemplate, 2, 60, 10, 60);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        String key = "admin:rate-limit:mutation:" + RedisKeyHash.sha256Hex("user-key-1") + ":users:pii-rotation";
        given(valueOperations.increment(key)).willReturn(1L);

        service.checkMutationLimit("user-key-1", "users:pii-rotation");

        verify(redisTemplate).expire(
                eq(key),
                eq(Duration.ofSeconds(60))
        );
    }

    @Test
    @DisplayName("관리자 mutation 제한을 초과하면 C005를 던진다")
    void checkMutationLimitThrowsWhenLimitExceeded() {
        AdminOperationRateLimitService service = new AdminOperationRateLimitService(redisTemplate, 2, 60, 10, 60);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.increment("admin:rate-limit:mutation:" + RedisKeyHash.sha256Hex("user-key-1") + ":collect:all")).willReturn(3L);
        given(redisTemplate.getExpire(any())).willReturn(30L);

        assertThatThrownBy(() -> service.checkMutationLimit("user-key-1", "collect:all"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ADMIN_OPERATION_RATE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("관리자 expensive read 제한도 별도 prefix로 적용한다")
    void checkExpensiveReadLimitUsesReadPrefix() {
        AdminOperationRateLimitService service = new AdminOperationRateLimitService(redisTemplate, 2, 60, 1, 30);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.increment("admin:rate-limit:expensive-read:" + RedisKeyHash.sha256Hex("user-key-1") + ":policies:compare")).willReturn(2L);
        given(redisTemplate.getExpire(any())).willReturn(10L);

        assertThatThrownBy(() -> service.checkExpensiveReadLimit("user-key-1", "policies:compare"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ADMIN_OPERATION_RATE_LIMIT_EXCEEDED);
    }
}
