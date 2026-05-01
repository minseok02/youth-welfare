package com.example.welfare.recommend.gateway;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.dto.ScoredCandidate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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

    @Test
    void buildRequestBodyIncludesSeedOnlyWhenPresent() {
        Map<String, Object> withSeed = RealtimeAiGateway.buildRequestBody("prompt", 4242L, "gpt-4o-mini");
        Map<String, Object> withoutSeed = RealtimeAiGateway.buildRequestBody("prompt", null, "gpt-4o-mini");

        assertThat(withSeed)
                .containsEntry("model", "gpt-4o-mini")
                .containsEntry("seed", 4242L)
                .containsEntry("temperature", 0.3);
        assertThat(withoutSeed)
                .containsEntry("model", "gpt-4o-mini")
                .doesNotContainKey("seed");
    }

    @Test
    void parseAiCallResultCapturesResponseMetadata() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String responseBody = """
                {
                  "id": "chatcmpl-test-123",
                  "system_fingerprint": "fp_test_abc",
                  "seed": 4242,
                  "choices": [
                    {
                      "message": {
                        "content": "{\\"results\\":[{\\"service_id\\":403,\\"score\\":91,\\"reason\\":\\"sample\\"}]}"
                      }
                    }
                  ]
                }
                """;

        RealtimeAiGateway.AiCallResult result = RealtimeAiGateway.parseAiCallResult(objectMapper, responseBody);

        assertThat(result.responseId()).isEqualTo("chatcmpl-test-123");
        assertThat(result.systemFingerprint()).isEqualTo("fp_test_abc");
        assertThat(result.responseSeed()).isEqualTo(4242L);
        assertThat(result.aiResponse()).isNotNull();
        assertThat(result.aiResponse().getResults()).hasSize(1);
        assertThat(result.aiResponse().getResults().get(0).getServiceId()).isEqualTo(403L);
    }

    @Test
    void resolveUnifiedCategoryUsesProjectionCompatBeforeEntityField() {
        ScoredCandidate candidate = ScoredCandidate.builder()
                .service(WelfareService.builder()
                        .id(1L)
                        .title("service-1")
                        .unifiedCategory("기타")
                        .build())
                .projection(RecommendationCandidateProjection.builder()
                        .serviceId(1L)
                        .unifiedCategoryCompat("주거")
                        .build())
                .ruleBaseScore(10.0)
                .ruleWeightedScore(10.0)
                .build();

        assertThat(RealtimeAiGateway.resolveUnifiedCategory(candidate)).isEqualTo("주거");
    }

    @Test
    void buildPromptPolicyLineIncludesCanonicalSummaryFieldsWhenPresent() {
        ScoredCandidate candidate = ScoredCandidate.builder()
                .service(WelfareService.builder()
                        .id(1L)
                        .title("청년 정책")
                        .unifiedCategory("기타")
                        .build())
                .projection(RecommendationCandidateProjection.builder()
                        .serviceId(1L)
                        .unifiedCategoryCompat("주거")
                        .youthMajorLabel("주거")
                        .youthMidLabel("전월세 및 주거급여 지원")
                        .provisionMethodLabel("온라인")
                        .build())
                .ruleBaseScore(10.0)
                .ruleWeightedScore(10.0)
                .build();

        String line = RealtimeAiGateway.buildPromptPolicyLine(candidate, "설명");

        assertThat(line)
                .contains("분류:주거")
                .contains("정책분야:주거")
                .contains("세부분야:전월세 및 주거급여 지원")
                .contains("제공방식:온라인")
                .contains("내용:설명");
    }

    @Test
    void buildPromptPolicyLineSkipsBlankCanonicalSummaryFields() {
        ScoredCandidate candidate = ScoredCandidate.builder()
                .service(WelfareService.builder()
                        .id(2L)
                        .title("청년 정책")
                        .unifiedCategory("교육")
                        .build())
                .projection(RecommendationCandidateProjection.builder()
                        .serviceId(2L)
                        .unifiedCategoryCompat("교육")
                        .youthMajorLabel("")
                        .youthMidLabel(null)
                        .provisionMethodLabel(" ")
                        .build())
                .ruleBaseScore(10.0)
                .ruleWeightedScore(10.0)
                .build();

        String line = RealtimeAiGateway.buildPromptPolicyLine(candidate, "설명");

        assertThat(line)
                .contains("분류:교육")
                .doesNotContain("정책분야:")
                .doesNotContain("세부분야:")
                .doesNotContain("제공방식:");
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
