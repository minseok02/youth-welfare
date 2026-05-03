package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminRecommendationBreakdownResponse;
import com.example.welfare.admin.dashboard.dto.AdminSearchFailureResponse;
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
        given(adminDashboardReadRepository.fetchTopZeroResultSearchKeywords(org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of(
                        new AdminDashboardReadRepository.SearchKeywordSnapshotRow("대출", 4),
                        new AdminDashboardReadRepository.SearchKeywordSnapshotRow("월세", 2)
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
        given(adminDashboardReadRepository.fetchTopZeroResultSearchKeywords(org.mockito.ArgumentMatchers.any()))
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
        assertThat(response.recommendation().nextWeightKey()).isEqualTo("GROWTH");
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

    @Test
    @DisplayName("검색 실패 상세는 zero-result 키워드/지역/필터 패턴/샘플을 조합한다")
    void getSearchFailuresBuildsResponse() {
        given(adminDashboardReadRepository.fetchSearchSummary(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).willReturn(new AdminDashboardReadRepository.SearchSummaryRow(21, 88, 13, 43, new BigDecimal("6.375")));
        given(adminDashboardReadRepository.fetchTopZeroResultSearchKeywords(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(3)
        )).willReturn(List.of(
                new AdminDashboardReadRepository.SearchKeywordSnapshotRow("대출", 4),
                new AdminDashboardReadRepository.SearchKeywordSnapshotRow("월세", 2)
        ));
        given(adminDashboardReadRepository.fetchTopZeroResultRegions(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(3)
        )).willReturn(List.of(
                new AdminDashboardReadRepository.SearchRegionSnapshotRow("서울", "관악구", 3)
        ));
        given(adminDashboardReadRepository.fetchTopZeroResultFilterPatterns(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(3)
        )).willReturn(List.of(
                new AdminDashboardReadRepository.SearchFilterPatternSnapshotRow(
                        "UNEMPLOYED",
                        "HOUSING",
                        "YOUTH",
                        Boolean.TRUE,
                        false,
                        "LATEST",
                        5
                )
        ));
        given(adminDashboardReadRepository.fetchRecentZeroResultSearchSamples(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(3)
        )).willReturn(List.of(
                new AdminDashboardReadRepository.SearchFailureSampleRow(
                        "대출",
                        "서울",
                        "관악구",
                        "UNEMPLOYED",
                        "HOUSING",
                        "YOUTH",
                        Boolean.TRUE,
                        false,
                        "LATEST",
                        LocalDateTime.of(2026, 5, 3, 9, 15)
                )
        ));

        AdminSearchFailureResponse response = adminDashboardService.getSearchFailures(14, 3);

        assertThat(response.windowDays()).isEqualTo(14);
        assertThat(response.totalZeroResultSearches()).isEqualTo(13);
        assertThat(response.zeroResultKeywords()).extracting(AdminSearchFailureResponse.KeywordCount::keyword)
                .containsExactly("대출", "월세");
        assertThat(response.zeroResultRegions()).extracting(AdminSearchFailureResponse.RegionCount::sido, AdminSearchFailureResponse.RegionCount::sgg)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("서울", "관악구"));
        assertThat(response.zeroResultFilterPatterns()).singleElement().satisfies(pattern -> {
            assertThat(pattern.statusFilter()).isEqualTo("UNEMPLOYED");
            assertThat(pattern.category()).isEqualTo("HOUSING");
            assertThat(pattern.sourceType()).isEqualTo("YOUTH");
            assertThat(pattern.onlineApply()).isTrue();
            assertThat(pattern.includeClosed()).isFalse();
            assertThat(pattern.sortKey()).isEqualTo("LATEST");
            assertThat(pattern.searchCount()).isEqualTo(5);
        });
        assertThat(response.recentSamples()).singleElement().satisfies(sample -> {
            assertThat(sample.keyword()).isEqualTo("대출");
            assertThat(sample.sido()).isEqualTo("서울");
            assertThat(sample.sgg()).isEqualTo("관악구");
            assertThat(sample.searchedAt()).isEqualTo(LocalDateTime.of(2026, 5, 3, 9, 15));
        });
    }

    @Test
    @DisplayName("추천 breakdown은 source/category/weight/fallback sample/clicked sample을 조합한다")
    void getRecommendationBreakdownsBuildsResponse() {
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
        given(adminDashboardReadRepository.fetchRecommendationSourceBreakdowns(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(3)
        )).willReturn(List.of(
                new AdminDashboardReadRepository.RecommendationSourceBreakdownRow("YOUTH", 18, 6, 3)
        ));
        given(adminDashboardReadRepository.fetchRecommendationCategoryBreakdowns(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(3)
        )).willReturn(List.of(
                new AdminDashboardReadRepository.RecommendationCategoryBreakdownRow("HOUSING", 10, 4, 1)
        ));
        given(adminDashboardReadRepository.fetchRecommendationWeightBreakdowns(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(3)
        )).willReturn(List.of(
                new AdminDashboardReadRepository.RecommendationWeightBreakdownRow(
                        "GROWTH",
                        new BigDecimal("0.60"),
                        new BigDecimal("0.40"),
                        12,
                        3,
                        2
                )
        ));
        given(adminDashboardReadRepository.fetchRecentFallbackRecommendationSamples(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(3)
        )).willReturn(List.of(
                new AdminDashboardReadRepository.RecommendationSampleRow(
                        101L,
                        501L,
                        "청년 월세 지원",
                        "YOUTH",
                        "HOUSING",
                        new BigDecimal("0.75231"),
                        true,
                        false,
                        LocalDateTime.of(2026, 5, 3, 9, 0),
                        null
                )
        ));
        given(adminDashboardReadRepository.fetchRecentClickedRecommendationSamples(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(3)
        )).willReturn(List.of(
                new AdminDashboardReadRepository.RecommendationSampleRow(
                        102L,
                        502L,
                        "청년 전세 지원",
                        "BOKJIRO_LOCAL",
                        "HOUSING",
                        new BigDecimal("0.88123"),
                        false,
                        true,
                        LocalDateTime.of(2026, 5, 3, 8, 0),
                        LocalDateTime.of(2026, 5, 3, 8, 30)
                )
        ));
        given(adminDashboardReadRepository.fetchRecommendationRepeatExposureGroups(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(3)
        )).willReturn(List.of(
                new AdminDashboardReadRepository.RecommendationRepeatExposureGroupRow(
                        "user-key-1",
                        501L,
                        "청년 월세 지원",
                        "YOUTH",
                        "HOUSING",
                        3,
                        1,
                        1,
                        LocalDateTime.of(2026, 5, 1, 8, 0),
                        LocalDateTime.of(2026, 5, 3, 9, 0),
                        LocalDateTime.of(2026, 5, 3, 9, 10)
                )
        ));

        AdminRecommendationBreakdownResponse response = adminDashboardService.getRecommendationBreakdowns(14, 3);

        assertThat(response.windowDays()).isEqualTo(14);
        assertThat(response.totalLogs()).isEqualTo(30);
        assertThat(response.clickedLogs()).isEqualTo(9);
        assertThat(response.fallbackLogs()).isEqualTo(6);
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
        assertThat(response.recentFallbackSamples()).singleElement().satisfies(sample -> {
            assertThat(sample.logId()).isEqualTo(101L);
            assertThat(sample.title()).isEqualTo("청년 월세 지원");
            assertThat(sample.fallback()).isTrue();
        });
        assertThat(response.recentClickedSamples()).singleElement().satisfies(sample -> {
            assertThat(sample.logId()).isEqualTo(102L);
            assertThat(sample.title()).isEqualTo("청년 전세 지원");
            assertThat(sample.clicked()).isTrue();
            assertThat(sample.clickedAt()).isEqualTo(LocalDateTime.of(2026, 5, 3, 8, 30));
        });
        assertThat(response.repeatExposureGroups()).singleElement().satisfies(group -> {
            assertThat(group.userKey()).isEqualTo("user-key-1");
            assertThat(group.serviceId()).isEqualTo(501L);
            assertThat(group.title()).isEqualTo("청년 월세 지원");
            assertThat(group.exposureCount()).isEqualTo(3);
            assertThat(group.clickedCount()).isEqualTo(1);
            assertThat(group.fallbackCount()).isEqualTo(1);
            assertThat(group.firstSentAt()).isEqualTo(LocalDateTime.of(2026, 5, 1, 8, 0));
            assertThat(group.latestSentAt()).isEqualTo(LocalDateTime.of(2026, 5, 3, 9, 0));
            assertThat(group.latestClickedAt()).isEqualTo(LocalDateTime.of(2026, 5, 3, 9, 10));
        });
    }
}
