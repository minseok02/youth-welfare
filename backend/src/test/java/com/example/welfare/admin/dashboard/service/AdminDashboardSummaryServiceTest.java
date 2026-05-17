package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminDashboardResponse;
import com.example.welfare.admin.dashboard.repository.AdminDashboardCollectReadRepository;
import com.example.welfare.admin.dashboard.repository.AdminDashboardNotificationReadRepository;
import com.example.welfare.admin.dashboard.repository.AdminDashboardReadRows;
import com.example.welfare.admin.dashboard.repository.AdminDashboardRecommendationReadRepository;
import com.example.welfare.admin.dashboard.repository.AdminDashboardSearchReadRepository;
import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.recommend.service.ScoreWeightService;
import com.example.welfare.user.dto.response.UserPiiSyncStatusResponse;
import com.example.welfare.user.service.UserPiiSyncStatusService;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminDashboardSummaryServiceTest {

    @Mock
    private AdminDashboardCollectReadRepository adminDashboardCollectReadRepository;

    @Mock
    private AdminDashboardRecommendationReadRepository adminDashboardRecommendationReadRepository;

    @Mock
    private AdminDashboardNotificationReadRepository adminDashboardNotificationReadRepository;

    @Mock
    private AdminDashboardSearchReadRepository adminDashboardSearchReadRepository;

    @Mock
    private UserPiiSyncStatusService userPiiSyncStatusService;

    @Mock
    private ScoreWeightService scoreWeightService;

    @InjectMocks
    private AdminDashboardSummaryService adminDashboardSummaryService;

    @Test
    @DisplayName("대시보드 요약은 collect/recommendation/search/notification/user sync 지표를 조합한다")
    void getSummaryBuildsDashboardResponse() {
        given(adminDashboardCollectReadRepository.fetchCollectSummary(org.mockito.ArgumentMatchers.any()))
                .willReturn(new AdminDashboardReadRows.CollectSummaryRow(1, 3, 1, 2));
        given(adminDashboardCollectReadRepository.fetchLatestCollectJobs())
                .willReturn(List.of(
                        new AdminDashboardReadRows.CollectJobSnapshotRow(
                                "YOUTH",
                                "SUCCESS",
                                LocalDateTime.of(2026, 5, 2, 8, 0),
                                LocalDateTime.of(2026, 5, 2, 8, 2),
                                2363,
                                2363,
                                0
                        )
                ));
        given(adminDashboardCollectReadRepository.fetchLatestCollectFailures(org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of(
                        new AdminDashboardReadRows.CollectFailureSnapshotRow(
                                "BOKJIRO_LOCAL",
                                "FAILED",
                                LocalDateTime.of(2026, 5, 2, 7, 0),
                                LocalDateTime.of(2026, 5, 2, 7, 1),
                                "COL001",
                                "rate limited",
                                0,
                                0,
                                1
                        )
                ));
        given(adminDashboardCollectReadRepository.fetchCollectTrend(org.mockito.ArgumentMatchers.any()))
                .willReturn(
                        new AdminDashboardReadRows.CollectTrendRow(3, 1, 0),
                        new AdminDashboardReadRows.CollectTrendRow(9, 2, 1),
                        new AdminDashboardReadRows.CollectTrendRow(18, 4, 2)
                );
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
                        "CONCENTRATED_TOP1",
                        "DEFERRED_NO_REAL_USER_COHORT",
                        "LOCAL_REAL_NON_EXAMPLE_SEED_WITH_NON_REAL_BATCH"
                ));
        ScoreWeight activeWeight = ScoreWeight.builder()
                .weightKey("GROWTH")
                .ruleWeight(new BigDecimal("0.60"))
                .aiWeight(new BigDecimal("0.40"))
                .minLogCount(100)
                .isActive(true)
                .build();
        given(scoreWeightService.getProgress(250))
                .willReturn(new ScoreWeightService.ScoreWeightProgress(
                        activeWeight,
                        250,
                        "STABLE",
                        500,
                        250L,
                        false
                ));
        given(adminDashboardRecommendationReadRepository.fetchRecommendationWeightBuckets(org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of(
                        new AdminDashboardReadRows.RecommendationWeightSnapshotRow("GROWTH", new BigDecimal("0.60"), new BigDecimal("0.40"), 18),
                        new AdminDashboardReadRows.RecommendationWeightSnapshotRow("COLD_START", new BigDecimal("0.80"), new BigDecimal("0.20"), 12)
                ));
        given(adminDashboardRecommendationReadRepository.fetchRecommendationTrend(org.mockito.ArgumentMatchers.any()))
                .willReturn(
                        new AdminDashboardReadRows.RecommendationTrendRow(8, 3, 1),
                        new AdminDashboardReadRows.RecommendationTrendRow(30, 9, 6),
                        new AdminDashboardReadRows.RecommendationTrendRow(90, 18, 20)
                );
        given(adminDashboardNotificationReadRepository.fetchNotificationSummary(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).willReturn(new AdminDashboardReadRows.NotificationSummaryRow(4, 1, 14, 2));
        given(adminDashboardSearchReadRepository.fetchSearchSummary(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).willReturn(new AdminDashboardReadRows.SearchSummaryRow(21, 88, 13, 43, new BigDecimal("6.375")));
        given(adminDashboardSearchReadRepository.fetchSearchTrend(org.mockito.ArgumentMatchers.any()))
                .willReturn(
                        new AdminDashboardReadRows.SearchTrendRow(12, 2),
                        new AdminDashboardReadRows.SearchTrendRow(88, 13),
                        new AdminDashboardReadRows.SearchTrendRow(240, 31)
                );
        given(adminDashboardSearchReadRepository.fetchTopSearchKeywords(org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of(
                        new AdminDashboardReadRows.SearchKeywordSnapshotRow("월세", 17),
                        new AdminDashboardReadRows.SearchKeywordSnapshotRow("주거", 9)
                ));
        given(adminDashboardSearchReadRepository.fetchTopZeroResultSearchKeywords(org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of(
                        new AdminDashboardReadRows.SearchKeywordSnapshotRow("대출", 4),
                        new AdminDashboardReadRows.SearchKeywordSnapshotRow("월세", 2)
                ));
        given(userPiiSyncStatusService.getStatus(5))
                .willReturn(new UserPiiSyncStatusResponse(
                        2,
                        1,
                        40,
                        null,
                        null,
                        "user-key-failed",
                        LocalDateTime.of(2026, 5, 2, 7, 30),
                        LocalDateTime.of(2026, 5, 2, 7, 45),
                        List.of()
                ));

        AdminDashboardResponse response = adminDashboardSummaryService.getSummary(null, null);

        assertThat(response.collect().runningJobs()).isEqualTo(1);
        assertThat(response.collect().latestJobs()).hasSize(1);
        assertThat(response.collect().windowDays()).isEqualTo(7);
        assertThat(response.collect().latestFailuresInWindow()).hasSize(1);
        assertThat(response.collect().latestFailuresInWindow().get(0).jobName()).isEqualTo("BOKJIRO_LOCAL");
        assertThat(response.trend().collect()).extracting(AdminDashboardResponse.CollectTrendPoint::windowDays)
                .containsExactly(1, 7, 30);
        assertThat(response.recommendation().activeWeightKey()).isEqualTo("GROWTH");
        assertThat(response.recommendation().activeRuleWeight()).isEqualByComparingTo("0.60");
        assertThat(response.recommendation().activeAiWeight()).isEqualByComparingTo("0.40");
        assertThat(response.recommendation().nextWeightKey()).isEqualTo("STABLE");
        assertThat(response.recommendation().nextWeightMinLogCount()).isEqualTo(500);
        assertThat(response.recommendation().remainingLogsUntilNextWeight()).isEqualTo(250L);
        assertThat(response.recommendation().topWeightStage()).isFalse();
        assertThat(response.recommendation().totalLogs()).isEqualTo(250);
        assertThat(response.recommendation().windowDays()).isEqualTo(7);
        assertThat(response.recommendation().sentInWindow()).isEqualTo(30);
        assertThat(response.recommendation().latestClickedAt()).isEqualTo(LocalDateTime.of(2026, 5, 2, 8, 45));
        assertThat(response.recommendation().clickThroughRateInWindow()).isEqualByComparingTo("0.3000");
        assertThat(response.recommendation().fallbackRateInWindow()).isEqualByComparingTo("0.2000");
        assertThat(response.recommendation().trafficMixInWindow().exampleLogsInWindow()).isEqualTo(30);
        assertThat(response.recommendation().trafficMixInWindow().boundedLocalLogsInWindow()).isZero();
        assertThat(response.recommendation().trafficMixInWindow().localRealNonExampleSeedLogsInWindow()).isZero();
        assertThat(response.recommendation().trafficMixInWindow().realUserLogsInWindow()).isZero();
        assertThat(response.recommendation().trafficMixInWindow().realNonExampleLogsInWindow()).isZero();
        assertThat(response.recommendation().realUserTrafficGateInWindow()).isEqualTo("DEFERRED_NO_REAL_USER_TRAFFIC");
        assertThat(response.recommendation().latestBatchConcentration().latestBatchRows()).isEqualTo(4071);
        assertThat(response.recommendation().latestBatchConcentration().top1LeaderServiceId()).isEqualTo(2622L);
        assertThat(response.recommendation().latestBatchConcentration().top1LeaderSharePct()).isEqualByComparingTo("59.69");
        assertThat(response.recommendation().latestBatchConcentration().concentrationReadiness()).isEqualTo("CONCENTRATED_TOP1");
        assertThat(response.recommendation().latestBatchConcentration().realUserCohortGate()).isEqualTo("DEFERRED_NO_REAL_USER_COHORT");
        assertThat(response.recommendation().trafficMixInWindow().exampleClickedUsersInWindow()).isEqualTo(9);
        assertThat(response.recommendation().weightBucketsInWindow()).extracting(AdminDashboardResponse.RecommendationWeightSnapshot::weightKey)
                .containsExactly("GROWTH", "COLD_START");
        assertThat(response.trend().recommendation()).extracting(AdminDashboardResponse.RecommendationTrendPoint::windowDays)
                .containsExactly(1, 7, 30);
        assertThat(response.trend().recommendation().get(1).clickThroughRate()).isEqualByComparingTo("0.3000");
        assertThat(response.notification().failedLast24h()).isEqualTo(1);
        assertThat(response.notification().windowDays()).isEqualTo(7);
        assertThat(response.notification().sentInWindow()).isEqualTo(14);
        assertThat(response.notification().failedInWindow()).isEqualTo(2);
        assertThat(response.search().windowDays()).isEqualTo(7);
        assertThat(response.search().searchesInWindow()).isEqualTo(88);
        assertThat(response.search().zeroResultSearchesInWindow()).isEqualTo(13);
        assertThat(response.search().averageResultCountInWindow()).isEqualByComparingTo("6.38");
        assertThat(response.search().topKeywordsInWindow()).extracting(AdminDashboardResponse.SearchKeywordSnapshot::keyword)
                .containsExactly("월세", "주거");
        assertThat(response.search().zeroResultKeywordsInWindow()).extracting(AdminDashboardResponse.SearchKeywordSnapshot::keyword)
                .containsExactly("대출", "월세");
        assertThat(response.trend().search()).extracting(AdminDashboardResponse.SearchTrendPoint::windowDays)
                .containsExactly(1, 7, 30);
        assertThat(response.userPiiSync().failedCount()).isEqualTo(1);
        assertThat(response.userPiiSync().latestSyncedAt()).isEqualTo(LocalDateTime.of(2026, 5, 2, 7, 45));
    }

    @Test
    @DisplayName("대시보드 요약은 요청한 trend window days만 사용한다")
    void getSummaryUsesRequestedTrendWindows() {
        given(adminDashboardCollectReadRepository.fetchCollectSummary(org.mockito.ArgumentMatchers.any()))
                .willReturn(new AdminDashboardReadRows.CollectSummaryRow(0, 0, 0, 0));
        given(adminDashboardCollectReadRepository.fetchLatestCollectJobs()).willReturn(List.of());
        given(adminDashboardCollectReadRepository.fetchLatestCollectFailures(org.mockito.ArgumentMatchers.any())).willReturn(List.of());
        given(adminDashboardRecommendationReadRepository.fetchRecommendationSummary(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).willReturn(new AdminDashboardReadRows.RecommendationSummaryRow(0, 0, 0, 0, 0, null));
        given(adminDashboardRecommendationReadRepository.fetchRecommendationTrafficMix(org.mockito.ArgumentMatchers.any()))
                .willReturn(new AdminDashboardReadRows.RecommendationTrafficMixRow(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        given(adminDashboardRecommendationReadRepository.fetchRecommendationConcentration())
                .willReturn(new AdminDashboardReadRows.RecommendationConcentrationRow(
                        0,
                        0,
                        0,
                        null,
                        null,
                        null,
                        null,
                        0,
                        BigDecimal.ZERO,
                        "DEFERRED_EMPTY_COHORT",
                        "DEFERRED_EMPTY_COHORT",
                        "EMPTY_COHORT"
                ));
        ScoreWeight activeWeight = ScoreWeight.builder()
                .weightKey("GROWTH")
                .ruleWeight(new BigDecimal("0.60"))
                .aiWeight(new BigDecimal("0.40"))
                .minLogCount(100)
                .isActive(true)
                .build();
        given(scoreWeightService.getProgress(0))
                .willReturn(new ScoreWeightService.ScoreWeightProgress(
                        activeWeight,
                        0,
                        "GROWTH",
                        100,
                        100L,
                        false
                ));
        given(adminDashboardRecommendationReadRepository.fetchRecommendationWeightBuckets(org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of());
        given(adminDashboardNotificationReadRepository.fetchNotificationSummary(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).willReturn(new AdminDashboardReadRows.NotificationSummaryRow(0, 0, 0, 0));
        given(adminDashboardSearchReadRepository.fetchSearchSummary(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).willReturn(new AdminDashboardReadRows.SearchSummaryRow(0, 0, 0, 0, BigDecimal.ZERO));
        given(adminDashboardSearchReadRepository.fetchTopSearchKeywords(org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of());
        given(adminDashboardSearchReadRepository.fetchTopZeroResultSearchKeywords(org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of());
        given(userPiiSyncStatusService.getStatus(5))
                .willReturn(new UserPiiSyncStatusResponse(0, 0, 0, null, null, null, null, null, List.of()));

        given(adminDashboardCollectReadRepository.fetchCollectTrend(org.mockito.ArgumentMatchers.any()))
                .willReturn(
                        new AdminDashboardReadRows.CollectTrendRow(1, 0, 0),
                        new AdminDashboardReadRows.CollectTrendRow(2, 0, 0)
                );
        given(adminDashboardRecommendationReadRepository.fetchRecommendationTrend(org.mockito.ArgumentMatchers.any()))
                .willReturn(
                        new AdminDashboardReadRows.RecommendationTrendRow(3, 1, 0),
                        new AdminDashboardReadRows.RecommendationTrendRow(4, 2, 0)
                );
        given(adminDashboardSearchReadRepository.fetchSearchTrend(org.mockito.ArgumentMatchers.any()))
                .willReturn(
                        new AdminDashboardReadRows.SearchTrendRow(5, 1),
                        new AdminDashboardReadRows.SearchTrendRow(6, 2)
                );

        AdminDashboardResponse response = adminDashboardSummaryService.getSummary(14, List.of(3, 14, 14, -1, 400));

        assertThat(response.collect().windowDays()).isEqualTo(14);
        assertThat(response.trend().collect()).extracting(AdminDashboardResponse.CollectTrendPoint::windowDays)
                .containsExactly(3, 14);
        assertThat(response.recommendation().windowDays()).isEqualTo(14);
        assertThat(response.recommendation().nextWeightKey()).isEqualTo("GROWTH");
        assertThat(response.recommendation().realUserTrafficGateInWindow()).isEqualTo("DEFERRED_EMPTY_COHORT");
        assertThat(response.recommendation().latestBatchConcentration().concentrationReadiness()).isEqualTo("DEFERRED_EMPTY_COHORT");
        assertThat(response.notification().windowDays()).isEqualTo(14);
        assertThat(response.trend().recommendation()).extracting(AdminDashboardResponse.RecommendationTrendPoint::windowDays)
                .containsExactly(3, 14);
        assertThat(response.search().windowDays()).isEqualTo(14);
        assertThat(response.trend().search()).extracting(AdminDashboardResponse.SearchTrendPoint::windowDays)
                .containsExactly(3, 14);

        verify(adminDashboardCollectReadRepository, times(2)).fetchCollectTrend(org.mockito.ArgumentMatchers.any());
        verify(adminDashboardRecommendationReadRepository, times(2)).fetchRecommendationTrend(org.mockito.ArgumentMatchers.any());
        verify(adminDashboardSearchReadRepository, times(2)).fetchSearchTrend(org.mockito.ArgumentMatchers.any());
    }
}
