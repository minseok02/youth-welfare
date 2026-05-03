package com.example.welfare.recommend.facade;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.recommend.dto.RetrievedRecommendationCandidates;
import com.example.welfare.recommend.dto.ScoredCandidate;
import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.UserRecommendationRepository;
import com.example.welfare.recommend.service.*;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserRepository;
import com.example.welfare.user.service.UserReadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 추천 파이프라인 유일한 진입점
 * Controller는 이 Facade만 호출. 개별 Service 직접 호출 금지 (CLAUDE.md 원칙)
 *
 * 파이프라인:
 * ① ClusterService.assignCluster
 * ② RetrievalService.retrieve (K=50)
 * ③ RuleScoringService.score
 * ④ AiScoringService.score (NULL-safe)
 * ⑤ ReRankingService.rerank (score_weights 테이블 조회)
 * ⑥ RecommendationPersistenceService.save
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationFacade {

    private final UserRepository userRepository;
    private final UserReadService userReadService;
    private final ClusterService clusterService;
    private final RetrievalService retrievalService;
    private final RuleScoringService ruleScoringService;
    private final AiScoringService aiScoringService;
    private final ReRankingService reRankingService;
    private final RecommendationPostScoringFilterService recommendationPostScoringFilterService;
    private final RecommendationPersistenceService persistenceService;
    private final RecommendationLogService recommendationLogService;
    private final RecommendationRefreshCacheService recommendationRefreshCacheService;
    private final RecommendationBookmarkCommandService recommendationBookmarkCommandService;
    private final UserRecommendationRepository userRecommendationRepository;

    /**
     * 추천 생성 및 저장 — 로그인 시 또는 명시적 갱신 요청 시 실행
     */
    @Transactional
    public List<UserRecommendation> recommend(Long userId) {
        return recommend(userId, false);
    }

    // personal=true: 군집 캐시 무시, 개인 프로필 기반 실시간 AI 호출
    @Transactional
    public List<UserRecommendation> recommend(Long userId, boolean personal) {
        UserReadService.RecommendationReadContext context = userReadService.getRecommendationContext(userId);
        RecommendationUserSnapshot snapshot = context.snapshot();
        User user = context.user();
        String userKey = snapshot.userKey();

        if (personal) {
            recommendationRefreshCacheService.evict(userKey);
        } else if (recommendationRefreshCacheService.canReuse(userKey)) {
            List<UserRecommendation> cached = userRecommendationRepository.findLatestByUserKeyOrderByFinalScoreDesc(userKey);
            if (!cached.isEmpty()) {
                log.info("[RecommendationFacade] refresh cache hit userId={} userKey={}", userId, userKey);
                return cached;
            }
            recommendationRefreshCacheService.evict(userKey);
        }

        // ① 군집 결정 — personal 모드는 youth_all로 강제 (캐시 미사용)
        String clusterId = personal ? "youth_all" : clusterService.assignCluster(snapshot);

        // ② 후보 추출
        RetrievedRecommendationCandidates retrieved = retrievalService.retrieve(clusterId, snapshot);
        if (retrieved.isEmpty()) {
            log.info("[RecommendationFacade] 후보 없음 userId={}", userId);
            return List.of();
        }
        List<WelfareService> candidates = retrieved.candidates();

        // ③ Rule 점수
        List<ScoredCandidate> scored = ruleScoringService.score(retrieved, snapshot);

        // ③-b 특수 대상 불일치 정책 제거 (사용자와 맞지 않는 장애/농촌/다문화 등)
        // 숫자 임계값이 아닌 RuleScoringService가 명시한 mismatch 플래그를 사용
        scored = recommendationPostScoringFilterService.filterSpecialTargetMismatches(scored);
        if (scored.isEmpty()) {
            log.info("[RecommendationFacade] 필터 후 후보 없음 userId={}", userId);
            return List.of();
        }

        // ④ AI 점수 (실패 시 null 유지)
        scored = aiScoringService.score(clusterId, scored, snapshot);

        // ⑤ 최종 점수 계산 + 정렬
        List<ScoredCandidate> reranked = reRankingService.rerank(scored);
        ScoreWeight weight = reRankingService.getCurrentWeight();

        // ⑥ 저장 (recommended_at, rule_weight_used, ai_weight_used 필수)
        List<UserRecommendation> saved = persistenceService.save(user, reranked, weight);

        // ⑦ CTR 추적용 로그 생성 — 미클릭 이전 로그 정리 후 새 로그 기록
        recommendationLogService.refreshLogs(user, saved, weight);
        if (!personal) {
            recommendationRefreshCacheService.markReusable(userKey);
        }

        return saved;
    }

    /**
     * 저장된 추천 목록 조회 (실시간 AI 추가 호출 없음)
     */
    @Transactional(readOnly = true)
    public List<UserRecommendation> getRecommendations(Long userId, int size) {
        String userKey = resolveUserKey(userId);
        return userRecommendationRepository.findTopByUserKey(userKey, PageRequest.of(0, size));
    }

    /**
     * 북마크 토글
     */
    @Transactional
    public void toggleBookmark(Long userId, Long recommendationId) {
        recommendationBookmarkCommandService.toggleRecommendationBookmark(userId, recommendationId);
    }

    private String resolveUserKey(Long userId) {
        return userRepository.findUserKeyById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
