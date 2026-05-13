package com.example.welfare.policy.service;

import com.example.welfare.chat.config.ChatRetrievalProperties;
import com.example.welfare.chat.dto.ChatPolicyCandidate;
import com.example.welfare.chat.service.ChatBranchCatalog;
import com.example.welfare.chat.service.ChatPolicyService;
import com.example.welfare.chat.service.ChatRetrievalSnapshotService;
import com.example.welfare.policy.dto.PolicyRetrievalEvaluationCompareRequest;
import com.example.welfare.policy.dto.PolicyRetrievalEvaluationCompareResponse;
import com.example.welfare.policy.dto.PolicyRetrievalEvaluationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyRetrievalEvaluationServiceTest {

    @Mock
    private ChatPolicyService chatPolicyService;
    @Mock
    private ChatRetrievalSnapshotService chatRetrievalSnapshotService;

    private PolicyRetrievalEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new PolicyRetrievalEvaluationService(
                chatPolicyService,
                new ChatBranchCatalog(),
                chatRetrievalSnapshotService,
                new ChatRetrievalProperties(3, 2, 3, 3)
        );

        when(chatPolicyService.traceCandidates(anyString(), any(), eq(5), any()))
                .thenAnswer(invocation -> traceFor(
                        invocation.getArgument(0, String.class),
                        invocation.getArgument(1, String.class),
                        invocation.getArgument(3, ChatRetrievalProperties.class)
                ));
    }

    @Test
    @DisplayName("retrieval baseline 평가는 고정 시나리오를 집계하고 snapshot 을 남긴다")
    void evaluateBaselineAggregatesScenarioMetrics() {
        PolicyRetrievalEvaluationResponse response = service.evaluateBaseline();

        assertThat(response.datasetKey()).isEqualTo("retrieval-baseline-v2");
        assertThat(response.scenarioCount()).isEqualTo(11);
        assertThat(response.retrievalScenarioCount()).isEqualTo(9);
        assertThat(response.branchScenarioCount()).isEqualTo(2);
        assertThat(response.top1HitCount()).isEqualTo(9);
        assertThat(response.top3HitCount()).isEqualTo(9);
        assertThat(response.branchSuggestionHitCount()).isEqualTo(2);
        assertThat(response.fallbackCount()).isEqualTo(2);
        assertThat(response.semanticContributionCount()).isEqualTo(2);
        assertThat(response.top1HitRate()).isEqualTo(1.0);
        assertThat(response.top3HitRate()).isEqualTo(1.0);
        assertThat(response.branchSuggestionHitRate()).isEqualTo(1.0);
        assertThat(response.fallbackRate()).isCloseTo(2.0 / 9.0, within(0.0001));
        assertThat(response.semanticContributionRate()).isCloseTo(2.0 / 9.0, within(0.0001));
        assertThat(response.averageResultCount()).isCloseTo(12.0 / 9.0, within(0.0001));
        assertThat(response.averageFtsResultCount()).isCloseTo(7.0 / 9.0, within(0.0001));
        assertThat(response.averageSemanticResultCount()).isCloseTo(2.0 / 9.0, within(0.0001));
        assertThat(response.averageSemanticOnlyResultCount()).isCloseTo(2.0 / 9.0, within(0.0001));
        assertThat(response.averageTop3CategoryConcentration()).isEqualTo(1.0);
        assertThat(response.averageBranchReductionCount()).isEqualTo(1.0);
        assertThat(response.scenarios()).hasSize(11);

        verify(chatRetrievalSnapshotService, times(11))
                .recordEvaluationTrace(anyString(), anyString(), any(List.class), any(ChatPolicyService.CandidateTrace.class));
    }

    @Test
    @DisplayName("retrieval compare 는 baseline 과 candidate tuning 결과를 함께 반환한다")
    void compareBaselineReturnsDelta() {
        PolicyRetrievalEvaluationCompareResponse response = service.compareBaseline(
                new PolicyRetrievalEvaluationCompareRequest(1, 1, 1, 2)
        );

        assertThat(response.baseline().datasetKey()).isEqualTo("retrieval-baseline-v2");
        assertThat(response.candidate().datasetKey()).contains("retrieval-compare-v2-min1-blend1-sem1-terms2");
        assertThat(response.baselineTuning().minResultCount()).isEqualTo(3);
        assertThat(response.candidateTuning().semanticBlendLimit()).isEqualTo(1);
        assertThat(response.delta().fallbackCountDelta()).isLessThanOrEqualTo(0);
    }

    private ChatPolicyService.CandidateTrace traceFor(String question,
                                                     String branchKey,
                                                     ChatRetrievalProperties tuning) {
        boolean candidateMode = tuning != null
                && (tuning.minResultCount() == 1
                || tuning.semanticBlendLimit() == 1
                || tuning.semanticOnlyLimit() == 1
                || tuning.maxPreferredTermsInSearchKeyword() == 2);
        if ("월세 지원 받을 수 있나요?".equals(question) && branchKey == null) {
            return trace(
                    question,
                    null,
                    "MERGED_RESULTS",
                    List.of(candidate(1L, "주거"), candidate(2L, "주거"), candidate(3L, "금융·생활지원")),
                    List.of(candidate(2L, "주거")),
                    List.of(candidate(1L, "주거"), candidate(2L, "주거"), candidate(3L, "금융·생활지원"))
            );
        }
        if ("월세 지원 받을 수 있나요?".equals(question)) {
            return trace(
                    question,
                    branchKey,
                    "MERGED_RESULTS",
                    List.of(candidate(1L, "주거")),
                    candidateMode ? List.of() : List.of(candidate(2L, "주거")),
                    candidateMode ? List.of(candidate(1L, "주거")) : List.of(candidate(1L, "주거"), candidate(2L, "주거"))
            );
        }
        if ("전세임대나 공공임대 정보를 보고 싶어요".equals(question) && branchKey == null) {
            return trace(question, null, "MERGED_RESULTS", List.of(candidate(10L, "주거"), candidate(11L, "주거")), List.of(), List.of(candidate(10L, "주거"), candidate(11L, "주거")));
        }
        if ("전세임대나 공공임대 정보를 보고 싶어요".equals(question)) {
            return trace(question, branchKey, "MERGED_RESULTS", List.of(candidate(10L, "주거")), List.of(), List.of(candidate(10L, "주거")));
        }
        if ("청약이나 입주 모집 공고를 보고 싶어요".equals(question) && branchKey == null) {
            return trace(question, null, "MERGED_RESULTS", List.of(candidate(12L, "주거"), candidate(13L, "주거")), List.of(), List.of(candidate(12L, "주거"), candidate(13L, "주거")));
        }
        if ("청약이나 입주 모집 공고를 보고 싶어요".equals(question)) {
            return trace(question, branchKey, "MERGED_RESULTS", List.of(candidate(12L, "주거")), List.of(), List.of(candidate(12L, "주거")));
        }
        if ("청년 인턴이나 채용 공고가 궁금해요".equals(question) && branchKey == null) {
            return trace(question, null, "MERGED_RESULTS", List.of(candidate(20L, "일자리"), candidate(21L, "일자리")), List.of(), List.of(candidate(20L, "일자리"), candidate(21L, "일자리")));
        }
        if ("청년 인턴이나 채용 공고가 궁금해요".equals(question)) {
            return trace(question, branchKey, "MERGED_RESULTS", List.of(candidate(20L, "일자리")), List.of(), List.of(candidate(20L, "일자리")));
        }
        if ("직업훈련이나 취업 교육을 찾고 있어요".equals(question) && branchKey == null) {
            return trace(question, null, "MERGED_RESULTS", List.of(candidate(30L, "교육·직업훈련"), candidate(31L, "교육·직업훈련")), List.of(), List.of(candidate(30L, "교육·직업훈련"), candidate(31L, "교육·직업훈련")));
        }
        if ("직업훈련이나 취업 교육을 찾고 있어요".equals(question)) {
            return trace(question, branchKey, "MERGED_RESULTS", List.of(candidate(30L, "교육·직업훈련")), List.of(), List.of(candidate(30L, "교육·직업훈련")));
        }
        if ("청년 창업 자금 지원이 있나요?".equals(question) && branchKey == null) {
            return trace(
                    question,
                    null,
                    candidateMode ? "MERGED_RESULTS" : "POPULAR_FALLBACK",
                    List.of(),
                    candidateMode ? List.of(candidate(40L, "일자리")) : List.of(),
                    candidateMode ? List.of(candidate(40L, "일자리")) : List.of(candidate(40L, "일자리"), candidate(41L, "금융·생활지원"))
            );
        }
        if ("청년 창업 자금 지원이 있나요?".equals(question)) {
            return trace(
                    question,
                    branchKey,
                    candidateMode ? "MERGED_RESULTS" : "CATEGORY_FALLBACK",
                    List.of(),
                    candidateMode ? List.of(candidate(40L, "일자리")) : List.of(),
                    List.of(candidate(40L, "일자리"))
            );
        }
        if ("청년 생활비나 금융 지원이 있나요?".equals(question)) {
            return trace(
                    question,
                    branchKey,
                    "MERGED_RESULTS",
                    List.of(candidate(50L, "금융·생활지원")),
                    List.of(candidate(51L, "금융·생활지원")),
                    List.of(candidate(50L, "금융·생활지원"), candidate(51L, "금융·생활지원"))
            );
        }
        if ("청년 문화 활동비 지원 있나요?".equals(question)) {
            return trace(
                    question,
                    branchKey,
                    "MERGED_RESULTS",
                    List.of(candidate(60L, "문화·여가")),
                    List.of(),
                    List.of(candidate(60L, "문화·여가"))
            );
        }
        if ("청년 정신건강 상담이나 의료비 지원이 있나요?".equals(question)) {
            return trace(
                    question,
                    branchKey,
                    "POPULAR_FALLBACK",
                    List.of(),
                    List.of(),
                    List.of(candidate(70L, "건강·의료"), candidate(71L, "건강·의료"))
            );
        }

        return trace(question, branchKey, "POPULAR_FALLBACK", List.of(), List.of(), List.of());
    }

    private ChatPolicyService.CandidateTrace trace(String question,
                                                   String branchKey,
                                                   String fallbackStrategy,
                                                   List<ChatPolicyCandidate> fts,
                                                   List<ChatPolicyCandidate> semantic,
                                                   List<ChatPolicyCandidate> merged) {
        return new ChatPolicyService.CandidateTrace(
                question,
                question,
                branchKey,
                branchKey == null ? null : merged.stream().findFirst().map(ChatPolicyCandidate::getUnifiedCategory).orElse(null),
                List.of(),
                fallbackStrategy,
                fts,
                semantic,
                merged
        );
    }

    private ChatPolicyCandidate candidate(Long serviceId, String category) {
        return ChatPolicyCandidate.builder()
                .serviceId(serviceId)
                .title("policy-" + serviceId)
                .unifiedCategory(category)
                .build();
    }
}
