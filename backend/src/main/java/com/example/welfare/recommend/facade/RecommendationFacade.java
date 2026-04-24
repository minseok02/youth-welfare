package com.example.welfare.recommend.facade;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.ScoredCandidate;
import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.UserRecommendationRepository;
import com.example.welfare.recommend.service.*;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserRepository;
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
    private final ClusterService clusterService;
    private final RetrievalService retrievalService;
    private final RuleScoringService ruleScoringService;
    private final AiScoringService aiScoringService;
    private final ReRankingService reRankingService;
    private final RecommendationPersistenceService persistenceService;
    private final RecommendationLogService recommendationLogService;
    private final UserRecommendationRepository userRecommendationRepository;

    /**
     * 추천 생성 및 저장 — 로그인 시 또는 명시적 갱신 요청 시 실행
     */
    @Transactional
    public List<UserRecommendation> recommend(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // ① 군집 (1차 고정: youth_all)
        String clusterId = clusterService.assignCluster(user);

        // ② 후보 추출
        List<WelfareService> candidates = retrievalService.retrieve(clusterId, user);
        if (candidates.isEmpty()) {
            log.info("[RecommendationFacade] 후보 없음 userId={}", userId);
            return List.of();
        }

        // ③ Rule 점수
        List<ScoredCandidate> scored = ruleScoringService.score(candidates, user);

        // ③-b 노이즈 컷오프: YouthPolicyFilter 기본 신호만 받은 정책 제거
        // relevanceBonus 최대값은 15+8=23점 — 그 외 가점(관심분야/대상/마감 등)이 없는 정책은 제외
        // rule_base_score <= 8 → 청년 신호만 있고 실질 매칭 없음
        scored = scored.stream()
                .filter(c -> c.getRuleBaseScore() > 8.0)
                .collect(java.util.stream.Collectors.toList());
        if (scored.isEmpty()) {
            log.info("[RecommendationFacade] 컷오프 후 후보 없음 userId={}", userId);
            return List.of();
        }

        // ④ AI 점수 (실패 시 null 유지)
        scored = aiScoringService.score(clusterId, scored, user);

        // ⑤ 최종 점수 계산 + 정렬
        List<ScoredCandidate> reranked = reRankingService.rerank(scored);
        ScoreWeight weight = reRankingService.getCurrentWeight();

        // ⑥ 저장 (recommended_at, rule_weight_used, ai_weight_used 필수)
        List<UserRecommendation> saved = persistenceService.save(user, reranked, weight);

        // ⑦ CTR 추적용 로그 생성 — 미클릭 이전 로그 정리 후 새 로그 기록
        recommendationLogService.refreshLogs(user, saved, weight);

        return saved;
    }

    /**
     * 저장된 추천 목록 조회 (실시간 AI 추가 호출 없음)
     */
    @Transactional(readOnly = true)
    public List<UserRecommendation> getRecommendations(Long userId, int size) {
        return userRecommendationRepository.findTopByUserId(userId, PageRequest.of(0, size));
    }

    /**
     * 북마크 토글
     */
    @Transactional
    public void toggleBookmark(Long userId, Long recommendationId) {
        UserRecommendation rec = userRecommendationRepository.findByIdAndUserId(recommendationId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECOMMENDATION_NOT_FOUND));
        rec.toggleBookmark();
    }
}
