package com.example.welfare.admin.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.RedisKeyHash;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
@Slf4j
public class AdminOperationRateLimitService {

    private static final String MUTATION_PREFIX = "admin:rate-limit:mutation:";
    private static final String EXPENSIVE_READ_PREFIX = "admin:rate-limit:expensive-read:";

    private final RedisTemplate<String, String> redisTemplate;
    private final int mutationMaxRequests;
    private final long mutationWindowSeconds;
    private final int expensiveReadMaxRequests;
    private final long expensiveReadWindowSeconds;

    public AdminOperationRateLimitService(
            RedisTemplate<String, String> redisTemplate,
            @Value("${admin.operation-rate-limit.mutation.max-requests:20}") int mutationMaxRequests,
            @Value("${admin.operation-rate-limit.mutation.window-seconds:60}") long mutationWindowSeconds,
            @Value("${admin.operation-rate-limit.expensive-read.max-requests:60}") int expensiveReadMaxRequests,
            @Value("${admin.operation-rate-limit.expensive-read.window-seconds:60}") long expensiveReadWindowSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.mutationMaxRequests = mutationMaxRequests;
        this.mutationWindowSeconds = mutationWindowSeconds;
        this.expensiveReadMaxRequests = expensiveReadMaxRequests;
        this.expensiveReadWindowSeconds = expensiveReadWindowSeconds;
    }

    public void checkMutationLimit(String actorKey, String operation) {
        checkLimit(MUTATION_PREFIX, actorKey, operation, mutationMaxRequests, mutationWindowSeconds);
    }

    public void checkExpensiveReadLimit(String actorKey, String operation) {
        checkLimit(EXPENSIVE_READ_PREFIX, actorKey, operation, expensiveReadMaxRequests, expensiveReadWindowSeconds);
    }

    private void checkLimit(String prefix, String actorKey, String operation, int maxRequests, long windowSeconds) {
        String key = prefix + RedisKeyHash.sha256Hex(actorKey) + ":" + normalize(operation);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        if (count == 1L || hasNoExpiry(key)) {
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }
        if (count > maxRequests) {
            log.warn("[AdminAudit] event=rate_limit outcome=exceeded operation={} actorHash={} count={} maxRequests={} windowSeconds={}",
                    normalize(operation), RedisKeyHash.sha256Hex(actorKey), count, maxRequests, windowSeconds);
            throw new CustomException(ErrorCode.ADMIN_OPERATION_RATE_LIMIT_EXCEEDED);
        }
    }

    private boolean hasNoExpiry(String key) {
        Long ttl = redisTemplate.getExpire(key);
        return ttl == null || ttl < 0;
    }

    private String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "unknown";
        }
        return raw.trim().replaceAll("[^A-Za-z0-9._:-]", "_");
    }
}
