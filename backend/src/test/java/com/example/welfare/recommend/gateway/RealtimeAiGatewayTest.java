package com.example.welfare.recommend.gateway;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.ScoredCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeAiGatewayTest {

    @Test
    void candidateIdsKeepsInputOrder() {
        List<ScoredCandidate> candidates = List.of(
                scoredCandidate(403L, 30.0),
                scoredCandidate(356L, 30.0),
                scoredCandidate(371L, 25.0)
        );

        assertThat(RealtimeAiGateway.candidateIds(candidates))
                .isEqualTo("403,356,371");
    }

    @Test
    void candidateRuleScoresIncludesServiceIdAndTwoDecimalRuleScore() {
        List<ScoredCandidate> candidates = List.of(
                scoredCandidate(403L, 30.0),
                scoredCandidate(371L, 25.0)
        );

        assertThat(RealtimeAiGateway.candidateRuleScores(candidates))
                .isEqualTo("403:30.00,371:25.00");
    }

    @Test
    void sha256HexIsStableForSamePrompt() {
        String prompt = "sample prompt for replay trace";

        assertThat(RealtimeAiGateway.sha256Hex(prompt))
                .isEqualTo(RealtimeAiGateway.sha256Hex(prompt))
                .hasSize(64);
    }

    private ScoredCandidate scoredCandidate(Long serviceId, double ruleWeightedScore) {
        WelfareService service = WelfareService.builder()
                .id(serviceId)
                .title("service-" + serviceId)
                .build();

        return ScoredCandidate.builder()
                .service(service)
                .ruleBaseScore(ruleWeightedScore)
                .ruleWeightedScore(ruleWeightedScore)
                .build();
    }
}
