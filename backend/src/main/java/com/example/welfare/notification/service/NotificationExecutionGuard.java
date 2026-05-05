package com.example.welfare.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class NotificationExecutionGuard {

    private static final String LOCK_KEY_PREFIX = "notification:lock:";

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> releaseIfOwnedScript;
    private final long lockLeaseMinutes;

    public NotificationExecutionGuard(
            RedisTemplate<String, String> redisTemplate,
            @Value("${notification.execution.lock-lease-minutes:180}") long lockLeaseMinutes
    ) {
        this.redisTemplate = redisTemplate;
        this.releaseIfOwnedScript = new DefaultRedisScript<>(
                """
                        if redis.call('get', KEYS[1]) == ARGV[1] then
                          return redis.call('del', KEYS[1])
                        end
                        return 0
                        """,
                Long.class
        );
        this.lockLeaseMinutes = lockLeaseMinutes;
    }

    public boolean runIfAvailable(String lockName, Runnable task) {
        String ownerToken = UUID.randomUUID().toString();
        String lockKey = lockKey(lockName);
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                lockKey,
                ownerToken,
                lockLeaseMinutes,
                TimeUnit.MINUTES
        );
        if (!Boolean.TRUE.equals(acquired)) {
            log.warn("[NotificationExecutionGuard] 이미 알림 작업이 실행 중입니다. lock={}", lockName);
            return false;
        }

        try {
            log.info("[NotificationExecutionGuard] 알림 실행 시작 lock={}", lockName);
            task.run();
            return true;
        } finally {
            Long released = redisTemplate.execute(releaseIfOwnedScript, List.of(lockKey), ownerToken);
            if (!Long.valueOf(1L).equals(released)) {
                log.warn("[NotificationExecutionGuard] 알림 lock 해제 확인 실패 lock={} ownerToken={}", lockName, ownerToken);
            }
            log.info("[NotificationExecutionGuard] 알림 실행 종료 lock={}", lockName);
        }
    }

    String lockKey(String lockName) {
        return LOCK_KEY_PREFIX + lockName;
    }
}
