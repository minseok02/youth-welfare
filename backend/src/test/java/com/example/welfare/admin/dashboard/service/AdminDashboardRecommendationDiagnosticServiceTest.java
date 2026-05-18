package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminRecommendationCandidateDiagnosticResponse;
import com.example.welfare.collect.support.Gov24LabelTokenSupport;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.recommend.dto.RetrievedRecommendationCandidates;
import com.example.welfare.recommend.dto.ScoredCandidate;
import com.example.welfare.recommend.entity.AiScoreStatus;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.service.ClusterService;
import com.example.welfare.recommend.service.RecommendationPostScoringFilterService;
import com.example.welfare.recommend.service.RecommendationResultReadService;
import com.example.welfare.recommend.service.ReRankingService;
import com.example.welfare.recommend.service.RetrievalService;
import com.example.welfare.recommend.service.RuleScoringService;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.service.UserRecommendationReadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardRecommendationDiagnosticServiceTest {

    @Mock
    private UserRecommendationReadService userRecommendationReadService;
    @Mock
    private ClusterService clusterService;
    @Mock
    private RetrievalService retrievalService;
    @Mock
    private RuleScoringService ruleScoringService;
    @Mock
    private RecommendationPostScoringFilterService recommendationPostScoringFilterService;
    @Mock
    private ReRankingService reRankingService;
    @Mock
    private RecommendationResultReadService recommendationResultReadService;
    @Mock
    private WelfareServiceRepository welfareServiceRepository;

    @Test
    @DisplayName("진단 응답은 서비스별 드롭 단계를 구분한다")
    void getRecommendationDiagnosticsClassifiesDropStages() {
        AdminDashboardRecommendationDiagnosticService service = new AdminDashboardRecommendationDiagnosticService(
                userRecommendationReadService,
                clusterService,
                retrievalService,
                ruleScoringService,
                recommendationPostScoringFilterService,
                reRankingService,
                recommendationResultReadService,
                welfareServiceRepository
        );

        User user = User.builder()
                .id(1L)
                .userKey("user-key-1")
                .email("ops@youthmoa.kr")
                .accountOrigin(User.AccountOrigin.REAL_USER)
                .passwordHash("encoded")
                .isActive(true)
                .build();
        RecommendationUserSnapshot snapshot = new RecommendationUserSnapshot(
                1L,
                "user-key-1",
                25,
                "25_29",
                "인천광역시",
                "중구",
                "28110",
                (byte) 5,
                "1인 가구",
                "미취업",
                10,
                0.5,
                List.of(),
                List.of(),
                List.of()
        );

        WelfareService leader = welfareService(3686L, "드림나래");
        WelfareService droppedAfterScoring = welfareService(2736L, "동구 청년 컬처페이 지원사업");
        WelfareService filteredByPrimaryAudience = welfareService(3714L, "(인천형)발달장애인 주간활동서비스 추가지원");
        WelfareService filteredByAge = welfareService(8888L, "청소년 전용 정책");
        WelfareService notRetrieved = welfareService(9999L, "미조회 정책");

        RetrievalService.RecommendationRetrievalTrace trace = new RetrievalService.RecommendationRetrievalTrace(
                List.of(leader, droppedAfterScoring, filteredByPrimaryAudience, filteredByAge),
                List.of(),
                List.of(leader, droppedAfterScoring),
                List.of(),
                List.of(leader, droppedAfterScoring),
                Map.of(
                        3686L, projection(3686L, true, "개인", "현금"),
                        2736L, projection(2736L, true, "개인||가구", "현금||서비스(의료)||현금"),
                        3714L, projection(3714L, false, "개인", "서비스(돌봄)")
                ),
                Map.of(
                        3686L, new RetrievalService.CandidateFilterTrace(true, true),
                        2736L, new RetrievalService.CandidateFilterTrace(true, true),
                        3714L, new RetrievalService.CandidateFilterTrace(false, true),
                        8888L, new RetrievalService.CandidateFilterTrace(true, false)
                )
        );

        ScoredCandidate leaderScored = scoredCandidate(leader, 23.0, 23.0, 90.0, false);
        ScoredCandidate droppedScored = scoredCandidate(droppedAfterScoring, 10.0, 10.0, null, false);

        UserRecommendation savedLeader = UserRecommendation.builder()
                .id(101L)
                .userKey("user-key-1")
                .service(leader)
                .recommendedAt(LocalDateTime.of(2026, 5, 17, 21, 0))
                .finalScore(BigDecimal.valueOf(1.025))
                .build();

        when(userRecommendationReadService.getRecommendationContextByUserKey("user-key-1"))
                .thenReturn(new UserRecommendationReadService.RecommendationReadContext(user, snapshot));
        when(clusterService.assignCluster(snapshot)).thenReturn("youth_all");
        when(retrievalService.trace(snapshot)).thenReturn(trace);
        when(ruleScoringService.score(any(RetrievedRecommendationCandidates.class), eq(snapshot)))
                .thenReturn(List.of(leaderScored, droppedScored));
        when(recommendationPostScoringFilterService.filterSpecialTargetMismatches(List.of(leaderScored, droppedScored)))
                .thenReturn(List.of(leaderScored, droppedScored));
        when(reRankingService.trace(List.of(leaderScored, droppedScored), snapshot))
                .thenReturn(new ReRankingService.RerankTrace(
                        List.of(leaderScored, droppedScored),
                        Map.of(
                                3686L, new ReRankingService.CandidateRerankTrace(
                                        1.0, null, 1.0, 0.0, 1.0, 1.0, 1.0,
                                        "JOB", 0.0, true, 0.085, 1.085, 1
                                ),
                                2736L, new ReRankingService.CandidateRerankTrace(
                                        0.43, null, 1.0, 0.0, 0.43, 1.0, 0.43,
                                        "JOB", 0.03, true, 0.03, 0.40, 8
                                )
                        )
                ));
        when(recommendationResultReadService.findLatestSavedRecommendations("user-key-1"))
                .thenReturn(List.of(savedLeader));
        when(welfareServiceRepository.findAllById(any()))
                .thenReturn(List.of(leader, droppedAfterScoring, filteredByPrimaryAudience, filteredByAge, notRetrieved));

        AdminRecommendationCandidateDiagnosticResponse response = service.getRecommendationDiagnostics(
                "user-key-1",
                List.of(3686L, 2736L, 3714L, 8888L, 9999L)
        );

        assertThat(response.userKey()).isEqualTo("user-key-1");
        assertThat(response.accountOrigin()).isEqualTo("REAL_USER");
        assertThat(response.clusterId()).isEqualTo("youth_all");
        assertThat(response.rerankTraceMode()).isEqualTo("PRE_AI_POST_SCORING");
        assertThat(response.services()).hasSize(5);

        assertThat(response.services()).filteredOn(row -> row.serviceId().equals(3686L)).singleElement()
                .satisfies(row -> {
                    assertThat(row.inLatestSavedBatch()).isTrue();
                    assertThat(row.dropStage()).isEqualTo("PRESENT_IN_SAVED_BATCH");
                    assertThat(row.latestSavedRank()).isEqualTo(1);
                    assertThat(row.latestSavedAiStatus()).isEqualTo("NOT_REQUESTED");
                    assertThat(row.rerankCurrentRank()).isEqualTo(1);
                    assertThat(row.rerankNoPriorityAdjustment()).isEqualTo(0.085);
                });

        assertThat(response.services()).filteredOn(row -> row.serviceId().equals(2736L)).singleElement()
                .satisfies(row -> {
                    assertThat(row.inMergedCandidates()).isTrue();
                    assertThat(row.inLatestSavedBatch()).isFalse();
                    assertThat(row.dropStage()).isEqualTo("SCORED_BUT_NOT_IN_SAVED_BATCH");
                    assertThat(row.gov24UserTypeTokens()).containsExactly("개인", "가구");
                    assertThat(row.gov24BenefitTypeTokens()).containsExactly("현금", "서비스(의료)");
                    assertThat(row.youthEmploymentRequirementCodes()).containsExactly("0013003", "0013006");
                    assertThat(row.youthEmploymentRequirementLabels()).containsExactly("미취업자", "(예비)창업자");
                    assertThat(row.youthSpecialRequirementCodes()).containsExactly("0014003", "0014008");
                    assertThat(row.youthSpecialRequirementLabels()).containsExactly("기초생활수급자", "지역인재");
                    assertThat(row.youthIncomeConditionTypeCode()).isEqualTo("0043002");
                    assertThat(row.youthIncomeConditionTypeLabel()).isEqualTo("연소득");
                    assertThat(row.rerankDiversityPenalty()).isEqualTo(0.03);
                    assertThat(row.rerankCurrentRank()).isEqualTo(8);
                });

        assertThat(response.services()).filteredOn(row -> row.serviceId().equals(3714L)).singleElement()
                .satisfies(row -> {
                    assertThat(row.inBaseRetrieval()).isTrue();
                    assertThat(row.passedBaseFilters()).isFalse();
                    assertThat(row.dropStage()).isEqualTo("FILTERED_BY_PRIMARY_AUDIENCE_RELEVANCE");
                });

        assertThat(response.services()).filteredOn(row -> row.serviceId().equals(8888L)).singleElement()
                .satisfies(row -> {
                    assertThat(row.inBaseRetrieval()).isTrue();
                    assertThat(row.passedBaseFilters()).isFalse();
                    assertThat(row.dropStage()).isEqualTo("FILTERED_BY_AGE_CONSTRAINT");
                });

        assertThat(response.services()).filteredOn(row -> row.serviceId().equals(9999L)).singleElement()
                .satisfies(row -> assertThat(row.dropStage()).isEqualTo("NOT_IN_SQL_RETRIEVAL"));
    }

    private WelfareService welfareService(Long id, String title) {
        return WelfareService.builder()
                .id(id)
                .sourceType(WelfareService.SourceType.BOKJIRO_LOCAL)
                .sourceId("SRC-" + id)
                .title(title)
                .unifiedCategory("기타")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
    }

    private RecommendationCandidateProjection projection(Long serviceId,
                                                         boolean youthRelevant,
                                                         String gov24UserTypeLabel,
                                                         String gov24BenefitTypeLabel) {
        return RecommendationCandidateProjection.builder()
                .serviceId(serviceId)
                .youthRelevant(youthRelevant)
                .gov24UserTypeLabel(gov24UserTypeLabel)
                .gov24BenefitTypeLabel(gov24BenefitTypeLabel)
                .gov24UserTypeTokens(Gov24LabelTokenSupport.userTypeTokens(gov24UserTypeLabel))
                .gov24BenefitTypeTokens(Gov24LabelTokenSupport.benefitTypeTokens(gov24BenefitTypeLabel))
                .youthEmploymentRequirementCodes(List.of("0013003", "0013006"))
                .youthEmploymentRequirementLabels(List.of("미취업자", "(예비)창업자"))
                .youthSpecialRequirementCodes(List.of("0014003", "0014008"))
                .youthSpecialRequirementLabels(List.of("기초생활수급자", "지역인재"))
                .youthIncomeConditionTypeCode("0043002")
                .youthIncomeConditionTypeLabel("연소득")
                .build();
    }

    private ScoredCandidate scoredCandidate(WelfareService service,
                                            double ruleBaseScore,
                                            double ruleWeightedScore,
                                            Double aiScore,
                                            boolean specialTargetMismatch) {
        return ScoredCandidate.builder()
                .service(service)
                .ruleBaseScore(ruleBaseScore)
                .ruleWeightedScore(ruleWeightedScore)
                .aiScore(aiScore)
                .aiStatus(aiScore == null ? AiScoreStatus.NOT_REQUESTED : AiScoreStatus.SCORED)
                .hasSpecialTargetMismatch(specialTargetMismatch)
                .build();
    }
}
