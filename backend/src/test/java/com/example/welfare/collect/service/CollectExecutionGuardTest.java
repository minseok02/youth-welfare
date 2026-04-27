package com.example.welfare.collect.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectExecutionGuardTest {

    @Test
    @DisplayName("다른 수집 작업이 실행 중이면 새 실행을 거절한다")
    void runExclusiveRejectsConcurrentExecution() throws Exception {
        CollectExecutionGuard guard = new CollectExecutionGuard();
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread thread = new Thread(() -> guard.runExclusive("collect-youth", () -> {
            running.countDown();
            try {
                assertThat(release.await(3, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }));
        thread.start();

        assertThat(running.await(1, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> guard.runExclusive("collect-bokjiro-central", () -> {
        }))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COLLECT_ALREADY_RUNNING);

        release.countDown();
        thread.join(3000);
        assertThat(thread.isAlive()).isFalse();
    }

    @Test
    @DisplayName("수집 작업이 예외로 끝나도 다음 실행을 다시 받을 수 있다")
    void runExclusiveReleasesLockAfterFailure() {
        CollectExecutionGuard guard = new CollectExecutionGuard();

        assertThatThrownBy(() -> guard.runExclusive("collect-youth", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThatCode(() -> guard.runExclusive("collect-bokjiro-local", () -> {
        })).doesNotThrowAnyException();
    }
}
