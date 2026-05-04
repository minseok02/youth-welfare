package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.ClusterAiResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ClusterAiResultReadRepositoryImplTest {

    @Mock
    private ClusterAiResultRepository clusterAiResultRepository;

    @InjectMocks
    private ClusterAiResultReadRepositoryImpl clusterAiResultReadRepository;

    @Test
    @DisplayName("cluster ai result read repository는 clusterId 기준 조회를 위임한다")
    void findByClusterIdDelegates() {
        ClusterAiResult result = ClusterAiResult.builder().clusterId("cluster-a").build();
        given(clusterAiResultRepository.findByClusterId("cluster-a")).willReturn(List.of(result));

        assertThat(clusterAiResultReadRepository.findByClusterId("cluster-a")).containsExactly(result);
    }
}
