package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.ClusterAiResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ClusterAiResultCommandRepositoryImplTest {

    @Mock
    private ClusterAiResultRepository clusterAiResultRepository;

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
    @DisplayName("cluster ai result command repository는 만료 캐시 삭제를 위임한다")
    void deleteExpiredBeforeDelegates() {
        LocalDateTime before = LocalDateTime.of(2026, 5, 4, 0, 0);

        clusterAiResultCommandRepository.deleteExpiredBefore(before);

        then(clusterAiResultRepository).should().deleteExpiredBefore(before);
    }
}
