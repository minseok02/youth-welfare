package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.RecommendationLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RecommendationLogCommandRepositoryImplTest {

    @Mock
    private RecommendationLogRepository recommendationLogRepository;

    @InjectMocks
    private RecommendationLogCommandRepositoryImpl recommendationLogCommandRepository;

    @Test
    @DisplayName("recommendation log command repository는 미클릭 로그 삭제를 위임한다")
    void deleteUnclickedByUserKeyDelegates() {
        recommendationLogCommandRepository.deleteUnclickedByUserKey("user-key-1");

        then(recommendationLogRepository).should().deleteUnclickedByUserKey("user-key-1");
    }

    @Test
    @DisplayName("recommendation log command repository는 로그 저장을 위임한다")
    void saveAllDelegates() {
        RecommendationLog log = RecommendationLog.builder().id(1L).build();

        recommendationLogCommandRepository.saveAll(List.of(log));

        then(recommendationLogRepository).should().saveAll(List.of(log));
    }

    @Test
    @DisplayName("recommendation log command repository는 단건 로그 조회를 위임한다")
    void findByIdDelegates() {
        RecommendationLog log = RecommendationLog.builder().id(1L).build();
        given(recommendationLogRepository.findById(1L)).willReturn(Optional.of(log));

        assertThat(recommendationLogCommandRepository.findById(1L)).contains(log);
    }
}
