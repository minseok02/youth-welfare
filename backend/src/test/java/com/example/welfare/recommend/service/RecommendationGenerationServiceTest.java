package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.recommend.dto.RetrievedRecommendationCandidates;
import com.example.welfare.recommend.dto.ScoredCandidate;
import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.service.UserRecommendationReadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationGenerationServiceTest {

    @Mock private ClusterService clusterService;
    @Mock private RetrievalService retrievalService;
    @Mock private RuleScoringService ruleScoringService;
    @Mock private AiScoringService aiScoringService;
    @Mock private ReRankingService reRankingService;
    @Mock private RecommendationPostScoringFilterService recommendationPostScoringFilterService;
    @Mock private RecommendationPersistenceService recommendationPersistenceService;
    @Mock private RecommendationLogService recommendationLogService;
    @Mock private RecommendationRefreshCacheService recommendationRefreshCacheService;
    @Mock private RecommendationResultReadService recommendationResultReadService;
    @Mock private UserRecommendationReadService userRecommendationReadService;

    private RecommendationGenerationService recommendationGenerationService;

    @BeforeEach
    void setUp() {
        recommendationGenerationService = new RecommendationGenerationService(
                clusterService,
                retrievalService,
                ruleScoringService,
                aiScoringService,
                reRankingService,
                recommendationPostScoringFilterService,
                recommendationPersistenceService,
                recommendationLogService,
                recommendationRefreshCacheService,
                recommendationResultReadService,
                userRecommendationReadService
        );
    }

    @Test
    @DisplayName("non-personal refresh 캐시가 있으면 기존 저장 추천을 재사용하고 파이프라인을 생략한다")
    void recommendReturnsSavedRecommendationsOnRefreshCacheHit() {
        User user = sampleUser();
        RecommendationUserSnapshot snapshot = sampleSnapshot();
        UserRecommendation cached = sampleRecommendation(101L);

        when(userRecommendationReadService.getRecommendationContext(1L))
                .thenReturn(new UserRecommendationReadService.RecommendationReadContext(user, snapshot));
        when(recommendationRefreshCacheService.canReuse("user-key-1")).thenReturn(true);
        when(recommendationResultReadService.findLatestSavedRecommendations("user-key-1"))
                .thenReturn(List.of(cached));

        List<UserRecommendation> result = recommendationGenerationService.recommend(1L, false);

        assertThat(result).containsExactly(cached);
        verify(retrievalService, never()).retrieve(any(), any());
        verify(ruleScoringService, never()).score(any(RetrievedRecommendationCandidates.class), eq(snapshot));
        verify(aiScoringService, never()).score(any(), any(), any());
        verify(recommendationPersistenceService, never()).save(any(), any(), any());
        verify(recommendationLogService, never()).refreshLogs(any(), any(), any());
        verify(recommendationRefreshCacheService, never()).markReusable(anyString());
    }

    @Test
    @DisplayName("non-personal refresh 캐시 hit 이지만 저장 추천이 비어 있으면 재계산 후 마커를 다시 저장한다")
    void recommendRecomputesWhenRefreshMarkerExistsButRowsMissing() {
        User user = sampleUser();
        RecommendationUserSnapshot snapshot = sampleSnapshot();
        WelfareService service = sampleService();
        RetrievedRecommendationCandidates retrieved = new RetrievedRecommendationCandidates(List.of(service), null);
        ScoredCandidate scored = sampleScoredCandidate(service);
        ScoreWeight weight = sampleWeight();
        UserRecommendation saved = sampleRecommendation(202L);

        when(userRecommendationReadService.getRecommendationContext(1L))
                .thenReturn(new UserRecommendationReadService.RecommendationReadContext(user, snapshot));
        when(recommendationRefreshCacheService.canReuse("user-key-1")).thenReturn(true);
        when(recommendationResultReadService.findLatestSavedRecommendations("user-key-1"))
                .thenReturn(List.of());
        when(clusterService.assignCluster(snapshot)).thenReturn("youth_all");
        when(retrievalService.retrieve("youth_all", snapshot)).thenReturn(retrieved);
        when(ruleScoringService.score(retrieved, snapshot)).thenReturn(List.of(scored));
        when(recommendationPostScoringFilterService.filterSpecialTargetMismatches(List.of(scored))).thenReturn(List.of(scored));
        when(aiScoringService.score("youth_all", List.of(scored), snapshot)).thenReturn(List.of(scored));
        when(reRankingService.rerank(List.of(scored))).thenReturn(List.of(scored));
        when(reRankingService.getCurrentWeight()).thenReturn(weight);
        when(recommendationPersistenceService.save(user, List.of(scored), weight)).thenReturn(List.of(saved));

        List<UserRecommendation> result = recommendationGenerationService.recommend(1L, false);

        assertThat(result).containsExactly(saved);
        verify(recommendationRefreshCacheService).evict("user-key-1");
        verify(recommendationLogService).refreshLogs(user, List.of(saved), weight);
        verify(recommendationRefreshCacheService).markReusable("user-key-1");
    }

    @Test
    @DisplayName("personal refresh 는 기존 non-personal refresh 캐시를 먼저 비우고 실계산한다")
    void recommendPersonalBypassesAndEvictsRefreshCache() {
        User user = sampleUser();
        RecommendationUserSnapshot snapshot = sampleSnapshot();
        WelfareService service = sampleService();
        RetrievedRecommendationCandidates retrieved = new RetrievedRecommendationCandidates(List.of(service), null);
        ScoredCandidate scored = sampleScoredCandidate(service);
        ScoreWeight weight = sampleWeight();
        UserRecommendation saved = sampleRecommendation(303L);

        when(userRecommendationReadService.getRecommendationContext(1L))
                .thenReturn(new UserRecommendationReadService.RecommendationReadContext(user, snapshot));
        when(retrievalService.retrieve("youth_all", snapshot)).thenReturn(retrieved);
        when(ruleScoringService.score(retrieved, snapshot)).thenReturn(List.of(scored));
        when(recommendationPostScoringFilterService.filterSpecialTargetMismatches(List.of(scored))).thenReturn(List.of(scored));
        when(aiScoringService.score("youth_all", List.of(scored), snapshot)).thenReturn(List.of(scored));
        when(reRankingService.rerank(List.of(scored))).thenReturn(List.of(scored));
        when(reRankingService.getCurrentWeight()).thenReturn(weight);
        when(recommendationPersistenceService.save(user, List.of(scored), weight)).thenReturn(List.of(saved));

        List<UserRecommendation> result = recommendationGenerationService.recommend(1L, true);

        assertThat(result).containsExactly(saved);
        verify(recommendationRefreshCacheService).evict("user-key-1");
        verify(clusterService, never()).assignCluster(snapshot);
        verify(retrievalService).retrieve("youth_all", snapshot);
        verify(recommendationRefreshCacheService, never()).markReusable(anyString());
    }

    private User sampleUser() {
        return User.builder()
                .id(1L)
                .userKey("user-key-1")
                .email("user@example.com")
                .passwordHash("hash")
                .build();
    }

    private RecommendationUserSnapshot sampleSnapshot() {
        return new RecommendationUserSnapshot(
                1L, "user-key-1", 25, "20S", "서울", "강남구", "11680", (byte) 5,
                "ONE_PERSON", "EMPLOYED", 10, 0.5, List.of(), List.of(), List.of()
        );
    }

    private WelfareService sampleService() {
        return WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y11")
                .title("청년 월세 지원")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
    }

    private ScoredCandidate sampleScoredCandidate(WelfareService service) {
        return ScoredCandidate.builder()
                .service(service)
                .ruleBaseScore(0.61)
                .ruleWeightedScore(0.72)
                .finalScore(0.91)
                .build();
    }

    private ScoreWeight sampleWeight() {
        return ScoreWeight.builder()
                .weightKey("GROWTH")
                .ruleWeight(BigDecimal.valueOf(0.7))
                .aiWeight(BigDecimal.valueOf(0.3))
                .minLogCount(0)
                .isActive(true)
                .build();
    }

    private UserRecommendation sampleRecommendation(Long id) {
        return UserRecommendation.builder()
                .id(id)
                .userKey("user-key-1")
                .service(sampleService())
                .finalScore(BigDecimal.valueOf(0.91))
                .recommendedAt(LocalDateTime.of(2026, 5, 3, 12, 0))
                .build();
    }
}
