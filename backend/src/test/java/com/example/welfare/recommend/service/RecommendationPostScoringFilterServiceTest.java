package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.ScoredCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationPostScoringFilterServiceTest {

    private final RecommendationPostScoringFilterService service = new RecommendationPostScoringFilterService();

    @Test
    @DisplayName("특수 대상 mismatch 후보는 facade 밖의 post-scoring filter 에서 제거한다")
    void filterSpecialTargetMismatches() {
        ScoredCandidate keep = candidate(1L, false);
        ScoredCandidate drop = candidate(2L, true);

        List<ScoredCandidate> filtered = service.filterSpecialTargetMismatches(List.of(keep, drop));

        assertThat(filtered).containsExactly(keep);
    }

    private ScoredCandidate candidate(Long serviceId, boolean mismatch) {
        WelfareService serviceEntity = WelfareService.builder()
                .id(serviceId)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("SRC-" + serviceId)
                .title("정책" + serviceId)
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
        return ScoredCandidate.builder()
                .service(serviceEntity)
                .ruleBaseScore(10.0)
                .ruleWeightedScore(10.0)
                .hasSpecialTargetMismatch(mismatch)
                .build();
    }
}
