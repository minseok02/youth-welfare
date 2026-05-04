package com.example.welfare.recommend.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.recommend.repository.RecommendationLogReadRepository;
import com.example.welfare.recommend.repository.ScoreWeightReadRepository;
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
class ScoreWeightProgressReadServiceTest {

    @Mock
    private RecommendationLogReadRepository recommendationLogReadRepository;

    @Mock
    private ScoreWeightReadRepository scoreWeightReadRepository;

    @InjectMocks
    private ScoreWeightProgressReadService scoreWeightProgressReadService;

    @Test
    @DisplayName("score weight progress read service는 로그 수와 활성 가중치 목록을 함께 로드한다")
    void loadSnapshotReturnsCountAndWeights() {
        List<ScoreWeight> weights = List.of(weight("COLD_START", "0.8", "0.2", 0));
        given(recommendationLogReadRepository.countAll()).willReturn(42L);
        given(scoreWeightReadRepository.findConfiguredActiveWeights()).willReturn(weights);

        ScoreWeightProgressReadService.ScoreWeightProgressSnapshot snapshot =
                scoreWeightProgressReadService.loadSnapshot();

        assertThat(snapshot.totalLogCount()).isEqualTo(42L);
        assertThat(snapshot.activeWeights()).containsExactlyElementsOf(weights);
    }

    @Test
    @DisplayName("score weight progress read service는 활성 가중치 설정이 없으면 예외를 던진다")
    void loadSnapshotThrowsWhenNoWeights() {
        given(recommendationLogReadRepository.countAll()).willReturn(0L);
        given(scoreWeightReadRepository.findConfiguredActiveWeights()).willReturn(List.of());

        assertThatThrownBy(() -> scoreWeightProgressReadService.loadSnapshot())
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCORE_WEIGHT_NOT_CONFIGURED);
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
