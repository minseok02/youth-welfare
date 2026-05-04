package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.RecommendationLog;
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
class RecommendationLogReadRepositoryImplTest {

    @Mock
    private RecommendationLogRepository recommendationLogRepository;

    @InjectMocks
    private RecommendationLogReadRepositoryImpl recommendationLogReadRepository;

    @Test
    @DisplayName("recommendation log read repository는 전체 로그 수 조회를 위임한다")
    void countAllDelegates() {
        given(recommendationLogRepository.count()).willReturn(123L);

        assertThat(recommendationLogReadRepository.countAll()).isEqualTo(123L);
    }

    @Test
    @DisplayName("recommendation log read repository는 사용자 최신 로그 조회를 위임한다")
    void findLatestByUserKeyAndServiceIdsDelegates() {
        RecommendationLog log = RecommendationLog.builder().id(1L).build();
        given(recommendationLogRepository.findLatestByUserKeyAndServiceIds("user-key-1", List.of(10L)))
                .willReturn(List.of(log));

        assertThat(recommendationLogReadRepository.findLatestByUserKeyAndServiceIds("user-key-1", List.of(10L)))
                .containsExactly(log);
    }
}
