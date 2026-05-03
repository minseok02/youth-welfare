package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminDashboardResponse;
import com.example.welfare.admin.dashboard.repository.AdminDashboardReadRepository;
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
class AdminDashboardServiceTest {

    @Mock
    private AdminDashboardReadRepository adminDashboardReadRepository;

    @Mock
    private UserPiiSyncStatusService userPiiSyncStatusService;

    @Mock
    private ScoreWeightService scoreWeightService;

    @InjectMocks
    private AdminDashboardService adminDashboardService;

    @Test
    @DisplayName("대시보드 요약은 collect/recommendation/search/notification/user sync 지표를 조합한다")
    void getSummaryBuildsDashboardResponse() {
        given(adminDashboardReadRepository.fetchCollectSummary(org.mockito.ArgumentMatchers.any()))
                .willReturn(new AdminDashboardReadRepository.CollectSummaryRow(1, 3, 1, 2));
        given(adminDashboardReadRepository.fetchLatestCollectJobs())
                .willReturn(List.of(
                        new AdminDashboardReadRepository.CollectJobSnapshotRow(
                                "YOUTH",
                                "SUCCESS",
                                LocalDateTime.of(2026, 5, 2, 8, 0),
                                LocalDateTime.of(2026, 5, 2, 8, 2),
                                2363,
                                2363,
                                0
                        )
                ));
        given(adminDashboardReadRepository.fetchLatestCollectFailures(org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of(
                        new AdminDashboardReadRepository.CollectFailureSnapshotRow(
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
        given(adminDashboardReadRepository.fetchCollectTrend(org.mockito.ArgumentMatchers.any()))
                .willReturn(
                        new AdminDashboardReadRepository.CollectTrendRow(3, 1, 0),
                        new AdminDashboardReadRepository.CollectTrendRow(9, 2, 1),
                        new AdminDashboardReadRepository.CollectTrendRow(18, 4, 2)
                );
        given(adminDashboardReadRepository.fetchRecommendationSummary(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).willReturn(new AdminDashboardReadRepository.RecommendationSummaryRow(
                250,
                12,
                30,
                9,
                6,
                LocalDateTime.of(2026, 5, 2, 8, 45)
        ));
        given(scoreWeightService.getActiveWeight())
                .willReturn(ScoreWeight.builder()
                        .weightKey("GROWTH")
                        .ruleWeight(new BigDecimal("0.60"))
                        .aiWeight(new BigDecimal("0.40"))
                        .minLogCount(100)
                        .isActive(true)
                        .build());
        given(adminDashboardReadRepository.fetchRecommendationWeightBuckets(org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of(
                        new AdminDashboardReadRepository.RecommendationWeightSnapshotRow("GROWTH", new BigDecimal("0.60"), new BigDecimal("0.40"), 18),
                        new AdminDashboardReadRepository.RecommendationWeightSnapshotRow("COLD_START", new BigDecimal("0.80"), new BigDecimal("0.20"), 12)
                ));
        given(adminDashboardReadRepository.fetchRecommendationTrend(org.mockito.ArgumentMatchers.any()))
                .willReturn(
                        new AdminDashboardReadRepository.RecommendationTrendRow(8, 3, 1),
                        new AdminDashboardReadRepository.RecommendationTrendRow(30, 9, 6),
                        new AdminDashboardReadRepository.RecommendationTrendRow(90, 18, 20)
                );
        given(adminDashboardReadRepository.fetchNotificationSummary(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).willReturn(new AdminDashboardReadRepository.NotificationSummaryRow(4, 1, 14, 2));
        given(adminDashboardReadRepository.fetchSearchSummary(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).willReturn(new AdminDashboardReadRepository.SearchSummaryRow(21, 88, 13, 43, new BigDecimal("6.375")));
        given(adminDashboardReadRepository.fetchSearchTrend(org.mockito.ArgumentMatchers.any()))
                .willReturn(
                        new AdminDashboardReadRepository.SearchTrendRow(12, 2),
                        new AdminDashboardReadRepository.SearchTrendRow(88, 13),
                        new AdminDashboardReadRepository.SearchTrendRow(240, 31)
                );
        given(adminDashboardReadRepository.fetchTopSearchKeywords(org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of(
                        new AdminDashboardReadRepository.SearchKeywordSnapshotRow("월세", 17),
                        new AdminDashboardReadRepository.SearchKeywordSnapshotRow("주거", 9)
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

        AdminDashboardResponse response = adminDashboardService.getSummary();

        assertThat(response.collect().runningJobs()).isEqualTo(1);
        assertThat(response.collect().latestJobs()).hasSize(1);
        assertThat(response.collect().failureWindowDays()).isEqualTo(7);
        assertThat(response.collect().latestFailuresInWindow()).hasSize(1);
        assertThat(response.collect().latestFailuresInWindow().get(0).jobName()).isEqualTo("BOKJIRO_LOCAL");
        assertThat(response.trend().collect()).extracting(AdminDashboardResponse.CollectTrendPoint::windowDays)
                .containsExactly(1, 7, 30);
        assertThat(response.recommendation().activeWeightKey()).isEqualTo("GROWTH");
        assertThat(response.recommendation().activeRuleWeight()).isEqualByComparingTo("0.60");
        assertThat(response.recommendation().activeAiWeight()).isEqualByComparingTo("0.40");
        assertThat(response.recommendation().totalLogs()).isEqualTo(250);
        assertThat(response.recommendation().windowDays()).isEqualTo(7);
        assertThat(response.recommendation().sentInWindow()).isEqualTo(30);
        assertThat(response.recommendation().latestClickedAt()).isEqualTo(LocalDateTime.of(2026, 5, 2, 8, 45));
        assertThat(response.recommendation().clickThroughRateInWindow()).isEqualByComparingTo("0.3000");
        assertThat(response.recommendation().fallbackRateInWindow()).isEqualByComparingTo("0.2000");
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
        assertThat(response.trend().search()).extracting(AdminDashboardResponse.SearchTrendPoint::windowDays)
                .containsExactly(1, 7, 30);
        assertThat(response.userPiiSync().failedCount()).isEqualTo(1);
        assertThat(response.userPiiSync().latestSyncedAt()).isEqualTo(LocalDateTime.of(2026, 5, 2, 7, 45));
    }

    @Test
    @DisplayName("대시보드 요약은 요청한 trend window days만 사용한다")
    void getSummaryUsesRequestedTrendWindows() {
        given(adminDashboardReadRepository.fetchCollectSummary(org.mockito.ArgumentMatchers.any()))
                .willReturn(new AdminDashboardReadRepository.CollectSummaryRow(0, 0, 0, 0));
        given(adminDashboardReadRepository.fetchLatestCollectJobs()).willReturn(List.of());
        given(adminDashboardReadRepository.fetchLatestCollectFailures(org.mockito.ArgumentMatchers.any())).willReturn(List.of());
        given(adminDashboardReadRepository.fetchRecommendationSummary(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).willReturn(new AdminDashboardReadRepository.RecommendationSummaryRow(
                0, 0, 0, 0, 0, null
        ));
        given(scoreWeightService.getActiveWeight())
                .willReturn(ScoreWeight.builder()
                        .weightKey("GROWTH")
                        .ruleWeight(new BigDecimal("0.60"))
                        .aiWeight(new BigDecimal("0.40"))
                        .minLogCount(100)
                        .isActive(true)
                        .build());
        given(adminDashboardReadRepository.fetchRecommendationWeightBuckets(org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of());
        given(adminDashboardReadRepository.fetchNotificationSummary(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).willReturn(new AdminDashboardReadRepository.NotificationSummaryRow(0, 0, 0, 0));
        given(adminDashboardReadRepository.fetchSearchSummary(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).willReturn(new AdminDashboardReadRepository.SearchSummaryRow(0, 0, 0, 0, BigDecimal.ZERO));
        given(adminDashboardReadRepository.fetchTopSearchKeywords(org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of());
        given(userPiiSyncStatusService.getStatus(5))
                .willReturn(new UserPiiSyncStatusResponse(0, 0, 0, null, null, null, null, null, List.of()));

        given(adminDashboardReadRepository.fetchCollectTrend(org.mockito.ArgumentMatchers.any()))
                .willReturn(
                        new AdminDashboardReadRepository.CollectTrendRow(1, 0, 0),
                        new AdminDashboardReadRepository.CollectTrendRow(2, 0, 0)
                );
        given(adminDashboardReadRepository.fetchRecommendationTrend(org.mockito.ArgumentMatchers.any()))
                .willReturn(
                        new AdminDashboardReadRepository.RecommendationTrendRow(3, 1, 0),
                        new AdminDashboardReadRepository.RecommendationTrendRow(4, 2, 0)
                );
        given(adminDashboardReadRepository.fetchSearchTrend(org.mockito.ArgumentMatchers.any()))
                .willReturn(
                        new AdminDashboardReadRepository.SearchTrendRow(5, 1),
                        new AdminDashboardReadRepository.SearchTrendRow(6, 2)
                );

        AdminDashboardResponse response = adminDashboardService.getSummary(14, List.of(3, 14, 14, -1, 400));

        assertThat(response.collect().failureWindowDays()).isEqualTo(14);
        assertThat(response.trend().collect()).extracting(AdminDashboardResponse.CollectTrendPoint::windowDays)
                .containsExactly(3, 14);
        assertThat(response.recommendation().windowDays()).isEqualTo(14);
        assertThat(response.notification().windowDays()).isEqualTo(14);
        assertThat(response.trend().recommendation()).extracting(AdminDashboardResponse.RecommendationTrendPoint::windowDays)
                .containsExactly(3, 14);
        assertThat(response.search().windowDays()).isEqualTo(14);
        assertThat(response.trend().search()).extracting(AdminDashboardResponse.SearchTrendPoint::windowDays)
                .containsExactly(3, 14);

        verify(adminDashboardReadRepository, times(2)).fetchCollectTrend(org.mockito.ArgumentMatchers.any());
        verify(adminDashboardReadRepository, times(2)).fetchRecommendationTrend(org.mockito.ArgumentMatchers.any());
        verify(adminDashboardReadRepository, times(2)).fetchSearchTrend(org.mockito.ArgumentMatchers.any());
    }
}
