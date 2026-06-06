package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.entity.ClusterAiResult;
import com.example.welfare.recommend.repository.ClusterAiResultCommandRepository;
import com.example.welfare.recommend.repository.ClusterAiResultReadRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class JpaClusterAiScoreCacheTest {

    @Mock
    private ClusterAiResultReadRepository clusterAiResultReadRepository;

    @Mock
    private ClusterAiResultCommandRepository clusterAiResultCommandRepository;

    @InjectMocks
    private JpaClusterAiScoreCache jpaClusterAiScoreCache;

    @Test
    @DisplayName("cluster cache는 read repository 결과를 serviceId 기준 score map으로 변환한다")
    void findByClusterIdBuildsScoreMap() {
        WelfareService service = WelfareService.builder().id(1L).build();
        ClusterAiResult result = ClusterAiResult.builder()
                .clusterId("cluster-a")
                .service(service)
                .aiScore(BigDecimal.valueOf(81.5))
                .aiReason("  지역\n청년\t주거 지원 조건과 지역 조건이 잘 맞아요  ")
                .build();
        given(clusterAiResultReadRepository.findByClusterId("cluster-a")).willReturn(List.of(result));

        Map<Long, ClusterAiScoreCache.CachedClusterAiScore> cacheMap = jpaClusterAiScoreCache.findByClusterId("cluster-a");

        assertThat(cacheMap).containsKey(1L);
        assertThat(cacheMap.get(1L).aiScore()).isEqualByComparingTo("81.5");
        assertThat(cacheMap.get(1L).aiReason()).isEqualTo("지역 청년 주거 지원 조건과 지역 조");
    }

    @Test
    @DisplayName("cluster cache는 없는 service 결과만 command repository로 저장한다")
    void saveAllPersistsOnlyNewRows() {
        WelfareService existingService = WelfareService.builder().id(1L).build();
        ClusterAiResult existing = ClusterAiResult.builder()
                .clusterId("cluster-a")
                .service(existingService)
                .aiScore(BigDecimal.valueOf(70))
                .aiReason("old")
                .build();
        WelfareService newService = WelfareService.builder().id(2L).build();
        given(clusterAiResultReadRepository.findByClusterId("cluster-a")).willReturn(List.of(existing));

        jpaClusterAiScoreCache.saveAll("cluster-a", List.of(
                new ClusterAiScoreCache.ClusterAiScoreWrite(existingService, BigDecimal.valueOf(90), "  \n  "),
                new ClusterAiScoreCache.ClusterAiScoreWrite(newService, BigDecimal.valueOf(88), "  지역\n청년\t주거 지원 조건과 지역 조건이 잘 맞아요  ")
        ));

        assertThat(existing.getAiScore()).isEqualByComparingTo("90");
        assertThat(existing.getAiReason()).isNull();
        ArgumentCaptor<ClusterAiResult> captor = ArgumentCaptor.forClass(ClusterAiResult.class);
        then(clusterAiResultCommandRepository).should().save(captor.capture());
        assertThat(captor.getValue().getClusterId()).isEqualTo("cluster-a");
        assertThat(captor.getValue().getService().getId()).isEqualTo(2L);
        assertThat(captor.getValue().getAiReason()).isEqualTo("지역 청년 주거 지원 조건과 지역 조");
    }
}
