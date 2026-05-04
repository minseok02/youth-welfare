package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.ScoreWeight;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ScoreWeightReadRepositoryImplTest {

    @Mock
    private ScoreWeightRepository scoreWeightRepository;

    @InjectMocks
    private ScoreWeightReadRepositoryImpl scoreWeightReadRepository;

    @Test
    @DisplayName("score weight read repository는 활성 가중치 목록 조회를 위임한다")
    void findConfiguredActiveWeightsDelegates() {
        ScoreWeight weight = ScoreWeight.builder().weightKey("COLD_START").build();
        given(scoreWeightRepository.findByIsActiveTrueOrderByMinLogCountAsc()).willReturn(List.of(weight));

        assertThat(scoreWeightReadRepository.findConfiguredActiveWeights()).containsExactly(weight);
        then(scoreWeightRepository).should().findByIsActiveTrueOrderByMinLogCountAsc();
    }
}
