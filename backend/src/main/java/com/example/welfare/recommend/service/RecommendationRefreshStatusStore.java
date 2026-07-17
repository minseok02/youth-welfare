package com.example.welfare.recommend.service;

import com.example.welfare.global.util.RedisKeyHash;
import com.example.welfare.recommend.dto.RecommendationRefreshStatusResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class RecommendationRefreshStatusStore {

    private static final String STATUS_KEY_PREFIX = "recommend:refresh-status:user:v1:";
    private static final String ACTIVE_KEY_PREFIX = "recommend:refresh-active:user:v1:";
    private static final long DEFAULT_POLL_AFTER_MS = 1000L;

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final DefaultRedisScript<Long> releaseIfOwnedScript;
    private final Duration statusTtl;
    private final Duration activeTtl;

    public RecommendationRefreshStatusStore(
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            @Value("${recommend.async.status-ttl-minutes:30}") long statusTtlMinutes,
            @Value("${recommend.async.active-ttl-minutes:30}") long activeTtlMinutes
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.releaseIfOwnedScript = new DefaultRedisScript<>(
                """
                        if redis.call('get', KEYS[1]) == ARGV[1] then
                          return redis.call('del', KEYS[1])
                        end
                        return 0
                        """,
                Long.class
        );
        this.statusTtl = Duration.ofMinutes(Math.max(1L, statusTtlMinutes));
        this.activeTtl = Duration.ofMinutes(Math.max(1L, activeTtlMinutes));
    }

    public Optional<ActiveRefreshHandle> tryStart(String userKey, boolean personal, LocalDateTime requestedAt) {
        validateUserKey(userKey);
        String ownerToken = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                activeKey(userKey, personal),
                ownerToken,
                activeTtl
        );
        if (!Boolean.TRUE.equals(acquired)) {
            return Optional.empty();
        }
        ActiveRefreshHandle handle = new ActiveRefreshHandle(userKey, personal, ownerToken, requestedAt);
        try {
            write(userKey, personal, JobStatus.queued(personal, requestedAt));
            return Optional.of(handle);
        } catch (RuntimeException e) {
            release(handle);
            throw e;
        }
    }

    public RecommendationRefreshStatusResponse read(String userKey,
                                                    boolean personal,
                                                    LocalDateTime latestRecommendedAt,
                                                    int savedCount) {
        validateUserKey(userKey);
        JobStatus status = readStatus(userKey, personal).orElse(JobStatus.idle(personal));
        return toResponse(status, latestRecommendedAt, savedCount);
    }

    public void markRunning(ActiveRefreshHandle handle, LocalDateTime startedAt) {
        write(handle.userKey(), handle.personal(), JobStatus.running(handle.personal(), handle.requestedAt(), startedAt));
    }

    public void markSucceeded(ActiveRefreshHandle handle,
                              LocalDateTime startedAt,
                              LocalDateTime finishedAt,
                              LocalDateTime latestRecommendedAt,
                              int savedCount,
                              long generationDurationMs) {
        write(handle.userKey(), handle.personal(), JobStatus.succeeded(
                handle.personal(),
                handle.requestedAt(),
                startedAt,
                finishedAt,
                latestRecommendedAt,
                savedCount,
                generationDurationMs
        ));
    }

    public void markFailed(ActiveRefreshHandle handle,
                           LocalDateTime startedAt,
                           LocalDateTime finishedAt,
                           String errorCode,
                           boolean rateLimited,
                           long generationDurationMs) {
        write(handle.userKey(), handle.personal(), JobStatus.failed(
                handle.personal(),
                handle.requestedAt(),
                startedAt,
                finishedAt,
                sanitizeErrorCode(errorCode),
                rateLimited,
                generationDurationMs
        ));
    }

    public void release(ActiveRefreshHandle handle) {
        Long released = redisTemplate.execute(
                releaseIfOwnedScript,
                List.of(activeKey(handle.userKey(), handle.personal())),
                handle.ownerToken()
        );
        if (!Long.valueOf(1L).equals(released)) {
            log.warn("[RecommendationRefreshStatusStore] active key release skipped userKeyHash={} personal={}",
                    RedisKeyHash.sha256Hex(handle.userKey()), handle.personal());
        }
    }

    private Optional<JobStatus> readStatus(String userKey, boolean personal) {
        String raw = redisTemplate.opsForValue().get(statusKey(userKey, personal));
        if (!StringUtils.hasText(raw)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(raw, JobStatus.class));
        } catch (Exception e) {
            log.warn("[RecommendationRefreshStatusStore] invalid status payload removed userKeyHash={} personal={} errorType={}",
                    RedisKeyHash.sha256Hex(userKey), personal, e.getClass().getSimpleName());
            redisTemplate.delete(statusKey(userKey, personal));
            return Optional.empty();
        }
    }

    private void write(String userKey, boolean personal, JobStatus status) {
        try {
            redisTemplate.opsForValue().set(
                    statusKey(userKey, personal),
                    objectMapper.writeValueAsString(status),
                    statusTtl
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("추천 refresh 상태 직렬화 실패", e);
        }
    }

    private RecommendationRefreshStatusResponse toResponse(JobStatus status,
                                                           LocalDateTime latestRecommendedAt,
                                                           int savedCount) {
        RecommendationRefreshStatusResponse.RecommendationRefreshState state = status.state();
        boolean active = state == RecommendationRefreshStatusResponse.RecommendationRefreshState.QUEUED
                || state == RecommendationRefreshStatusResponse.RecommendationRefreshState.RUNNING;
        LocalDateTime effectiveLatestRecommendedAt = status.latestRecommendedAt() != null
                ? status.latestRecommendedAt()
                : latestRecommendedAt;
        int effectiveSavedCount = status.savedCount() > 0 ? status.savedCount() : savedCount;
        return new RecommendationRefreshStatusResponse(
                state,
                active,
                status.personal(),
                messageFor(state),
                status.requestedAt(),
                status.startedAt(),
                status.finishedAt(),
                effectiveLatestRecommendedAt,
                effectiveSavedCount,
                status.errorCode(),
                status.generationDurationMs(),
                active ? DEFAULT_POLL_AFTER_MS : null
        );
    }

    private String messageFor(RecommendationRefreshStatusResponse.RecommendationRefreshState state) {
        return switch (state) {
            case IDLE -> "추천 갱신 대기 중입니다.";
            case QUEUED -> "추천 갱신이 대기열에 등록되었습니다.";
            case RUNNING -> "추천 갱신이 진행 중입니다.";
            case SUCCEEDED -> "추천 갱신이 완료되었습니다.";
            case FAILED -> "추천 갱신이 실패했습니다.";
            case RATE_LIMITED -> "추천 갱신 요청이 너무 많습니다. 잠시 후 다시 시도하세요.";
        };
    }

    private String statusKey(String userKey, boolean personal) {
        return STATUS_KEY_PREFIX + RedisKeyHash.sha256Hex(userKey) + ":personal:" + personal;
    }

    private String activeKey(String userKey, boolean personal) {
        return ACTIVE_KEY_PREFIX + RedisKeyHash.sha256Hex(userKey) + ":personal:" + personal;
    }

    private String sanitizeErrorCode(String errorCode) {
        if (!StringUtils.hasText(errorCode)) {
            return null;
        }
        return errorCode.replaceAll("[^A-Za-z0-9_-]", "");
    }

    private void validateUserKey(String userKey) {
        if (!StringUtils.hasText(userKey)) {
            throw new IllegalArgumentException("userKey is required");
        }
    }

    public record ActiveRefreshHandle(
            String userKey,
            boolean personal,
            String ownerToken,
            LocalDateTime requestedAt
    ) {
    }

    private record JobStatus(
            boolean personal,
            RecommendationRefreshStatusResponse.RecommendationRefreshState state,
            LocalDateTime requestedAt,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            LocalDateTime latestRecommendedAt,
            int savedCount,
            String errorCode,
            Long generationDurationMs
    ) {
        static JobStatus idle(boolean personal) {
            return new JobStatus(personal, RecommendationRefreshStatusResponse.RecommendationRefreshState.IDLE,
                    null, null, null, null, 0, null, null);
        }

        static JobStatus queued(boolean personal, LocalDateTime requestedAt) {
            return new JobStatus(personal, RecommendationRefreshStatusResponse.RecommendationRefreshState.QUEUED,
                    requestedAt, null, null, null, 0, null, null);
        }

        static JobStatus running(boolean personal, LocalDateTime requestedAt, LocalDateTime startedAt) {
            return new JobStatus(personal, RecommendationRefreshStatusResponse.RecommendationRefreshState.RUNNING,
                    requestedAt, startedAt, null, null, 0, null, null);
        }

        static JobStatus succeeded(boolean personal,
                                   LocalDateTime requestedAt,
                                   LocalDateTime startedAt,
                                   LocalDateTime finishedAt,
                                   LocalDateTime latestRecommendedAt,
                                   int savedCount,
                                   long generationDurationMs) {
            return new JobStatus(personal, RecommendationRefreshStatusResponse.RecommendationRefreshState.SUCCEEDED,
                    requestedAt, startedAt, finishedAt, latestRecommendedAt, savedCount, null, generationDurationMs);
        }

        static JobStatus failed(boolean personal,
                                LocalDateTime requestedAt,
                                LocalDateTime startedAt,
                                LocalDateTime finishedAt,
                                String errorCode,
                                boolean rateLimited,
                                long generationDurationMs) {
            RecommendationRefreshStatusResponse.RecommendationRefreshState state = rateLimited
                    ? RecommendationRefreshStatusResponse.RecommendationRefreshState.RATE_LIMITED
                    : RecommendationRefreshStatusResponse.RecommendationRefreshState.FAILED;
            return new JobStatus(personal, state, requestedAt, startedAt, finishedAt,
                    null, 0, errorCode, generationDurationMs);
        }
    }
}
