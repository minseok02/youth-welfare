package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.ClusterAiResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ClusterAiResultCommandRepositoryImplTest {

    @Mock
    private ClusterAiResultRepository clusterAiResultRepository;

    @Mock
    private NamedParameterJdbcTemplate clusterAiCleanupNamedParameterJdbcTemplate;

    @InjectMocks
    private ClusterAiResultCommandRepositoryImpl clusterAiResultCommandRepository;

    @Test
    @DisplayName("cluster ai result command repository는 save를 위임한다")
    void saveDelegates() {
        ClusterAiResult result = ClusterAiResult.builder().clusterId("cluster-a").build();

        clusterAiResultCommandRepository.save(result);

        then(clusterAiResultRepository).should().save(result);
    }

    @Test
    @DisplayName("cluster ai result command repository는 만료 캐시 삭제를 cleanup jdbc로 위임한다")
    void deleteExpiredBeforeDelegates() {
        LocalDateTime before = LocalDateTime.of(2026, 5, 4, 0, 0);

        clusterAiResultCommandRepository.deleteExpiredBefore(before);

        ArgumentCaptor<SqlParameterSource> parameterCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);

        then(clusterAiCleanupNamedParameterJdbcTemplate).should().update(
                eq("""
                        DELETE FROM cluster_ai_results
                        WHERE created_at < :before
                        """),
                parameterCaptor.capture()
        );
        assertThat(parameterCaptor.getValue().getValue("before")).isEqualTo(before);
        then(clusterAiResultRepository).shouldHaveNoMoreInteractions();
    }
}
