package com.example.welfare.recommend.service;

import com.example.welfare.recommend.entity.ScoreWeight;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ScoreWeightServiceTest {

    @Mock
    private ScoreWeightProgressReadService scoreWeightProgressReadService;

    @InjectMocks
    private ScoreWeightService scoreWeightService;

    @Test
    @DisplayName("추천 로그가 100건 미만이면 COLD_START 가중치를 선택한다")
    void getActiveWeightReturnsColdStart() {
        given(scoreWeightProgressReadService.loadSnapshot())
                .willReturn(new ScoreWeightProgressReadService.ScoreWeightProgressSnapshot(99L, defaultWeights()));

        ScoreWeight weight = scoreWeightService.getActiveWeight();

        assertThat(weight.getWeightKey()).isEqualTo("COLD_START");
        assertThat(weight.getRuleWeight()).isEqualByComparingTo("0.8");
        assertThat(weight.getAiWeight()).isEqualByComparingTo("0.2");
    }

    @Test
    @DisplayName("추천 로그가 100건 이상 500건 미만이면 GROWTH 가중치를 선택한다")
    void getActiveWeightReturnsGrowth() {
        given(scoreWeightProgressReadService.loadSnapshot())
                .willReturn(new ScoreWeightProgressReadService.ScoreWeightProgressSnapshot(100L, defaultWeights()));

        ScoreWeight weight = scoreWeightService.getActiveWeight();

        assertThat(weight.getWeightKey()).isEqualTo("GROWTH");
        assertThat(weight.getRuleWeight()).isEqualByComparingTo("0.6");
        assertThat(weight.getAiWeight()).isEqualByComparingTo("0.4");
    }

    @Test
    @DisplayName("추천 로그가 500건 이상이면 STABLE 가중치를 선택한다")
    void getActiveWeightReturnsStable() {
        given(scoreWeightProgressReadService.loadSnapshot())
                .willReturn(new ScoreWeightProgressReadService.ScoreWeightProgressSnapshot(500L, defaultWeights()));

        ScoreWeight weight = scoreWeightService.getActiveWeight();

        assertThat(weight.getWeightKey()).isEqualTo("STABLE");
        assertThat(weight.getRuleWeight()).isEqualByComparingTo("0.4");
        assertThat(weight.getAiWeight()).isEqualByComparingTo("0.6");
    }

    private List<ScoreWeight> defaultWeights() {
        return List.of(
                weight("COLD_START", "0.8", "0.2", 0),
                weight("GROWTH", "0.6", "0.4", 100),
                weight("STABLE", "0.4", "0.6", 500)
        );
    }

    private ScoreWeight weight(String key, String ruleWeight, String aiWeight, int minLogCount) {
        return ScoreWeight.builder()
                .weightKey(key)
                .ruleWeight(new BigDecimal(ruleWeight))
                .aiWeight(new BigDecimal(aiWeight))
                .minLogCount(minLogCount)
                .isActive(true)
                .build();
    }
}
