package com.example.welfare.recommend.service;

import com.example.welfare.global.util.RedisKeyHash;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.recommend.dto.RetrievedRecommendationCandidates;
import com.example.welfare.recommend.dto.ScoredCandidate;
import com.example.welfare.recommend.entity.AiScoreStatus;
import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.service.UserRecommendationReadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationGenerationService {

    private final ClusterService clusterService;
    private final RetrievalService retrievalService;
    private final RuleScoringService ruleScoringService;
    private final AiScoringService aiScoringService;
    private final ReRankingService reRankingService;
    private final RecommendationPostScoringFilterService recommendationPostScoringFilterService;
    private final RecommendationPersistenceService recommendationPersistenceService;
    private final RecommendationLogService recommendationLogService;
    private final RecommendationRefreshCacheService recommendationRefreshCacheService;
    private final RecommendationRefreshRateLimitService recommendationRefreshRateLimitService;
    private final RecommendationResultReadService recommendationResultReadService;
    private final UserRecommendationReadService userRecommendationReadService;
    private final RecommendationExecutionGuard recommendationExecutionGuard;
    private final RecommendationRunLogService recommendationRunLogService;

    public List<UserRecommendation> recommend(Long userId, boolean personal) {
        UserRecommendationReadService.RecommendationReadContext context =
                userRecommendationReadService.getRecommendationContext(userId);
        RecommendationUserSnapshot snapshot = context.snapshot();
        User user = context.user();
        String userKey = snapshot.userKey();
        recommendationRefreshRateLimitService.checkRefreshLimit(userKey, personal);

        return recommendationExecutionGuard.runForUser(
                userKey,
                () -> doRecommend(user, snapshot, personal),
                () -> recommendationResultReadService.findLatestSavedRecommendations(userKey)
        );
    }

    private List<UserRecommendation> doRecommend(User user, RecommendationUserSnapshot snapshot, boolean personal) {
        long startedNanos = System.nanoTime();
        String userKey = snapshot.userKey();
        if (personal) {
            recommendationRefreshCacheService.evict(userKey);
        } else {
            Optional<java.time.LocalDateTime> reusableRecommendedAt =
                    recommendationRefreshCacheService.findReusableRecommendedAt(userKey);
            if (reusableRecommendedAt.isPresent()) {
                List<UserRecommendation> cached = recommendationResultReadService
                        .findSavedRecommendationsForBatch(userKey, reusableRecommendedAt.get());
                if (!cached.isEmpty()) {
                    log.info("[RecommendationGenerationService] refresh cache hit userKeyHash={} recommendedAt={}",
                            RedisKeyHash.sha256Hex(userKey), reusableRecommendedAt.get());
                    logRunSummary(userKey, personal, "cache_hit", null, cached.size(), 0, 0, 0,
                            Map.of(), cached.size(), elapsedMs(startedNanos));
                    return cached;
                }
                recommendationRefreshCacheService.evict(userKey);
            }
        }

        String clusterId = personal ? "youth_all" : clusterService.assignCluster(snapshot);

        RetrievedRecommendationCandidates retrieved = retrievalService.retrieve(clusterId, snapshot);
        if (retrieved.isEmpty()) {
            log.info("[RecommendationGenerationService] 후보 없음 userKeyHash={}", RedisKeyHash.sha256Hex(userKey));
            logRunSummary(userKey, personal, "no_candidates", clusterId, 0, 0, 0, 0,
                    Map.of(), 0, elapsedMs(startedNanos));
            return List.of();
        }

        List<ScoredCandidate> scored = ruleScoringService.score(retrieved, snapshot);
        int ruleScoredCount = scored.size();
        scored = recommendationPostScoringFilterService.filterSpecialTargetMismatches(scored);
        if (scored.isEmpty()) {
            log.info("[RecommendationGenerationService] 필터 후 후보 없음 userKeyHash={}", RedisKeyHash.sha256Hex(userKey));
            logRunSummary(userKey, personal, "post_filter_empty", clusterId, retrieved.candidates().size(),
                    ruleScoredCount, 0, 0, Map.of(), 0, elapsedMs(startedNanos));
            return List.of();
        }

        scored = aiScoringService.score(clusterId, scored, snapshot);
        Map<AiScoreStatus, Long> aiStatusCounts = countAiStatuses(scored);

        List<ScoredCandidate> reranked = reRankingService.rerank(scored, snapshot);
        ScoreWeight weight = reRankingService.getCurrentWeight();
        List<UserRecommendation> saved = recommendationPersistenceService.save(user, reranked, weight);

        if (saved.isEmpty()) {
            log.warn("[RecommendationGenerationService] 저장 결과가 비어 후처리를 생략합니다. userKeyHash={}", RedisKeyHash.sha256Hex(userKey));
            recommendationRefreshCacheService.evict(userKey);
            logRunSummary(userKey, personal, "save_empty", clusterId, retrieved.candidates().size(),
                    ruleScoredCount, scored.size(), reranked.size(), aiStatusCounts, 0, elapsedMs(startedNanos));
            return saved;
        }

        saved = recommendationResultReadService.findSavedRecommendationsForBatch(userKey, saved.get(0).getRecommendedAt());

        try {
            recommendationLogService.refreshLogs(user, saved, weight);
        } catch (Exception e) {
            log.warn("[RecommendationGenerationService] 로그 후처리 실패 userKeyHash={} errorType={}",
                    RedisKeyHash.sha256Hex(userKey), e.getClass().getSimpleName());
        }
        if (!personal) {
            recommendationRefreshCacheService.markReusable(userKey, saved.get(0).getRecommendedAt());
        }
        logRunSummary(userKey, personal, "saved", clusterId, retrieved.candidates().size(),
                ruleScoredCount, scored.size(), reranked.size(), aiStatusCounts, saved.size(), elapsedMs(startedNanos));
        return saved;
    }

    private Map<AiScoreStatus, Long> countAiStatuses(List<ScoredCandidate> scored) {
        EnumMap<AiScoreStatus, Long> counts = new EnumMap<>(AiScoreStatus.class);
        for (ScoredCandidate candidate : scored) {
            AiScoreStatus status = candidate.getAiStatus() != null
                    ? candidate.getAiStatus()
                    : AiScoreStatus.NOT_REQUESTED;
            counts.merge(status, 1L, Long::sum);
        }
        return counts;
    }

    private void logRunSummary(String userKey,
                               boolean personal,
                               String outcome,
                               String clusterId,
                               int retrievedCount,
                               int ruleScoredCount,
                               int postFilterCount,
                               int rerankedCount,
                               Map<AiScoreStatus, Long> aiStatusCounts,
                               int savedCount,
                               long durationMs) {
        log.info("[RecommendationRun] outcome={} userKeyHash={} personal={} clusterId={} retrieved={} ruleScored={} postFilter={} reranked={} saved={} aiStatusCounts={} durationMs={}",
                outcome,
                RedisKeyHash.sha256Hex(userKey),
                personal,
                clusterId,
                retrievedCount,
                ruleScoredCount,
                postFilterCount,
                rerankedCount,
                savedCount,
                aiStatusCounts,
                durationMs);
        recommendationRunLogService.record(new RecommendationRunLogCommand(
                userKey,
                personal,
                outcome,
                clusterId,
                retrievedCount,
                ruleScoredCount,
                postFilterCount,
                rerankedCount,
                savedCount,
                toAiStatusCountsJson(aiStatusCounts),
                durationMs
        ));
    }

    private long elapsedMs(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private String toAiStatusCountsJson(Map<AiScoreStatus, Long> aiStatusCounts) {
        if (aiStatusCounts == null || aiStatusCounts.isEmpty()) {
            return "{}";
        }
        return aiStatusCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "\"%s\":%d".formatted(entry.getKey().name(), entry.getValue()))
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }
}
