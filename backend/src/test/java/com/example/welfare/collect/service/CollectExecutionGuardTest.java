package com.example.welfare.collect.service;

import com.example.welfare.collect.repository.CollectExecutionLockRepository;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectExecutionGuardTest {

    @Test
    @DisplayName("DB lock 을 획득하면 task 를 실행하고 종료 시 lock 을 해제한다")
    void runExclusiveAcquiresAndReleasesLock() {
        CollectExecutionLockRepository repository = mock(CollectExecutionLockRepository.class);
        when(repository.tryAcquire(eq(CollectExecutionGuard.GLOBAL_LOCK_NAME), any(), any(), any())).thenReturn(true);
        when(repository.release(eq(CollectExecutionGuard.GLOBAL_LOCK_NAME), any())).thenReturn(true);
        CollectExecutionGuard guard = new CollectExecutionGuard(repository, 360L);
        AtomicBoolean executed = new AtomicBoolean(false);

        guard.runExclusive("collect-all", () -> executed.set(true));

        assertThat(executed).isTrue();
        verify(repository).tryAcquire(eq(CollectExecutionGuard.GLOBAL_LOCK_NAME), any(), any(), any());
        verify(repository).release(eq(CollectExecutionGuard.GLOBAL_LOCK_NAME), any());
    }

    @Test
    @DisplayName("DB lock 을 획득하지 못하면 COLLECT_ALREADY_RUNNING 을 던진다")
    void runExclusiveThrowsWhenLockBusy() {
        CollectExecutionLockRepository repository = mock(CollectExecutionLockRepository.class);
        when(repository.tryAcquire(eq(CollectExecutionGuard.GLOBAL_LOCK_NAME), any(), any(), any())).thenReturn(false);
        CollectExecutionGuard guard = new CollectExecutionGuard(repository, 360L);

        assertThatThrownBy(() -> guard.runExclusive("collect-all", () -> {}))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> assertThat(((CustomException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.COLLECT_ALREADY_RUNNING));
    }
}
