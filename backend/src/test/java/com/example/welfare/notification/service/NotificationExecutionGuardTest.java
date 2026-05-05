package com.example.welfare.notification.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationExecutionGuardTest {

    @Test
    @DisplayName("notification guard 는 lock 획득 시 task 를 실행하고 lock 을 해제한다")
    void runIfAvailableExecutesWhenLockAcquired() {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("notification:lock:notification-daily"), any(), eq(180L), eq(TimeUnit.MINUTES))).thenReturn(true);
        when(redisTemplate.execute(any(), eq(List.of("notification:lock:notification-daily")), any())).thenReturn(1L);
        NotificationExecutionGuard guard = new NotificationExecutionGuard(redisTemplate, 180L);
        AtomicBoolean executed = new AtomicBoolean(false);

        boolean acquired = guard.runIfAvailable("notification-daily", () -> executed.set(true));

        assertThat(acquired).isTrue();
        assertThat(executed).isTrue();
        verify(valueOperations).setIfAbsent(eq("notification:lock:notification-daily"), any(), eq(180L), eq(TimeUnit.MINUTES));
        verify(redisTemplate).execute(any(), eq(List.of("notification:lock:notification-daily")), any());
    }

    @Test
    @DisplayName("notification guard 는 lock 이 이미 있으면 false 를 반환한다")
    void runIfAvailableReturnsFalseWhenBusy() {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("notification:lock:notification-daily"), any(), eq(180L), eq(TimeUnit.MINUTES))).thenReturn(false);
        NotificationExecutionGuard guard = new NotificationExecutionGuard(redisTemplate, 180L);

        boolean acquired = guard.runIfAvailable("notification-daily", () -> {});

        assertThat(acquired).isFalse();
    }
}
