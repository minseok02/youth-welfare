package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.recommend.dto.ScoredCandidate;
import com.example.welfare.recommend.entity.ScoreWeight;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReRankingServiceTest {

    @Mock
    private ScoreWeightService scoreWeightService;

    @Test
    @DisplayName("동점이면 AI 점수, 마감일, 조회수, 최신성 순으로 정렬한다")
    void rerankUsesTieBreakers() {
        ReRankingService reRankingService = new ReRankingService(
                new ScoreNormalizer(),
                scoreWeightService,
                new RecommendationDiversityService()
        );

        when(scoreWeightService.getActiveWeight()).thenReturn(ScoreWeight.builder()
                .weightKey("COLD_START")
                .ruleWeight(BigDecimal.valueOf(0.8))
                .aiWeight(BigDecimal.valueOf(0.2))
                .minLogCount(0)
                .isActive(true)
                .build());

        ScoredCandidate lowerPriority = candidate(
                service(1L, LocalDate.now().plusDays(20), 100, 1000L, LocalDateTime.of(2026, 1, 1, 0, 0)),
                10.0, 80.0
        );
        ScoredCandidate higherPriority = candidate(
                service(2L, LocalDate.now().plusDays(3), 200, 1500L, LocalDateTime.of(2026, 2, 1, 0, 0)),
                10.0, 80.0
        );

        List<ScoredCandidate> ranked = reRankingService.rerank(List.of(lowerPriority, higherPriority));

        assertThat(ranked).extracting(c -> c.getService().getId())
                .containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("우선순위 불일치 후보는 AI 점수가 높아도 AI 가중치를 낮춰 aligned 후보 뒤로 보낸다")
    void rerankDownweightsAiForPriorityMismatch() {
        ReRankingService reRankingService = new ReRankingService(
                new ScoreNormalizer(),
                scoreWeightService,
                new RecommendationDiversityService()
        );

        when(scoreWeightService.getActiveWeight()).thenReturn(ScoreWeight.builder()
                .weightKey("STABLE")
                .ruleWeight(BigDecimal.valueOf(0.6))
                .aiWeight(BigDecimal.valueOf(0.4))
                .minLogCount(100)
                .isActive(true)
                .build());

        ScoredCandidate aligned = candidate(
                service(10L, LocalDate.now().plusDays(20), 100, 1000L, LocalDateTime.of(2026, 1, 1, 0, 0)),
                100.0, 60.0, false, false, null
        );
        ScoredCandidate mismatch = candidate(
                service(11L, LocalDate.now().plusDays(20), 100, 1000L, LocalDateTime.of(2026, 1, 1, 0, 0)),
                80.0, 100.0, false, true, null
        );

        List<ScoredCandidate> ranked = reRankingService.rerank(List.of(mismatch, aligned));

        assertThat(ranked).extracting(c -> c.getService().getId())
                .containsExactly(10L, 11L);
    }

    @Test
    @DisplayName("1순위 매칭 후보는 2순위 매칭 후보보다 final score 보정을 더 크게 받는다")
    void rerankPrefersFirstPriorityOverSecondPriority() {
        ReRankingService reRankingService = new ReRankingService(
                new ScoreNormalizer(),
                scoreWeightService,
                new RecommendationDiversityService()
        );

        when(scoreWeightService.getActiveWeight()).thenReturn(ScoreWeight.builder()
                .weightKey("STABLE")
                .ruleWeight(BigDecimal.valueOf(0.6))
                .aiWeight(BigDecimal.valueOf(0.4))
                .minLogCount(100)
                .isActive(true)
                .build());

        ScoredCandidate firstPriority = candidate(
                service(20L, LocalDate.now().plusDays(20), 100, 1000L, LocalDateTime.of(2026, 1, 1, 0, 0)),
                70.0, 60.0, false, false, 1
        );
        ScoredCandidate secondPriority = candidate(
                service(21L, LocalDate.now().plusDays(20), 100, 1000L, LocalDateTime.of(2026, 1, 1, 0, 0)),
                85.0, 80.0, false, false, 2
        );

        List<ScoredCandidate> ranked = reRankingService.rerank(List.of(secondPriority, firstPriority));

        assertThat(ranked).extracting(c -> c.getService().getId())
                .containsExactly(20L, 21L);
    }

    @Test
    @DisplayName("상위 노출 구간에서 같은 카테고리가 연속되면 다른 버킷 후보를 앞으로 당긴다")
    void rerankDiversifiesTopWindowAcrossBuckets() {
        ReRankingService reRankingService = new ReRankingService(
                new ScoreNormalizer(),
                scoreWeightService,
                new RecommendationDiversityService()
        );

        when(scoreWeightService.getActiveWeight()).thenReturn(ScoreWeight.builder()
                .weightKey("COLD_START")
                .ruleWeight(BigDecimal.valueOf(0.8))
                .aiWeight(BigDecimal.valueOf(0.2))
                .minLogCount(0)
                .isActive(true)
                .build());

        ScoredCandidate housingTop = candidate(
                service(31L, "주거", LocalDate.now().plusDays(20), 100, 1000L, LocalDateTime.of(2026, 1, 1, 0, 0)),
                100.0,
                null
        );
        ScoredCandidate housingSecond = candidate(
                service(32L, "주거", LocalDate.now().plusDays(20), 100, 1000L, LocalDateTime.of(2026, 1, 1, 0, 0)),
                97.0,
                null
        );
        ScoredCandidate jobThird = candidate(
                service(33L, "일자리", LocalDate.now().plusDays(20), 100, 1000L, LocalDateTime.of(2026, 1, 1, 0, 0)),
                96.0,
                null
        );

        List<ScoredCandidate> ranked = reRankingService.rerank(List.of(housingTop, housingSecond, jobThird));

        assertThat(ranked).extracting(c -> c.getService().getId())
                .containsExactly(31L, 33L, 32L);
    }

    @Test
    @DisplayName("우선순위가 없는 사용자는 근접한 top band 안에서 fallback bonus로 top1 서비스를 분산한다")
    void rerankDiversifiesTop1ServiceForNoPriorityUsers() {
        ReRankingService reRankingService = new ReRankingService(
                new ScoreNormalizer(),
                scoreWeightService,
                new RecommendationDiversityService()
        );

        when(scoreWeightService.getActiveWeight()).thenReturn(ScoreWeight.builder()
                .weightKey("COLD_START")
                .ruleWeight(BigDecimal.valueOf(0.8))
                .aiWeight(BigDecimal.valueOf(0.2))
                .minLogCount(0)
                .isActive(true)
                .build());

        ScoredCandidate bokjiroTop = candidate(
                service(41L, WelfareService.SourceType.BOKJIRO_CENTRAL, "주거", LocalDate.now().plusDays(20), 100, 1000L, LocalDateTime.of(2026, 1, 1, 0, 0)),
                100.0,
                null
        );
        ScoredCandidate gov24Second = candidate(
                service(42L, WelfareService.SourceType.GOV24, "교육·직업훈련", LocalDate.now().plusDays(20), 100, 1000L, LocalDateTime.of(2026, 1, 1, 0, 0)),
                97.0,
                null
        );
        ScoredCandidate youthThird = candidate(
                service(43L, WelfareService.SourceType.YOUTH, "일자리", LocalDate.now().plusDays(20), 100, 1000L, LocalDateTime.of(2026, 1, 1, 0, 0)),
                96.0,
                null
        );

        RecommendationUserSnapshot userPrefersGov24 = noPrioritySnapshot("user-a");
        RecommendationUserSnapshot userKeepsTop = noPrioritySnapshot("user-c");

        List<ScoredCandidate> gov24First = reRankingService.rerank(
                List.of(bokjiroTop, gov24Second, youthThird),
                userPrefersGov24
        );
        List<ScoredCandidate> topFirst = reRankingService.rerank(
                List.of(bokjiroTop, gov24Second, youthThird),
                userKeepsTop
        );

        assertThat(gov24First.get(0).getService().getId()).isNotEqualTo(topFirst.get(0).getService().getId());
    }

    @Test
    @DisplayName("우선순위가 없는 사용자는 top band 내부에서 category bucket 기준으로 top1을 회전시킨다")
    void rerankRotatesNoPriorityTop1AcrossCategoryBuckets() {
        ReRankingService reRankingService = new ReRankingService(
                new ScoreNormalizer(),
                scoreWeightService,
                new RecommendationDiversityService()
        );

        when(scoreWeightService.getActiveWeight()).thenReturn(ScoreWeight.builder()
                .weightKey("COLD_START")
                .ruleWeight(BigDecimal.valueOf(0.8))
                .aiWeight(BigDecimal.valueOf(0.2))
                .minLogCount(0)
                .isActive(true)
                .build());

        ScoredCandidate housingTop = candidate(
                service(51L, WelfareService.SourceType.BOKJIRO_CENTRAL, "주거", LocalDate.now().plusDays(20), 100, 1000L, LocalDateTime.of(2026, 1, 1, 0, 0)),
                100.0,
                null
        );
        ScoredCandidate financeSecond = candidate(
                service(52L, WelfareService.SourceType.BOKJIRO_LOCAL, "금융·생활지원", LocalDate.now().plusDays(20), 100, 1000L, LocalDateTime.of(2026, 1, 1, 0, 0)),
                98.0,
                null
        );
        ScoredCandidate educationThird = candidate(
                service(53L, WelfareService.SourceType.GOV24, "교육·직업훈련", LocalDate.now().plusDays(20), 100, 1000L, LocalDateTime.of(2026, 1, 1, 0, 0)),
                97.0,
                null
        );

        List<ScoredCandidate> ranked = reRankingService.rerank(
                List.of(housingTop, financeSecond, educationThird),
                noPrioritySnapshot("user-b")
        );

        assertThat(ranked.get(0).getService().getId()).isNotEqualTo(51L);
        assertThat(ranked.get(0).getService().getUnifiedCategory())
                .isIn("금융·생활지원", "교육·직업훈련");
    }

    private ScoredCandidate candidate(WelfareService service,
                                      double ruleWeightedScore,
                                      Double aiScore,
                                      boolean hasInterestMismatch,
                                      boolean hasPriorityMismatch,
                                      Integer matchedPriorityRank) {
        return ScoredCandidate.builder()
                .service(service)
                .ruleBaseScore(ruleWeightedScore)
                .ruleWeightedScore(ruleWeightedScore)
                .aiScore(aiScore)
                .hasInterestMismatch(hasInterestMismatch)
                .hasPriorityMismatch(hasPriorityMismatch)
                .matchedPriorityRank(matchedPriorityRank)
                .build();
    }

    private ScoredCandidate candidate(WelfareService service, double ruleWeightedScore, Double aiScore) {
        return candidate(service, ruleWeightedScore, aiScore, false, false, null);
    }

    private WelfareService service(Long id,
                                   WelfareService.SourceType sourceType,
                                   String unifiedCategory,
                                   LocalDate applyEndDate,
                                   Integer viewCount,
                                   Long apiViewCount,
                                   LocalDateTime registeredAt) {
        return WelfareService.builder()
                .id(id)
                .sourceType(sourceType)
                .sourceId("S" + id)
                .title("service-" + id)
                .unifiedCategory(unifiedCategory)
                .status(WelfareService.ServiceStatus.ACTIVE)
                .applyEndDate(applyEndDate)
                .viewCount(viewCount)
                .apiViewCount(apiViewCount)
                .registeredAt(registeredAt)
                .build();
    }

    private WelfareService service(Long id,
                                   String unifiedCategory,
                                   LocalDate applyEndDate,
                                   Integer viewCount,
                                   Long apiViewCount,
                                   LocalDateTime registeredAt) {
        return service(id, WelfareService.SourceType.BOKJIRO_LOCAL, unifiedCategory, applyEndDate, viewCount, apiViewCount, registeredAt);
    }

    private WelfareService service(Long id,
                                   LocalDate applyEndDate,
                                   Integer viewCount,
                                   Long apiViewCount,
                                   LocalDateTime registeredAt) {
        return service(id, null, applyEndDate, viewCount, apiViewCount, registeredAt);
    }

    private RecommendationUserSnapshot noPrioritySnapshot(String userKey) {
        return new RecommendationUserSnapshot(
                1L,
                userKey,
                25,
                "20S",
                "서울",
                "강남구",
                "11680",
                (byte) 5,
                "ONE_PERSON",
                "EMPLOYED",
                10,
                0.5,
                List.of(),
                List.of(),
                List.of()
        );
    }
}
