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

    private ScoredCandidate candidate(WelfareService service, double ruleWeightedScore, Double aiScore) {
        ScoredCandidate candidate = ScoredCandidate.builder()
                .service(service)
                .ruleBaseScore(ruleWeightedScore)
                .ruleWeightedScore(ruleWeightedScore)
                .build();
        candidate.setAiScore(aiScore);
        return candidate;
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
