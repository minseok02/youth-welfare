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
