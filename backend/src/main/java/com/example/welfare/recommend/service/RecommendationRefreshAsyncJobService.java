package com.example.welfare.recommend.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.RedisKeyHash;
import com.example.welfare.recommend.dto.RecommendationRefreshStatusResponse;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.user.service.UserKeyLookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationRefreshAsyncJobService {

    private final UserKeyLookupService userKeyLookupService;
    private final RecommendationGenerationService recommendationGenerationService;
    private final RecommendationResultReadService recommendationResultReadService;
    private final RecommendationRefreshStatusStore recommendationRefreshStatusStore;
    @Qualifier("recommendationAsyncExecutor")
    private final Executor recommendationAsyncExecutor;

    public RecommendationRefreshStatusResponse trigger(Long userId, boolean personal) {
        String userKey = userKeyLookupService.findRequired(userId);
        RecommendationRefreshStatusResponse current = getStatusForUserKey(userKey, personal);
        if (current.active()) {
            return current;
        }

        LocalDateTime requestedAt = LocalDateTime.now();
        return recommendationRefreshStatusStore.tryStart(userKey, personal, requestedAt)
                .map(handle -> submit(userId, handle))
                .orElseGet(() -> getStatusForUserKey(userKey, personal));
    }

    public RecommendationRefreshStatusResponse getStatus(Long userId, boolean personal) {
        String userKey = userKeyLookupService.findRequired(userId);
        return getStatusForUserKey(userKey, personal);
    }

    private RecommendationRefreshStatusResponse submit(Long userId,
                                                       RecommendationRefreshStatusStore.ActiveRefreshHandle handle) {
        try {
            recommendationAsyncExecutor.execute(() -> runRefresh(userId, handle));
        } catch (RuntimeException e) {
            LocalDateTime now = LocalDateTime.now();
            recommendationRefreshStatusStore.markFailed(handle, now, now, e.getClass().getSimpleName(), false, 0L);
            recommendationRefreshStatusStore.release(handle);
            throw e;
        }
        return getStatusForUserKey(handle.userKey(), handle.personal());
    }

    private void runRefresh(Long userId, RecommendationRefreshStatusStore.ActiveRefreshHandle handle) {
        LocalDateTime startedAt = LocalDateTime.now();
        long startedNanos = System.nanoTime();
        recommendationRefreshStatusStore.markRunning(handle, startedAt);
        try {
            List<UserRecommendation> saved = recommendationGenerationService.recommend(userId, handle.personal());
            LocalDateTime finishedAt = LocalDateTime.now();
            LocalDateTime latestRecommendedAt = saved.isEmpty() ? null : saved.get(0).getRecommendedAt();
            recommendationRefreshStatusStore.markSucceeded(
                    handle,
                    startedAt,
                    finishedAt,
                    latestRecommendedAt,
                    saved.size(),
                    elapsedMs(startedNanos)
            );
        } catch (CustomException e) {
            boolean rateLimited = e.getErrorCode() == ErrorCode.RECOMMENDATION_REFRESH_RATE_LIMIT_EXCEEDED;
            recommendationRefreshStatusStore.markFailed(
                    handle,
                    startedAt,
                    LocalDateTime.now(),
                    e.getErrorCode().getCode(),
                    rateLimited,
                    elapsedMs(startedNanos)
            );
            log.warn("[RecommendationRefreshAsyncJobService] async refresh failed userKeyHash={} personal={} errorCode={}",
                    RedisKeyHash.sha256Hex(handle.userKey()), handle.personal(), e.getErrorCode().getCode());
        } catch (Exception e) {
            recommendationRefreshStatusStore.markFailed(
                    handle,
                    startedAt,
                    LocalDateTime.now(),
                    e.getClass().getSimpleName(),
                    false,
                    elapsedMs(startedNanos)
            );
            log.warn("[RecommendationRefreshAsyncJobService] async refresh failed userKeyHash={} personal={} errorType={}",
                    RedisKeyHash.sha256Hex(handle.userKey()), handle.personal(), e.getClass().getSimpleName());
        } finally {
            recommendationRefreshStatusStore.release(handle);
        }
    }

    private RecommendationRefreshStatusResponse getStatusForUserKey(String userKey, boolean personal) {
        SavedRecommendationMetadata metadata = latestSavedMetadata(userKey);
        return recommendationRefreshStatusStore.read(
                userKey,
                personal,
                metadata.latestRecommendedAt(),
                metadata.savedCount()
        );
    }

    private SavedRecommendationMetadata latestSavedMetadata(String userKey) {
        List<UserRecommendation> saved = recommendationResultReadService.findLatestSavedRecommendations(userKey);
        if (saved.isEmpty()) {
            return new SavedRecommendationMetadata(null, 0);
        }
        return new SavedRecommendationMetadata(saved.get(0).getRecommendedAt(), saved.size());
    }

    private long elapsedMs(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private record SavedRecommendationMetadata(LocalDateTime latestRecommendedAt, int savedCount) {
    }
}
