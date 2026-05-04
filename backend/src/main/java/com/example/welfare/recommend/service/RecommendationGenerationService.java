package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.recommend.dto.RetrievedRecommendationCandidates;
import com.example.welfare.recommend.dto.ScoredCandidate;
import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.service.UserRecommendationReadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

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
    private final RecommendationResultReadService recommendationResultReadService;
    private final UserRecommendationReadService userRecommendationReadService;
    private final RecommendationExecutionGuard recommendationExecutionGuard;

    public List<UserRecommendation> recommend(Long userId, boolean personal) {
        UserRecommendationReadService.RecommendationReadContext context =
                userRecommendationReadService.getRecommendationContext(userId);
        RecommendationUserSnapshot snapshot = context.snapshot();
        User user = context.user();
        String userKey = snapshot.userKey();

        return recommendationExecutionGuard.runForUser(
                userKey,
                () -> doRecommend(user, snapshot, personal),
                () -> recommendationResultReadService.findLatestSavedRecommendations(userKey)
        );
    }

    private List<UserRecommendation> doRecommend(User user, RecommendationUserSnapshot snapshot, boolean personal) {
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
                    log.info("[RecommendationGenerationService] refresh cache hit userKey={} recommendedAt={}",
                            userKey, reusableRecommendedAt.get());
                    return cached;
                }
                recommendationRefreshCacheService.evict(userKey);
            }
        }

        String clusterId = personal ? "youth_all" : clusterService.assignCluster(snapshot);

        RetrievedRecommendationCandidates retrieved = retrievalService.retrieve(clusterId, snapshot);
        if (retrieved.isEmpty()) {
            log.info("[RecommendationGenerationService] 후보 없음 userKey={}", userKey);
            return List.of();
        }

        List<ScoredCandidate> scored = ruleScoringService.score(retrieved, snapshot);
        scored = recommendationPostScoringFilterService.filterSpecialTargetMismatches(scored);
        if (scored.isEmpty()) {
            log.info("[RecommendationGenerationService] 필터 후 후보 없음 userKey={}", userKey);
            return List.of();
        }

        scored = aiScoringService.score(clusterId, scored, snapshot);

        List<ScoredCandidate> reranked = reRankingService.rerank(scored);
        ScoreWeight weight = reRankingService.getCurrentWeight();
        List<UserRecommendation> saved = recommendationPersistenceService.save(user, reranked, weight);

        recommendationLogService.refreshLogs(user, saved, weight);
        if (!personal) {
            recommendationRefreshCacheService.markReusable(userKey, saved.isEmpty() ? null : saved.get(0).getRecommendedAt());
        }
        return saved;
    }
}
