package com.example.welfare.collect.service;

import com.example.welfare.collect.repository.CollectExecutionLockRepository;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DB 기준 전역 수집 중복 실행 방지 가드.
 */
@Slf4j
@Component
public class CollectExecutionGuard {

    static final String GLOBAL_LOCK_NAME = "collect-global";

    private final CollectExecutionLockRepository collectExecutionLockRepository;
    private final long lockLeaseMinutes;

    public CollectExecutionGuard(CollectExecutionLockRepository collectExecutionLockRepository,
                                 @Value("${collect.execution.lock-lease-minutes:360}") long lockLeaseMinutes) {
        this.collectExecutionLockRepository = collectExecutionLockRepository;
        this.lockLeaseMinutes = lockLeaseMinutes;
    }

    public void runExclusive(String jobName, Runnable task) {
        String ownerToken = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lockedUntil = now.plusMinutes(lockLeaseMinutes);
        if (!collectExecutionLockRepository.tryAcquire(GLOBAL_LOCK_NAME, ownerToken, now, lockedUntil)) {
            log.warn("[CollectExecutionGuard] 이미 수집 작업이 실행 중입니다. request={}", jobName);
            throw new CustomException(ErrorCode.COLLECT_ALREADY_RUNNING);
        }

        try {
            log.info("[CollectExecutionGuard] 수집 실행 시작 job={}", jobName);
            task.run();
        } finally {
            if (!collectExecutionLockRepository.release(GLOBAL_LOCK_NAME, ownerToken)) {
                log.warn("[CollectExecutionGuard] 수집 lock 해제 확인 실패 job={} ownerToken={}", jobName, ownerToken);
            }
            log.info("[CollectExecutionGuard] 수집 실행 종료 job={}", jobName);
        }
    }
}
