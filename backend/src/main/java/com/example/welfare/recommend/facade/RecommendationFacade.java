package com.example.welfare.recommend.facade;

import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.service.RecommendationAccessService;
import com.example.welfare.recommend.service.RecommendationBookmarkCommandService;
import com.example.welfare.recommend.service.RecommendationGenerationService;
import lombok.RequiredArgsConstructor;
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
@Component
@RequiredArgsConstructor
public class RecommendationFacade {

    private final RecommendationGenerationService recommendationGenerationService;
    private final RecommendationAccessService recommendationAccessService;
    private final RecommendationBookmarkCommandService recommendationBookmarkCommandService;

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
        return recommendationGenerationService.recommend(userId, personal);
    }

    /**
     * 저장된 추천 목록 조회 (실시간 AI 추가 호출 없음)
     */
    @Transactional(readOnly = true)
    public List<UserRecommendation> getRecommendations(Long userId, int size) {
        return recommendationAccessService.getRecommendations(userId, size);
    }

    /**
     * 북마크 토글
     */
    @Transactional
    public void toggleBookmark(Long userId, Long recommendationId) {
        recommendationBookmarkCommandService.toggleRecommendationBookmark(userId, recommendationId);
    }
}
