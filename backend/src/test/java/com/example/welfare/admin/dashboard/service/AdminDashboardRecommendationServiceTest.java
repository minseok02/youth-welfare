package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminRecommendationBreakdownResponse;
import com.example.welfare.admin.dashboard.repository.AdminDashboardReadRows;
import com.example.welfare.admin.dashboard.repository.AdminDashboardRecommendationReadRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminDashboardRecommendationServiceTest {

    @Mock
    private AdminDashboardRecommendationReadRepository adminDashboardRecommendationReadRepository;

    @InjectMocks
    private AdminDashboardRecommendationService adminDashboardRecommendationService;

    @Test
    @DisplayName("추천 breakdown은 source/category/weight/fallback sample/clicked sample을 조합한다")
    void getRecommendationBreakdownsBuildsResponse() {
        given(adminDashboardRecommendationReadRepository.fetchRecommendationSummary(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).willReturn(new AdminDashboardReadRows.RecommendationSummaryRow(
                250,
                12,
                30,
                9,
                6,
                LocalDateTime.of(2026, 5, 2, 8, 45)
        ));
        given(adminDashboardRecommendationReadRepository.fetchRecommendationTrafficMix(org.mockito.ArgumentMatchers.any()))
                .willReturn(new AdminDashboardReadRows.RecommendationTrafficMixRow(
                        30,
                        0,
                        0,
                        0,
                        0,
                        18,
                        0,
                        0,
                        0,
                        0,
                        9,
                        0,
                        0,
                        0,
                        0
                ));
        given(adminDashboardRecommendationReadRepository.fetchRecommendationConcentration())
                .willReturn(new AdminDashboardReadRows.RecommendationConcentrationRow(
                        4071,
                        454,
                        113,
                        2622L,
                        "청년월세 지원사업",
                        "BOKJIRO_CENTRAL",
                        "주거",
                        271,
                        new BigDecimal("59.69"),
                        268,
                        1,
                        2,
                        0,
                        2,
                        "CONCENTRATED_TOP1",
                        "DEFERRED_NO_REAL_USER_COHORT",
                        "LOCAL_REAL_NON_EXAMPLE_SEED_WITH_NON_REAL_BATCH"
                ));
        given(adminDashboardRecommendationReadRepository.fetchRecommendationRecentWindowSnapshot(24, 2622L))
                .willReturn(new AdminDashboardReadRows.RecommendationRecentWindowRow(
                        24,
                        2622L,
                        83,
                        3,
                        80,
                        0,
                        3284L,
                        "인천 청년도약기지(취업아카데미)",
                        6,
                        5,
                        new BigDecimal("7.23"),
                        0,
                        0
                ));
        given(adminDashboardRecommendationReadRepository.fetchRecommendationReviewGateStalenessSnapshot(24, 2622L))
                .willReturn(new AdminDashboardReadRows.RecommendationReviewGateStalenessRow(
                        2622L,
                        "ALL_TIME_LATEST_PER_USER",
                        24,
                        454,
                        3,
                        272,
                        0,
                        LocalDateTime.of(2026, 5, 13, 13, 39, 31),
                        LocalDateTime.of(2026, 5, 17, 11, 49, 50),
                        80,
                        80,
                        0,
                        0
                ));
        given(adminDashboardRecommendationReadRepository.fetchTopRepeatedRecommendationServices(3))
                .willReturn(List.of(
                        new AdminDashboardReadRows.RecommendationRepeatedServiceRow(
                                2622L,
                                "청년월세 지원사업",
                                "BOKJIRO_CENTRAL",
                                "주거",
                                449,
                                449,
                                447,
                                0,
                                2,
                                0,
                                2
                        )
                ));
        given(adminDashboardRecommendationReadRepository.fetchTop1RecommendationServices(3))
                .willReturn(List.of(
                        new AdminDashboardReadRows.RecommendationTop1ServiceRow(
                                2622L,
                                "청년월세 지원사업",
                                "BOKJIRO_CENTRAL",
                                "주거",
                                271,
                                269,
                                0,
                                2,
                                0,
                                2
                        )
                ));
        given(adminDashboardRecommendationReadRepository.fetchRecommendationSourceBreakdowns(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(3)
        )).willReturn(List.of(
                new AdminDashboardReadRows.RecommendationSourceBreakdownRow("YOUTH", 18, 6, 3)
        ));
        given(adminDashboardRecommendationReadRepository.fetchRecommendationCategoryBreakdowns(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(3)
        )).willReturn(List.of(
                new AdminDashboardReadRows.RecommendationCategoryBreakdownRow("HOUSING", 10, 4, 1)
        ));
        given(adminDashboardRecommendationReadRepository.fetchRecommendationWeightBreakdowns(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(3)
        )).willReturn(List.of(
                new AdminDashboardReadRows.RecommendationWeightBreakdownRow(
                        "GROWTH",
                        new BigDecimal("0.60"),
                        new BigDecimal("0.40"),
                        12,
                        3,
                        2
                )
        ));
        given(adminDashboardRecommendationReadRepository.fetchLatestBatchYouthOfficialFacetRows(3))
                .willReturn(List.of(
                        new AdminDashboardReadRows.RecommendationFacetRow(
                                "YOUTH_INCOME_CONDITION_TYPE",
                                "기타",
                                12,
                                7
                        ),
                        new AdminDashboardReadRows.RecommendationFacetRow(
                                "YOUTH_EMPLOYMENT_REQUIREMENT",
                                "미취업자",
                                9,
                                5
                        )
                ));
        given(adminDashboardRecommendationReadRepository.fetchLatestBatchGov24FacetRows(3))
                .willReturn(List.of(
                        new AdminDashboardReadRows.RecommendationFacetRow(
                                "GOV24_SERVICE_FIELD",
                                "주거·자립",
                                5,
                                5
                        ),
                        new AdminDashboardReadRows.RecommendationFacetRow(
                                "GOV24_USER_TYPE_TOKEN",
                                "소상공인",
                                4,
                                4
                        ),
                        new AdminDashboardReadRows.RecommendationFacetRow(
                                "GOV24_BENEFIT_TYPE_TOKEN",
                                "현금(융자)",
                                3,
                                3
                        )
                ));
        given(adminDashboardRecommendationReadRepository.fetchRecentFallbackRecommendationSamples(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(3)
        )).willReturn(List.of(
                new AdminDashboardReadRows.RecommendationSampleRow(
                        101L,
                        501L,
                        "청년 월세 지원",
                        "YOUTH",
                        "HOUSING",
                        "EXAMPLE",
                        new BigDecimal("0.75231"),
                        true,
                        false,
                        LocalDateTime.of(2026, 5, 3, 9, 0),
                        null
                )
        ));
        given(adminDashboardRecommendationReadRepository.fetchRecentClickedRecommendationSamples(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(3)
        )).willReturn(List.of(
                new AdminDashboardReadRows.RecommendationSampleRow(
                        102L,
                        502L,
                        "청년 전세 지원",
                        "BOKJIRO_LOCAL",
                        "HOUSING",
                        "EXAMPLE",
                        new BigDecimal("0.88123"),
                        false,
                        true,
                        LocalDateTime.of(2026, 5, 3, 8, 0),
                        LocalDateTime.of(2026, 5, 3, 8, 30)
                )
        ));
        given(adminDashboardRecommendationReadRepository.fetchRecommendationRepeatExposureGroups(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(3)
        )).willReturn(List.of(
                new AdminDashboardReadRows.RecommendationRepeatExposureGroupRow(
                        "user-key-1",
                        501L,
                        "청년 월세 지원",
                        "YOUTH",
                        "HOUSING",
                        "EXAMPLE",
                        3,
                        1,
                        1,
                        LocalDateTime.of(2026, 5, 1, 8, 0),
                        LocalDateTime.of(2026, 5, 3, 9, 0),
                        LocalDateTime.of(2026, 5, 3, 9, 10)
                )
        ));

        AdminRecommendationBreakdownResponse response =
                adminDashboardRecommendationService.getRecommendationBreakdowns(14, 3);

        assertThat(response.windowDays()).isEqualTo(14);
        assertThat(response.sentLogsInWindow()).isEqualTo(30);
        assertThat(response.clickedLogsInWindow()).isEqualTo(9);
        assertThat(response.fallbackLogsInWindow()).isEqualTo(6);
        assertThat(response.trafficMixInWindow().exampleLogsInWindow()).isEqualTo(30);
        assertThat(response.trafficMixInWindow().boundedLocalUsersInWindow()).isZero();
        assertThat(response.trafficMixInWindow().localRealNonExampleSeedUsersInWindow()).isZero();
        assertThat(response.trafficMixInWindow().realUserUsersInWindow()).isZero();
        assertThat(response.trafficMixInWindow().realNonExampleUsersInWindow()).isZero();
        assertThat(response.realUserTrafficGateInWindow()).isEqualTo("DEFERRED_NO_REAL_USER_TRAFFIC");
        assertThat(response.recommendationReviewGate()).isEqualTo("DEFERRED_NO_REAL_USER_TRAFFIC");
        assertThat(response.latestBatchConcentration().latestBatchRows()).isEqualTo(4071);
        assertThat(response.latestBatchConcentration().top1LeaderServiceId()).isEqualTo(2622L);
        assertThat(response.latestBatchConcentration().top1LeaderSharePct()).isEqualByComparingTo("59.69");
        assertThat(response.latestBatchConcentration().top1LeaderUserMix().exampleUsers()).isEqualTo(268);
        assertThat(response.latestBatchConcentration().top1LeaderUserMix().boundedLocalUsers()).isEqualTo(1);
        assertThat(response.latestBatchConcentration().top1LeaderUserMix().localRealNonExampleSeedUsers()).isEqualTo(2);
        assertThat(response.latestBatchConcentration().top1LeaderUserMix().realUserUsers()).isZero();
        assertThat(response.latestBatchConcentration().top1LeaderSignalSummary())
                .isEqualTo("LOCAL_SEED_WITHOUT_REAL_USER_LEADER");
        assertThat(response.latestBatchConcentration().concentrationReadiness()).isEqualTo("CONCENTRATED_TOP1");
        assertThat(response.latestBatchConcentration().realUserCohortGate()).isEqualTo("DEFERRED_NO_REAL_USER_COHORT");
        assertThat(response.latestBatchConcentration().signalQuality()).isEqualTo("LOCAL_REAL_NON_EXAMPLE_SEED_WITH_NON_REAL_BATCH");
        assertThat(response.recentWindowLatestBatch().recentWindowHours()).isEqualTo(24);
        assertThat(response.recentWindowLatestBatch().top1LeaderServiceId()).isEqualTo(3284L);
        assertThat(response.recentWindowLatestBatch().top1LeaderRealUserUsers()).isEqualTo(5);
        assertThat(response.recentWindowLatestBatch().targetTop1Users()).isZero();
        assertThat(response.reviewGateStaleness().primaryReferenceMode()).isEqualTo("ALL_TIME_LATEST_PER_USER");
        assertThat(response.reviewGateStaleness().exampleTargetTop1Users()).isEqualTo(272);
        assertThat(response.reviewGateStaleness().exampleTargetTop1Last24h()).isZero();
        assertThat(response.reviewGateStaleness().realUserLatestUsers()).isEqualTo(80);
        assertThat(response.reviewGateStaleness().realUserTargetTop1Users()).isZero();
        assertThat(response.recentWindowRecommendationReviewReading())
                .isEqualTo("RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE");
        assertThat(response.historicalExampleDominanceDetected()).isFalse();
        assertThat(response.reviewGatePolicyCandidateStatus())
                .isEqualTo("NOT_A_CANDIDATE_PRIMARY_GATE_NOT_NON_REAL_BLOCKED");
        assertThat(response.reviewGatePolicyCandidateReason())
                .isEqualTo("PRIMARY_REVIEW_GATE_IS_NOT_DEFERRED_NON_REAL_LEADER_SIGNAL");
        assertThat(response.reviewGatePolicyPromotionStatus())
                .isEqualTo("KEEP_PRIMARY_BASELINE");
        assertThat(response.reviewGatePolicyPromotionReason())
                .isEqualTo("RECENT_WINDOW_CANDIDATE_HAS_NOT_CLEARED_PRIMARY_BASELINE_REQUIREMENTS");
        assertThat(response.reviewGatePolicyPromotionActionStatus())
                .isEqualTo("KEEP_PRIMARY_BASELINE");
        assertThat(response.reviewGatePolicyPromotionActionReason())
                .isEqualTo("RECENT_WINDOW_POLICY_PROMOTION_CONDITIONS_NOT_MET");
        assertThat(response.reviewGatePolicyPromotionReadinessStatus())
                .isEqualTo("NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW");
        assertThat(response.reviewGatePolicyPromotionReadinessReason())
                .isEqualTo("REAL_USER_TRAFFIC_GATE_NOT_READY");
        assertThat(response.reviewGatePolicyPromotionExecutionStatus())
                .isEqualTo("DO_NOT_RUN_BOUNDED_PROMOTION_REVIEW");
        assertThat(response.reviewGatePolicyPromotionExecutionReason())
                .isEqualTo("REAL_USER_TRAFFIC_GATE_NOT_READY");
        assertThat(response.reviewGatePolicyPromotionApprovalCriteriaStatus())
                .isEqualTo("NOT_READY_FOR_EXPLICIT_PROMOTION_APPROVAL");
        assertThat(response.reviewGatePolicyPromotionApprovalCriteriaReason())
                .isEqualTo("REAL_USER_TRAFFIC_GATE_NOT_READY");
        assertThat(response.reviewGatePolicyPromotionApprovalStatus())
                .isEqualTo("PROMOTION_APPROVAL_NOT_APPLICABLE");
        assertThat(response.reviewGatePolicyPromotionApprovalReason())
                .isEqualTo("REAL_USER_TRAFFIC_GATE_NOT_READY");
        assertThat(response.reviewGatePolicyPromotionApprovalDecisionStatus())
                .isEqualTo("APPROVAL_DECISION_NOT_READY");
        assertThat(response.reviewGatePolicyPromotionApprovalDecisionReason())
                .isEqualTo("REAL_USER_TRAFFIC_GATE_NOT_READY");
        assertThat(response.reviewGatePolicyPromotionApprovalRecordStatus())
                .isEqualTo("APPROVAL_RECORD_NOT_READY");
        assertThat(response.reviewGatePolicyPromotionApprovalRecordReason())
                .isEqualTo("REAL_USER_TRAFFIC_GATE_NOT_READY");
        assertThat(response.reviewGatePolicyPromotionReviewRunStatus())
                .isEqualTo("BOUNDED_PROMOTION_REVIEW_RUN_NOT_READY");
        assertThat(response.reviewGatePolicyPromotionReviewRunReason())
                .isEqualTo("REAL_USER_TRAFFIC_GATE_NOT_READY");
        assertThat(response.reviewGatePolicyPromotionReviewRunCriteriaStatus())
                .isEqualTo("NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN");
        assertThat(response.reviewGatePolicyPromotionReviewRunCriteriaReason())
                .isEqualTo("REAL_USER_TRAFFIC_GATE_NOT_READY");
        assertThat(response.topRepeatedServices()).singleElement().satisfies(service -> {
            assertThat(service.serviceId()).isEqualTo(2622L);
            assertThat(service.rowCount()).isEqualTo(449);
            assertThat(service.distinctUsers()).isEqualTo(449);
            assertThat(service.userMix().exampleUsers()).isEqualTo(447);
            assertThat(service.userMix().localRealNonExampleSeedUsers()).isEqualTo(2);
            assertThat(service.userMix().realUserUsers()).isZero();
        });
        assertThat(response.top1Services()).singleElement().satisfies(service -> {
            assertThat(service.serviceId()).isEqualTo(2622L);
            assertThat(service.usersAsTop1()).isEqualTo(271);
            assertThat(service.userMix().exampleUsers()).isEqualTo(269);
            assertThat(service.userMix().localRealNonExampleSeedUsers()).isEqualTo(2);
            assertThat(service.userMix().realUserUsers()).isZero();
        });
        assertThat(response.trafficMixInWindow().exampleClickedUsersInWindow()).isEqualTo(9);
        assertThat(response.sourceBreakdowns()).singleElement().satisfies(source -> {
            assertThat(source.sourceType()).isEqualTo("YOUTH");
            assertThat(source.sentCount()).isEqualTo(18);
            assertThat(source.clickedCount()).isEqualTo(6);
            assertThat(source.fallbackCount()).isEqualTo(3);
            assertThat(source.clickThroughRate()).isEqualByComparingTo("0.3333");
            assertThat(source.fallbackRate()).isEqualByComparingTo("0.1667");
        });
        assertThat(response.categoryBreakdowns()).singleElement().satisfies(category -> {
            assertThat(category.category()).isEqualTo("HOUSING");
            assertThat(category.clickThroughRate()).isEqualByComparingTo("0.4000");
        });
        assertThat(response.weightBreakdowns()).singleElement().satisfies(weight -> {
            assertThat(weight.weightKey()).isEqualTo("GROWTH");
            assertThat(weight.ruleWeight()).isEqualByComparingTo("0.60");
            assertThat(weight.aiWeight()).isEqualByComparingTo("0.40");
            assertThat(weight.clickThroughRate()).isEqualByComparingTo("0.2500");
            assertThat(weight.fallbackRate()).isEqualByComparingTo("0.1667");
        });
        assertThat(response.youthOfficialFacetGroups()).hasSize(2);
        assertThat(response.youthOfficialFacetGroups().get(0).facetKey()).isEqualTo("YOUTH_INCOME_CONDITION_TYPE");
        assertThat(response.youthOfficialFacetGroups().get(0).label()).isEqualTo("소득조건 유형");
        assertThat(response.youthOfficialFacetGroups().get(0).buckets()).singleElement().satisfies(bucket -> {
            assertThat(bucket.label()).isEqualTo("기타");
            assertThat(bucket.rowCount()).isEqualTo(12);
            assertThat(bucket.distinctServices()).isEqualTo(7);
        });
        assertThat(response.youthOfficialFacetGroups().get(1).facetKey()).isEqualTo("YOUTH_EMPLOYMENT_REQUIREMENT");
        assertThat(response.gov24FacetGroups()).hasSize(3);
        assertThat(response.gov24FacetGroups().get(0).facetKey()).isEqualTo("GOV24_SERVICE_FIELD");
        assertThat(response.gov24FacetGroups().get(0).label()).isEqualTo("Gov24 서비스분야");
        assertThat(response.gov24FacetGroups().get(0).buckets()).singleElement().satisfies(bucket -> {
            assertThat(bucket.label()).isEqualTo("주거·자립");
            assertThat(bucket.rowCount()).isEqualTo(5);
            assertThat(bucket.distinctServices()).isEqualTo(5);
        });
        assertThat(response.gov24FacetGroups().get(1).facetKey()).isEqualTo("GOV24_USER_TYPE_TOKEN");
        assertThat(response.gov24FacetGroups().get(1).label()).isEqualTo("Gov24 사용자구분");
        assertThat(response.gov24FacetGroups().get(1).buckets()).singleElement().satisfies(bucket -> {
            assertThat(bucket.label()).isEqualTo("소상공인");
            assertThat(bucket.rowCount()).isEqualTo(4);
            assertThat(bucket.distinctServices()).isEqualTo(4);
        });
        assertThat(response.gov24FacetGroups().get(2).facetKey()).isEqualTo("GOV24_BENEFIT_TYPE_TOKEN");
        assertThat(response.recentFallbackSamples()).singleElement().satisfies(sample -> {
            assertThat(sample.logId()).isEqualTo(101L);
            assertThat(sample.title()).isEqualTo("청년 월세 지원");
            assertThat(sample.userCohort()).isEqualTo("EXAMPLE");
            assertThat(sample.fallback()).isTrue();
        });
        assertThat(response.recentClickedSamples()).singleElement().satisfies(sample -> {
            assertThat(sample.logId()).isEqualTo(102L);
            assertThat(sample.title()).isEqualTo("청년 전세 지원");
            assertThat(sample.userCohort()).isEqualTo("EXAMPLE");
            assertThat(sample.clicked()).isTrue();
            assertThat(sample.clickedAt()).isEqualTo(LocalDateTime.of(2026, 5, 3, 8, 30));
        });
        assertThat(response.repeatExposureGroups()).singleElement().satisfies(group -> {
            assertThat(group.userKey()).isEqualTo("user-key-1");
            assertThat(group.serviceId()).isEqualTo(501L);
            assertThat(group.title()).isEqualTo("청년 월세 지원");
            assertThat(group.userCohort()).isEqualTo("EXAMPLE");
            assertThat(group.exposureCount()).isEqualTo(3);
            assertThat(group.clickedCount()).isEqualTo(1);
            assertThat(group.fallbackCount()).isEqualTo(1);
            assertThat(group.firstSentAt()).isEqualTo(LocalDateTime.of(2026, 5, 1, 8, 0));
            assertThat(group.latestSentAt()).isEqualTo(LocalDateTime.of(2026, 5, 3, 9, 0));
            assertThat(group.latestClickedAt()).isEqualTo(LocalDateTime.of(2026, 5, 3, 9, 10));
        });
    }
}
