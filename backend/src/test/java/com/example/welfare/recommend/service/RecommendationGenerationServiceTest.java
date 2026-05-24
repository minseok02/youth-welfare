package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.recommend.dto.RetrievedRecommendationCandidates;
import com.example.welfare.recommend.dto.ScoredCandidate;
import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.service.UserRecommendationReadService;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
    @Mock private RecommendationRefreshRateLimitService recommendationRefreshRateLimitService;
    @Mock private RecommendationResultReadService recommendationResultReadService;
    @Mock private UserRecommendationReadService userRecommendationReadService;
    @Mock private RecommendationExecutionGuard recommendationExecutionGuard;

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
                recommendationRefreshRateLimitService,
                recommendationResultReadService,
                userRecommendationReadService,
                recommendationExecutionGuard
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
        when(recommendationExecutionGuard.runForUser(eq("user-key-1"), any(), any()))
                .thenAnswer(invocation -> invocation.<java.util.function.Supplier<List<UserRecommendation>>>getArgument(1).get());
        when(recommendationRefreshCacheService.findReusableRecommendedAt("user-key-1"))
                .thenReturn(Optional.of(LocalDateTime.of(2026, 5, 3, 12, 0)));
        when(recommendationResultReadService.findSavedRecommendationsForBatch("user-key-1", LocalDateTime.of(2026, 5, 3, 12, 0)))
                .thenReturn(List.of(cached));

        List<UserRecommendation> result = recommendationGenerationService.recommend(1L, false);

        assertThat(result).containsExactly(cached);
        verify(recommendationRefreshRateLimitService).checkRefreshLimit("user-key-1", false);
        verify(retrievalService, never()).retrieve(any(), any());
        verify(ruleScoringService, never()).score(any(RetrievedRecommendationCandidates.class), eq(snapshot));
        verify(aiScoringService, never()).score(any(), any(), any());
        verify(recommendationPersistenceService, never()).save(any(), any(), any());
        verify(recommendationLogService, never()).refreshLogs(any(), any(), any());
        verify(recommendationRefreshCacheService, never()).markReusable(anyString(), any());
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
        LocalDateTime staleRecommendedAt = LocalDateTime.of(2026, 5, 3, 12, 0);
        UserRecommendation saved = sampleRecommendation(202L, service, LocalDateTime.of(2026, 5, 4, 12, 0));
        LocalDateTime savedRecommendedAt = saved.getRecommendedAt();

        when(userRecommendationReadService.getRecommendationContext(1L))
                .thenReturn(new UserRecommendationReadService.RecommendationReadContext(user, snapshot));
        when(recommendationExecutionGuard.runForUser(eq("user-key-1"), any(), any()))
                .thenAnswer(invocation -> invocation.<java.util.function.Supplier<List<UserRecommendation>>>getArgument(1).get());
        when(recommendationRefreshCacheService.findReusableRecommendedAt("user-key-1"))
                .thenReturn(Optional.of(staleRecommendedAt));
        when(recommendationResultReadService.findSavedRecommendationsForBatch("user-key-1", staleRecommendedAt))
                .thenReturn(List.of());
        when(clusterService.assignCluster(snapshot)).thenReturn("youth_all");
        when(retrievalService.retrieve("youth_all", snapshot)).thenReturn(retrieved);
        when(ruleScoringService.score(retrieved, snapshot)).thenReturn(List.of(scored));
        when(recommendationPostScoringFilterService.filterSpecialTargetMismatches(List.of(scored))).thenReturn(List.of(scored));
        when(aiScoringService.score("youth_all", List.of(scored), snapshot)).thenReturn(List.of(scored));
        when(reRankingService.rerank(List.of(scored), snapshot)).thenReturn(List.of(scored));
        when(reRankingService.getCurrentWeight()).thenReturn(weight);
        when(recommendationPersistenceService.save(user, List.of(scored), weight)).thenReturn(List.of(saved));
        when(recommendationResultReadService.findSavedRecommendationsForBatch("user-key-1", savedRecommendedAt))
                .thenReturn(List.of(saved));

        List<UserRecommendation> result = recommendationGenerationService.recommend(1L, false);

        assertThat(result).containsExactly(saved);
        verify(recommendationRefreshCacheService).evict("user-key-1");
        verify(recommendationLogService).refreshLogs(user, List.of(saved), weight);
        verify(recommendationRefreshCacheService).markReusable("user-key-1", saved.getRecommendedAt());
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
        LocalDateTime savedRecommendedAt = saved.getRecommendedAt();

        when(userRecommendationReadService.getRecommendationContext(1L))
                .thenReturn(new UserRecommendationReadService.RecommendationReadContext(user, snapshot));
        when(recommendationExecutionGuard.runForUser(eq("user-key-1"), any(), any()))
                .thenAnswer(invocation -> invocation.<java.util.function.Supplier<List<UserRecommendation>>>getArgument(1).get());
        when(retrievalService.retrieve("youth_all", snapshot)).thenReturn(retrieved);
        when(ruleScoringService.score(retrieved, snapshot)).thenReturn(List.of(scored));
        when(recommendationPostScoringFilterService.filterSpecialTargetMismatches(List.of(scored))).thenReturn(List.of(scored));
        when(aiScoringService.score("youth_all", List.of(scored), snapshot)).thenReturn(List.of(scored));
        when(reRankingService.rerank(List.of(scored), snapshot)).thenReturn(List.of(scored));
        when(reRankingService.getCurrentWeight()).thenReturn(weight);
        when(recommendationPersistenceService.save(user, List.of(scored), weight)).thenReturn(List.of(saved));
        when(recommendationResultReadService.findSavedRecommendationsForBatch("user-key-1", savedRecommendedAt))
                .thenReturn(List.of(saved));

        List<UserRecommendation> result = recommendationGenerationService.recommend(1L, true);

        assertThat(result).containsExactly(saved);
        verify(recommendationRefreshRateLimitService).checkRefreshLimit("user-key-1", true);
        verify(recommendationRefreshCacheService).evict("user-key-1");
        verify(clusterService, never()).assignCluster(snapshot);
        verify(retrievalService).retrieve("youth_all", snapshot);
        verify(recommendationRefreshCacheService, never()).markReusable(anyString(), any());
    }

    @Test
    @DisplayName("refresh rate limit을 초과하면 추천 파이프라인에 들어가기 전에 R004를 던진다")
    void recommendThrowsWhenRefreshRateLimitExceeded() {
        User user = sampleUser();
        RecommendationUserSnapshot snapshot = sampleSnapshot();

        when(userRecommendationReadService.getRecommendationContext(1L))
                .thenReturn(new UserRecommendationReadService.RecommendationReadContext(user, snapshot));
        org.mockito.Mockito.doThrow(new CustomException(ErrorCode.RECOMMENDATION_REFRESH_RATE_LIMIT_EXCEEDED))
                .when(recommendationRefreshRateLimitService)
                .checkRefreshLimit("user-key-1", true);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> recommendationGenerationService.recommend(1L, true))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RECOMMENDATION_REFRESH_RATE_LIMIT_EXCEEDED));

        verify(recommendationExecutionGuard, never()).runForUser(anyString(), any(), any());
        verify(retrievalService, never()).retrieve(any(), any());
    }

    @Test
    @DisplayName("refresh 응답은 저장 직후 DB 재조회 순서(final_score DESC, service_id DESC)를 따른다")
    void recommendReturnsSavedRecommendationsInRepositoryOrder() {
        User user = sampleUser();
        RecommendationUserSnapshot snapshot = sampleSnapshot();
        WelfareService firstService = sampleService(11L, "청년 월세 지원");
        WelfareService secondService = sampleService(12L, "청년 전세 지원");
        RetrievedRecommendationCandidates retrieved = new RetrievedRecommendationCandidates(List.of(firstService, secondService), null);
        ScoredCandidate firstScored = sampleScoredCandidate(firstService);
        ScoredCandidate secondScored = sampleScoredCandidate(secondService);
        ScoreWeight weight = sampleWeight();
        LocalDateTime recommendedAt = LocalDateTime.of(2026, 5, 17, 23, 20);

        UserRecommendation persistedOutOfOrder = sampleRecommendation(501L, firstService, recommendedAt);
        UserRecommendation persistedOrdered = sampleRecommendation(502L, secondService, recommendedAt);

        when(userRecommendationReadService.getRecommendationContext(1L))
                .thenReturn(new UserRecommendationReadService.RecommendationReadContext(user, snapshot));
        when(recommendationExecutionGuard.runForUser(eq("user-key-1"), any(), any()))
                .thenAnswer(invocation -> invocation.<java.util.function.Supplier<List<UserRecommendation>>>getArgument(1).get());
        when(recommendationRefreshCacheService.findReusableRecommendedAt("user-key-1"))
                .thenReturn(Optional.empty());
        when(clusterService.assignCluster(snapshot)).thenReturn("youth_all");
        when(retrievalService.retrieve("youth_all", snapshot)).thenReturn(retrieved);
        when(ruleScoringService.score(retrieved, snapshot)).thenReturn(List.of(firstScored, secondScored));
        when(recommendationPostScoringFilterService.filterSpecialTargetMismatches(List.of(firstScored, secondScored)))
                .thenReturn(List.of(firstScored, secondScored));
        when(aiScoringService.score("youth_all", List.of(firstScored, secondScored), snapshot))
                .thenReturn(List.of(firstScored, secondScored));
        when(reRankingService.rerank(List.of(firstScored, secondScored), snapshot))
                .thenReturn(List.of(firstScored, secondScored));
        when(reRankingService.getCurrentWeight()).thenReturn(weight);
        when(recommendationPersistenceService.save(user, List.of(firstScored, secondScored), weight))
                .thenReturn(List.of(persistedOutOfOrder, persistedOrdered));
        when(recommendationResultReadService.findSavedRecommendationsForBatch("user-key-1", recommendedAt))
                .thenReturn(List.of(persistedOrdered, persistedOutOfOrder));

        List<UserRecommendation> result = recommendationGenerationService.recommend(1L, false);

        assertThat(result).containsExactly(persistedOrdered, persistedOutOfOrder);
        verify(recommendationLogService).refreshLogs(user, List.of(persistedOrdered, persistedOutOfOrder), weight);
        verify(recommendationRefreshCacheService).markReusable("user-key-1", recommendedAt);
    }

    @Test
    @DisplayName("같은 사용자 추천 생성이 이미 진행 중이면 최신 저장 추천으로 폴백한다")
    void recommendReturnsLatestSavedRecommendationsWhenExecutionBusy() {
        User user = sampleUser();
        RecommendationUserSnapshot snapshot = sampleSnapshot();
        UserRecommendation saved = sampleRecommendation(909L);

        when(userRecommendationReadService.getRecommendationContext(1L))
                .thenReturn(new UserRecommendationReadService.RecommendationReadContext(user, snapshot));
        when(recommendationExecutionGuard.runForUser(eq("user-key-1"), any(), any()))
                .thenAnswer(invocation -> invocation.<java.util.function.Supplier<List<UserRecommendation>>>getArgument(2).get());
        when(recommendationResultReadService.findLatestSavedRecommendations("user-key-1"))
                .thenReturn(List.of(saved));

        List<UserRecommendation> result = recommendationGenerationService.recommend(1L, false);

        assertThat(result).containsExactly(saved);
        verify(retrievalService, never()).retrieve(any(), any());
        verify(recommendationPersistenceService, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("저장 후 로그 후처리가 실패해도 저장 추천은 그대로 반환하고 refresh 마커는 유지한다")
    void recommendReturnsSavedRecommendationsWhenLogRefreshFails() {
        User user = sampleUser();
        RecommendationUserSnapshot snapshot = sampleSnapshot();
        WelfareService service = sampleService();
        RetrievedRecommendationCandidates retrieved = new RetrievedRecommendationCandidates(List.of(service), null);
        ScoredCandidate scored = sampleScoredCandidate(service);
        ScoreWeight weight = sampleWeight();
        UserRecommendation saved = sampleRecommendation(404L);
        LocalDateTime savedRecommendedAt = saved.getRecommendedAt();

        when(userRecommendationReadService.getRecommendationContext(1L))
                .thenReturn(new UserRecommendationReadService.RecommendationReadContext(user, snapshot));
        when(recommendationExecutionGuard.runForUser(eq("user-key-1"), any(), any()))
                .thenAnswer(invocation -> invocation.<java.util.function.Supplier<List<UserRecommendation>>>getArgument(1).get());
        when(recommendationRefreshCacheService.findReusableRecommendedAt("user-key-1"))
                .thenReturn(Optional.empty());
        when(clusterService.assignCluster(snapshot)).thenReturn("youth_all");
        when(retrievalService.retrieve("youth_all", snapshot)).thenReturn(retrieved);
        when(ruleScoringService.score(retrieved, snapshot)).thenReturn(List.of(scored));
        when(recommendationPostScoringFilterService.filterSpecialTargetMismatches(List.of(scored))).thenReturn(List.of(scored));
        when(aiScoringService.score("youth_all", List.of(scored), snapshot)).thenReturn(List.of(scored));
        when(reRankingService.rerank(List.of(scored), snapshot)).thenReturn(List.of(scored));
        when(reRankingService.getCurrentWeight()).thenReturn(weight);
        when(recommendationPersistenceService.save(user, List.of(scored), weight)).thenReturn(List.of(saved));
        when(recommendationResultReadService.findSavedRecommendationsForBatch("user-key-1", savedRecommendedAt))
                .thenReturn(List.of(saved));
        org.mockito.Mockito.doThrow(new IllegalStateException("log failed"))
                .when(recommendationLogService).refreshLogs(user, List.of(saved), weight);

        List<UserRecommendation> result = recommendationGenerationService.recommend(1L, false);

        assertThat(result).containsExactly(saved);
        verify(recommendationRefreshCacheService).markReusable("user-key-1", saved.getRecommendedAt());
    }

    @Test
    @DisplayName("같은 사용자 추천 생성이 진행 중이고 저장 결과도 없으면 R003 을 던진다")
    void recommendThrowsWhenExecutionBusyAndNoFallbackResult() {
        User user = sampleUser();
        RecommendationUserSnapshot snapshot = sampleSnapshot();

        when(userRecommendationReadService.getRecommendationContext(1L))
                .thenReturn(new UserRecommendationReadService.RecommendationReadContext(user, snapshot));
        when(recommendationExecutionGuard.runForUser(eq("user-key-1"), any(), any()))
                .thenThrow(new CustomException(ErrorCode.RECOMMENDATION_ALREADY_RUNNING));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> recommendationGenerationService.recommend(1L, false))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.RECOMMENDATION_ALREADY_RUNNING));
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
        return sampleService(11L, "청년 월세 지원");
    }

    private WelfareService sampleService(Long id, String title) {
        return WelfareService.builder()
                .id(id)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y" + id)
                .title(title)
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
        return sampleRecommendation(id, sampleService(), LocalDateTime.of(2026, 5, 3, 12, 0));
    }

    private UserRecommendation sampleRecommendation(Long id, WelfareService service, LocalDateTime recommendedAt) {
        return UserRecommendation.builder()
                .id(id)
                .userKey("user-key-1")
                .service(service)
                .finalScore(BigDecimal.valueOf(0.91))
                .recommendedAt(recommendedAt)
                .build();
    }
}
