package com.example.welfare.recommend.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.RedisKeyHash;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationExecutionGuardTest {

    @Test
    @DisplayName("추천 lock 을 획득하면 task 를 실행하고 종료 시 lock 을 해제한다")
    void runForUserExecutesAndReleasesLock() {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String key = lockKey("user-key-1");
        when(valueOperations.setIfAbsent(eq(key), any(), eq(30L), eq(TimeUnit.MINUTES))).thenReturn(true);
        when(redisTemplate.execute(any(), eq(List.of(key)), any())).thenReturn(1L);
        RecommendationExecutionGuard guard = new RecommendationExecutionGuard(redisTemplate, 30L, 1_000L, 10L);
        AtomicBoolean executed = new AtomicBoolean(false);

        List<String> result = guard.runForUser("user-key-1", () -> {
            executed.set(true);
            return List.of("ok");
        }, List::of);

        assertThat(executed).isTrue();
        assertThat(result).containsExactly("ok");
        verify(valueOperations).setIfAbsent(eq(key), any(), eq(30L), eq(TimeUnit.MINUTES));
        verify(redisTemplate).execute(any(), eq(List.of(key)), any());
    }

    @Test
    @DisplayName("추천 lock 이 이미 있으면 fallback 결과를 반환한다")
    void runForUserReturnsFallbackWhenBusy() {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(lockKey("user-key-1")), any(), eq(30L), eq(TimeUnit.MINUTES))).thenReturn(false);
        RecommendationExecutionGuard guard = new RecommendationExecutionGuard(redisTemplate, 30L, 1_000L, 10L);

        List<String> result = guard.runForUser("user-key-1", List::of, () -> List.of("cached"));

        assertThat(result).containsExactly("cached");
    }

    @Test
    @DisplayName("추천 lock 이 있고 fallback 도 끝까지 비어 있으면 R003 을 던진다")
    void runForUserThrowsWhenBusyAndFallbackUnavailable() {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(lockKey("user-key-1")), any(), eq(30L), eq(TimeUnit.MINUTES))).thenReturn(false);
        RecommendationExecutionGuard guard = new RecommendationExecutionGuard(redisTemplate, 30L, 5L, 1L);

        assertThatThrownBy(() -> guard.runForUser("user-key-1", List::of, List::of))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.RECOMMENDATION_ALREADY_RUNNING));
    }

    @Test
    @DisplayName("사용자 식별자가 없으면 공유 추천 lock 키를 만들지 않는다")
    void runForUserRejectsBlankUserKey() {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        RecommendationExecutionGuard guard = new RecommendationExecutionGuard(redisTemplate, 30L, 5L, 1L);

        assertThatThrownBy(() -> guard.runForUser(" ", List::of, List::of))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("사용자 식별자가 없으면 추천 command lock 키를 만들지 않는다")
    void runCommandForUserRejectsBlankUserKey() {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        RecommendationExecutionGuard guard = new RecommendationExecutionGuard(redisTemplate, 30L, 5L, 1L);

        assertThatThrownBy(() -> guard.runCommandForUser(null, () -> {
        }))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("추천 command 는 lock 이 풀릴 때까지 기다렸다가 실행한다")
    void runCommandForUserWaitsUntilLockAvailable() {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String key = lockKey("user-key-1");
        when(valueOperations.setIfAbsent(eq(key), any(), eq(30L), eq(TimeUnit.MINUTES)))
                .thenReturn(false)
                .thenReturn(true);
        when(redisTemplate.execute(any(), eq(List.of(key)), any())).thenReturn(1L);
        RecommendationExecutionGuard guard = new RecommendationExecutionGuard(redisTemplate, 30L, 20L, 1L);
        AtomicBoolean executed = new AtomicBoolean(false);

        guard.runCommandForUser("user-key-1", () -> executed.set(true));

        assertThat(executed).isTrue();
        verify(redisTemplate).execute(any(), eq(List.of(key)), any());
    }

    private String lockKey(String userKey) {
        return "recommend:lock:user:v2:" + RedisKeyHash.sha256Hex(userKey);
    }
}
