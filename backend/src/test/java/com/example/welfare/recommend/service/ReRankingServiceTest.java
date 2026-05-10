package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.WelfareService;
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
        ReRankingService reRankingService = new ReRankingService(new ScoreNormalizer(), scoreWeightService);

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
        ReRankingService reRankingService = new ReRankingService(new ScoreNormalizer(), scoreWeightService);

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
        ReRankingService reRankingService = new ReRankingService(new ScoreNormalizer(), scoreWeightService);

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
                                   LocalDate applyEndDate,
                                   Integer viewCount,
                                   Long apiViewCount,
                                   LocalDateTime registeredAt) {
        return WelfareService.builder()
                .id(id)
                .sourceType(WelfareService.SourceType.BOKJIRO_LOCAL)
                .sourceId("S" + id)
                .title("service-" + id)
                .status(WelfareService.ServiceStatus.ACTIVE)
                .applyEndDate(applyEndDate)
                .viewCount(viewCount)
                .apiViewCount(apiViewCount)
                .registeredAt(registeredAt)
                .build();
    }
}
