package com.example.welfare.collect.service;

import com.example.welfare.collect.repository.CollectExecutionLockRepository;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
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
    private final long heartbeatSeconds;

    public CollectExecutionGuard(CollectExecutionLockRepository collectExecutionLockRepository,
                                 @Value("${collect.execution.lock-lease-minutes:15}") long lockLeaseMinutes,
                                 @Value("${collect.execution.lock-heartbeat-seconds:60}") long heartbeatSeconds) {
        this.collectExecutionLockRepository = collectExecutionLockRepository;
        this.lockLeaseMinutes = lockLeaseMinutes;
        this.heartbeatSeconds = heartbeatSeconds;
    }

    public void runExclusive(String jobName, Runnable task) {
        String ownerToken = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lockedUntil = now.plusMinutes(lockLeaseMinutes);
        if (!collectExecutionLockRepository.tryAcquire(GLOBAL_LOCK_NAME, ownerToken, now, lockedUntil)) {
            log.warn("[CollectExecutionGuard] 이미 수집 작업이 실행 중입니다. request={}", jobName);
            throw new CustomException(ErrorCode.COLLECT_ALREADY_RUNNING);
        }

        ScheduledExecutorService heartbeat = startHeartbeat(jobName, ownerToken);
        try {
            log.info("[CollectExecutionGuard] 수집 실행 시작 job={}", jobName);
            task.run();
        } finally {
            heartbeat.shutdownNow();
            if (!collectExecutionLockRepository.release(GLOBAL_LOCK_NAME, ownerToken)) {
                log.warn("[CollectExecutionGuard] 수집 lock 해제 확인 실패 job={} ownerToken={}", jobName, ownerToken);
            }
            log.info("[CollectExecutionGuard] 수집 실행 종료 job={}", jobName);
        }
    }

    private ScheduledExecutorService startHeartbeat(String jobName, String ownerToken) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(heartbeatThreadFactory(jobName));
        long intervalSeconds = Math.max(heartbeatSeconds, 1L);
        executor.scheduleAtFixedRate(() -> refreshLease(jobName, ownerToken), intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        return executor;
    }

    private void refreshLease(String jobName, String ownerToken) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lockedUntil = now.plusMinutes(lockLeaseMinutes);
        boolean refreshed = collectExecutionLockRepository.refresh(GLOBAL_LOCK_NAME, ownerToken, now, lockedUntil);
        if (!refreshed) {
            log.error("[CollectExecutionGuard] 수집 lock heartbeat 갱신 실패 job={} ownerToken={}", jobName, ownerToken);
        }
    }

    private ThreadFactory heartbeatThreadFactory(String jobName) {
        return runnable -> {
            Thread thread = new Thread(runnable, "collect-lock-heartbeat-" + jobName);
            thread.setDaemon(true);
            return thread;
        };
    }
}
