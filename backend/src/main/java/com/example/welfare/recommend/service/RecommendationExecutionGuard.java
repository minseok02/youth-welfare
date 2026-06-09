package com.example.welfare.recommend.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.RedisKeyHash;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Component
public class RecommendationExecutionGuard {

    private static final String LOCK_KEY_PREFIX = "recommend:lock:user:v2:";

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> releaseIfOwnedScript;
    private final long lockLeaseMinutes;
    private final long fallbackWaitMillis;
    private final long fallbackPollMillis;

    public RecommendationExecutionGuard(
            RedisTemplate<String, String> redisTemplate,
            @Value("${recommend.execution.lock-lease-minutes:30}") long lockLeaseMinutes,
            @Value("${recommend.execution.fallback-wait-millis:5000}") long fallbackWaitMillis,
            @Value("${recommend.execution.fallback-poll-millis:200}") long fallbackPollMillis
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
        this.fallbackWaitMillis = fallbackWaitMillis;
        this.fallbackPollMillis = fallbackPollMillis;
    }

    public <T> T runForUser(String userKey, Supplier<T> task, Supplier<T> fallback) {
        validateUserKey(userKey);
        LockHandle lockHandle = tryAcquire(userKey);
        if (!lockHandle.acquired()) {
            log.warn("[RecommendationExecutionGuard] 추천 생성이 이미 진행 중입니다. userKeyHash={}", RedisKeyHash.sha256Hex(userKey));
            return awaitFallback(userKey, fallback);
        }

        try {
            log.info("[RecommendationExecutionGuard] 추천 생성 시작 userKeyHash={}", RedisKeyHash.sha256Hex(userKey));
            return task.get();
        } finally {
            release(userKey, lockHandle.ownerToken());
            log.info("[RecommendationExecutionGuard] 추천 생성 종료 userKeyHash={}", RedisKeyHash.sha256Hex(userKey));
        }
    }

    public void runCommandForUser(String userKey, Runnable task) {
        validateUserKey(userKey);
        LockHandle lockHandle = awaitAcquire(userKey);
        try {
            log.info("[RecommendationExecutionGuard] 추천 command 실행 시작 userKeyHash={}", RedisKeyHash.sha256Hex(userKey));
            task.run();
        } finally {
            release(userKey, lockHandle.ownerToken());
            log.info("[RecommendationExecutionGuard] 추천 command 실행 종료 userKeyHash={}", RedisKeyHash.sha256Hex(userKey));
        }
    }

    private LockHandle awaitAcquire(String userKey) {
        long deadline = System.currentTimeMillis() + Math.max(fallbackWaitMillis, 0L);
        while (true) {
            LockHandle lockHandle = tryAcquire(userKey);
            if (lockHandle.acquired()) {
                return lockHandle;
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new CustomException(ErrorCode.RECOMMENDATION_ALREADY_RUNNING);
            }
            sleepQuietly(fallbackPollMillis);
        }
    }

    private LockHandle tryAcquire(String userKey) {
        String ownerToken = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                lockKey(userKey),
                ownerToken,
                lockLeaseMinutes,
                TimeUnit.MINUTES
        );
        return new LockHandle(ownerToken, Boolean.TRUE.equals(acquired));
    }

    private void release(String userKey, String ownerToken) {
        Long released = redisTemplate.execute(releaseIfOwnedScript, List.of(lockKey(userKey)), ownerToken);
        if (!Long.valueOf(1L).equals(released)) {
            log.warn("[RecommendationExecutionGuard] 추천 lock 해제 확인 실패 userKeyHash={}", RedisKeyHash.sha256Hex(userKey));
        }
    }

    private <T> T awaitFallback(String userKey, Supplier<T> fallback) {
        long deadline = System.currentTimeMillis() + Math.max(fallbackWaitMillis, 0L);
        while (true) {
            T result = fallback.get();
            if (isUsable(result)) {
                return result;
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new CustomException(ErrorCode.RECOMMENDATION_ALREADY_RUNNING);
            }
            sleepQuietly(fallbackPollMillis);
        }
    }

    private boolean isUsable(Object result) {
        if (result == null) {
            return false;
        }
        if (result instanceof java.util.Collection<?> collection) {
            return !collection.isEmpty();
        }
        return true;
    }

    private void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("추천 실행 대기 중 인터럽트 발생", e);
        }
    }

    String lockKey(String userKey) {
        return LOCK_KEY_PREFIX + RedisKeyHash.sha256Hex(userKey);
    }

    private void validateUserKey(String userKey) {
        if (!StringUtils.hasText(userKey)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private record LockHandle(String ownerToken, boolean acquired) {
    }
}
