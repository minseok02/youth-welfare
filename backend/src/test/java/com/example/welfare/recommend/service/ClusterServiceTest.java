package com.example.welfare.recommend.service;

import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClusterServiceTest {

    private final ClusterService clusterService = new ClusterService();

    @Test
    @DisplayName("1차 운영에서는 사용자 속성과 무관하게 youth_all 단일 군집을 반환한다")
    void assignClusterAlwaysReturnsYouthAll() {
        RecommendationUserSnapshot youngLowIncome = snapshot(22, (byte) 2);
        RecommendationUserSnapshot seniorHighIncome = snapshot(33, (byte) 9);
        RecommendationUserSnapshot missingProfile = snapshot(null, null);

        assertThat(clusterService.assignCluster(youngLowIncome)).isEqualTo("youth_all");
        assertThat(clusterService.assignCluster(seniorHighIncome)).isEqualTo("youth_all");
        assertThat(clusterService.assignCluster(missingProfile)).isEqualTo("youth_all");
    }

    private RecommendationUserSnapshot snapshot(Integer age, Byte incomeLevel) {
        return new RecommendationUserSnapshot(
                1L,
                "user-key",
                age,
                "20대",
                "서울특별시",
                "관악구",
                "11620",
                incomeLevel,
                "1인가구",
                "미취업",
                10,
                0.7,
                List.of(),
                List.of(),
                List.of()
        );
    }
}
