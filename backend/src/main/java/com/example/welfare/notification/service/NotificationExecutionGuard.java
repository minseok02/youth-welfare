package com.example.welfare.notification.service;

import com.example.welfare.collect.repository.CollectExecutionLockRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
public class NotificationExecutionGuard {

    private final CollectExecutionLockRepository collectExecutionLockRepository;
    private final long lockLeaseMinutes;

    public NotificationExecutionGuard(
            CollectExecutionLockRepository collectExecutionLockRepository,
            @Value("${notification.execution.lock-lease-minutes:180}") long lockLeaseMinutes
    ) {
        this.collectExecutionLockRepository = collectExecutionLockRepository;
        this.lockLeaseMinutes = lockLeaseMinutes;
    }

    public boolean runIfAvailable(String lockName, Runnable task) {
        String ownerToken = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lockedUntil = now.plusMinutes(lockLeaseMinutes);
        if (!collectExecutionLockRepository.tryAcquire(lockName, ownerToken, now, lockedUntil)) {
            log.warn("[NotificationExecutionGuard] 이미 알림 작업이 실행 중입니다. lock={}", lockName);
            return false;
        }

        try {
            log.info("[NotificationExecutionGuard] 알림 실행 시작 lock={}", lockName);
            task.run();
            return true;
        } finally {
            if (!collectExecutionLockRepository.release(lockName, ownerToken)) {
                log.warn("[NotificationExecutionGuard] 알림 lock 해제 확인 실패 lock={} ownerToken={}", lockName, ownerToken);
            }
            log.info("[NotificationExecutionGuard] 알림 실행 종료 lock={}", lockName);
        }
    }
}
