package com.example.welfare.notification.service;

import com.example.welfare.collect.repository.CollectExecutionLockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

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
        CollectExecutionLockRepository repository = mock(CollectExecutionLockRepository.class);
        when(repository.tryAcquire(eq("notification-daily"), any(), any(), any())).thenReturn(true);
        when(repository.release(eq("notification-daily"), any())).thenReturn(true);
        NotificationExecutionGuard guard = new NotificationExecutionGuard(repository, 180L);
        AtomicBoolean executed = new AtomicBoolean(false);

        boolean acquired = guard.runIfAvailable("notification-daily", () -> executed.set(true));

        assertThat(acquired).isTrue();
        assertThat(executed).isTrue();
        verify(repository).tryAcquire(eq("notification-daily"), any(), any(), any());
        verify(repository).release(eq("notification-daily"), any());
    }

    @Test
    @DisplayName("notification guard 는 lock 이 이미 있으면 false 를 반환한다")
    void runIfAvailableReturnsFalseWhenBusy() {
        CollectExecutionLockRepository repository = mock(CollectExecutionLockRepository.class);
        when(repository.tryAcquire(eq("notification-daily"), any(), any(), any())).thenReturn(false);
        NotificationExecutionGuard guard = new NotificationExecutionGuard(repository, 180L);

        boolean acquired = guard.runIfAvailable("notification-daily", () -> {});

        assertThat(acquired).isFalse();
    }
}
