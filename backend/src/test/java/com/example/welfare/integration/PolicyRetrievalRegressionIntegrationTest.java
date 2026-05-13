package com.example.welfare.integration;

import com.example.welfare.chat.service.PolicyChunkEmbeddingService;
import com.example.welfare.policy.dto.PolicyRetrievalEvaluationResponse;
import com.example.welfare.policy.dto.PolicyRetrievalQualityGateResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.policy.service.PolicyRetrievalEvaluationService;
import com.example.welfare.policy.service.PolicyRetrievalQualityGateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
class PolicyRetrievalRegressionIntegrationTest {

    private static final String TEST_SOURCE_PREFIX = "IT-RETR-EVAL-";
    private static final String BASELINE_SCENARIO_PREFIX = "retrieval-baseline-v2:";

    @Autowired
    private WelfareServiceRepository welfareServiceRepository;

    @Autowired
    private PolicyChunkEmbeddingService policyChunkEmbeddingService;

    @Autowired
    private PolicyRetrievalEvaluationService policyRetrievalEvaluationService;

    @Autowired
    private PolicyRetrievalQualityGateService policyRetrievalQualityGateService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    @DisplayName("seeded retrieval baseline은 integration 환경에서도 quality gate를 통과한다")
    void seededBaselinePassesQualityGate() {
        List<Long> serviceIds = List.of(
                saveService("housing-cash", "주거", "청년 월세 주거비 지원", "월세 부담과 주거비 현금 지원금을 제공합니다."),
                saveService("housing-stability", "주거", "청년 전세임대 공공임대 지원", "전세임대와 공공임대 중심의 장기 주거 안정을 지원합니다."),
                saveService("housing-subscription", "주거", "청년 청약 입주 모집 공고", "청약과 입주 모집 정보를 제공합니다."),
                saveService("job-employment", "일자리", "청년 인턴 채용 공고", "청년 채용과 인턴 기회를 연결합니다."),
                saveService("job-training", "교육·직업훈련", "청년 직업훈련 취업 교육", "직업훈련과 취업 교육 프로그램을 운영합니다."),
                saveService("job-startup", "일자리", "청년 창업 자금 지원", "청년 창업 사업 자금과 초기 운영비를 지원합니다."),
                saveService("finance-support", "금융·생활지원", "청년 생활비 금융 지원", "생활비 대출, 적금, 이자 부담 완화를 지원합니다."),
                saveService("culture-support", "문화·여가", "청년 문화 예술 활동비 지원", "문화 예술 관람과 동아리 활동비를 지원합니다."),
                saveService("health-support", "건강·의료", "청년 정신건강 상담 의료비 지원", "정신건강 상담과 치료, 의료비 부담 완화를 지원합니다.")
        );

        PolicyChunkEmbeddingService.EmbeddingRefreshResult embeddingRefreshResult =
                policyChunkEmbeddingService.refreshEmbeddingsForServiceIds(serviceIds);

        Integer embeddedChunkCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM policy_chunks
                WHERE service_id IN (%s)
                  AND embedding IS NOT NULL
                """.formatted(joinIds(serviceIds)), Integer.class);

        assertThat(embeddingRefreshResult.requestedServiceCount()).isEqualTo(serviceIds.size());
        assertThat(embeddedChunkCount).isNotNull();
        assertThat(embeddedChunkCount).isGreaterThan(0);

        PolicyRetrievalEvaluationResponse evaluation = policyRetrievalEvaluationService.evaluateBaseline();
        Map<String, PolicyRetrievalEvaluationResponse.ScenarioResult> scenarios = evaluation.scenarios().stream()
                .collect(Collectors.toMap(
                        PolicyRetrievalEvaluationResponse.ScenarioResult::scenarioKey,
                        Function.identity()
                ));

        assertThat(evaluation.datasetKey()).isEqualTo("retrieval-baseline-v2");
        assertThat(evaluation.scenarioCount()).isEqualTo(11);
        assertThat(evaluation.retrievalScenarioCount()).isEqualTo(9);
        assertThat(evaluation.branchScenarioCount()).isEqualTo(2);
        assertThat(evaluation.top1HitRate()).isEqualTo(1.0);
        assertThat(evaluation.top3HitRate()).isEqualTo(1.0);
        assertThat(evaluation.branchSuggestionHitRate()).isEqualTo(1.0);
        assertThat(evaluation.emptyResultCount()).isZero();
        assertThat(scenarios.get("branch-housing").branchSuggestionHit()).isTrue();
        assertThat(scenarios.get("branch-job").branchSuggestionHit()).isTrue();
        assertThat(scenarios.get("finance-support").top1Hit()).isTrue();
        assertThat(scenarios.get("culture-support").top1Hit()).isTrue();
        assertThat(scenarios.get("health-support").top1Hit()).isTrue();

        Integer snapshotCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM chat_retrieval_snapshots
                WHERE snapshot_type = 'EVALUATION'
                  AND scenario_key LIKE ?
                """, Integer.class, BASELINE_SCENARIO_PREFIX + "%");
        assertThat(snapshotCount).isEqualTo(11);

        PolicyRetrievalQualityGateResponse gate = policyRetrievalQualityGateService.evaluateGate();
        assertThat(gate.passed()).isTrue();
        assertThat(gate.failureReasons()).isEmpty();
        assertThat(gate.actualMetrics().top1HitRate()).isEqualTo(1.0);
        assertThat(gate.actualMetrics().top3HitRate()).isEqualTo(1.0);
        assertThat(gate.actualMetrics().branchSuggestionHitRate()).isEqualTo(1.0);
        assertThat(gate.actualMetrics().emptyResultCount()).isZero();
    }

    private Long saveService(String label,
                             String unifiedCategory,
                             String title,
                             String description) {
        return welfareServiceRepository.saveAndFlush(WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(TEST_SOURCE_PREFIX + label + "-" + UUID.randomUUID().toString().substring(0, 8))
                .title(title)
                .description(description)
                .supportContent(description)
                .keyword(title + " " + description)
                .unifiedCategory(unifiedCategory)
                .status(WelfareService.ServiceStatus.ACTIVE)
                .searchYouthRelevant(true)
                .apiViewCount(100L)
                .viewCount(10)
                .build()).getId();
    }

    private void cleanup() {
        jdbcTemplate.update("""
                DELETE FROM chat_retrieval_snapshots
                WHERE snapshot_type = 'EVALUATION'
                  AND scenario_key LIKE ?
                """, BASELINE_SCENARIO_PREFIX + "%");

        List<Long> serviceIds = welfareServiceRepository.findAll().stream()
                .filter(service -> service.getSourceId() != null && service.getSourceId().startsWith(TEST_SOURCE_PREFIX))
                .map(WelfareService::getId)
                .toList();
        if (serviceIds.isEmpty()) {
            return;
        }

        jdbcTemplate.update("""
                DELETE FROM policy_chunks
                WHERE service_id IN (%s)
                """.formatted(joinIds(serviceIds)));
        welfareServiceRepository.deleteAllByIdInBatch(serviceIds);
        welfareServiceRepository.flush();
    }

    private String joinIds(List<Long> ids) {
        return ids.stream()
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElseThrow();
    }
}
