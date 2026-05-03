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
        given(adminDashboardReadRepository.fetchNotificationSummary(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).willReturn(new AdminDashboardReadRepository.NotificationSummaryRow(4, 1, 14, 2));
        given(adminDashboardReadRepository.fetchSearchSummary(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).willReturn(new AdminDashboardReadRepository.SearchSummaryRow(21, 88, 13, 43, new BigDecimal("6.375")));
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
        assertThat(response.collect().latestFailuresLast7d()).hasSize(1);
        assertThat(response.collect().latestFailuresLast7d().get(0).jobName()).isEqualTo("BOKJIRO_LOCAL");
        assertThat(response.recommendation().activeWeightKey()).isEqualTo("GROWTH");
        assertThat(response.recommendation().activeRuleWeight()).isEqualByComparingTo("0.60");
        assertThat(response.recommendation().activeAiWeight()).isEqualByComparingTo("0.40");
        assertThat(response.recommendation().totalLogs()).isEqualTo(250);
        assertThat(response.recommendation().sentLast7d()).isEqualTo(30);
        assertThat(response.recommendation().latestClickedAt()).isEqualTo(LocalDateTime.of(2026, 5, 2, 8, 45));
        assertThat(response.recommendation().clickThroughRateLast7d()).isEqualByComparingTo("0.3000");
        assertThat(response.recommendation().fallbackRateLast7d()).isEqualByComparingTo("0.2000");
        assertThat(response.recommendation().weightBucketsLast7d()).extracting(AdminDashboardResponse.RecommendationWeightSnapshot::weightKey)
                .containsExactly("GROWTH", "COLD_START");
        assertThat(response.notification().failedLast24h()).isEqualTo(1);
        assertThat(response.search().searchesLast7d()).isEqualTo(88);
        assertThat(response.search().zeroResultSearchesLast7d()).isEqualTo(13);
        assertThat(response.search().averageResultCountLast7d()).isEqualByComparingTo("6.38");
        assertThat(response.search().topKeywordsLast7d()).extracting(AdminDashboardResponse.SearchKeywordSnapshot::keyword)
                .containsExactly("월세", "주거");
        assertThat(response.userPiiSync().failedCount()).isEqualTo(1);
        assertThat(response.userPiiSync().latestSyncedAt()).isEqualTo(LocalDateTime.of(2026, 5, 2, 7, 45));
    }
}
