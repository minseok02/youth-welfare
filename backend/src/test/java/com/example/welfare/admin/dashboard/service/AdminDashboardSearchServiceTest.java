package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminSearchFailureResponse;
import com.example.welfare.admin.dashboard.repository.AdminDashboardReadRows;
import com.example.welfare.admin.dashboard.repository.AdminDashboardSearchReadRepository;
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
class AdminDashboardSearchServiceTest {

    @Mock
    private AdminDashboardSearchReadRepository adminDashboardSearchReadRepository;

    @InjectMocks
    private AdminDashboardSearchService adminDashboardSearchService;

    @Test
    @DisplayName("검색 실패 상세는 zero-result 키워드/지역/필터 패턴/샘플을 조합한다")
    void getSearchFailuresBuildsResponse() {
        given(adminDashboardSearchReadRepository.fetchSearchSummary(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).willReturn(new AdminDashboardReadRows.SearchSummaryRow(21, 88, 13, 43, new BigDecimal("6.375")));
        given(adminDashboardSearchReadRepository.fetchTopZeroResultSearchKeywords(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(3)
        )).willReturn(List.of(
                new AdminDashboardReadRows.SearchKeywordSnapshotRow("대출", 4),
                new AdminDashboardReadRows.SearchKeywordSnapshotRow("월세", 2)
        ));
        given(adminDashboardSearchReadRepository.fetchTopZeroResultRegions(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(3)
        )).willReturn(List.of(
                new AdminDashboardReadRows.SearchRegionSnapshotRow("서울", "관악구", 3)
        ));
        given(adminDashboardSearchReadRepository.fetchTopZeroResultFilterPatterns(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(3)
        )).willReturn(List.of(
                new AdminDashboardReadRows.SearchFilterPatternSnapshotRow(
                        "UNEMPLOYED",
                        "HOUSING",
                        "YOUTH",
                        Boolean.TRUE,
                        false,
                        "LATEST",
                        5
                )
        ));
        given(adminDashboardSearchReadRepository.fetchRecentZeroResultSearchSamples(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(3)
        )).willReturn(List.of(
                new AdminDashboardReadRows.SearchFailureSampleRow(
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
        given(adminDashboardSearchReadRepository.fetchZeroResultRetryGroups(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(3)
        )).willReturn(List.of(
                new AdminDashboardReadRows.SearchRetryGroupRow(
                        "USER_KEY",
                        "user-key-1",
                        "대출",
                        "서울",
                        "관악구",
                        "UNEMPLOYED",
                        "HOUSING",
                        "YOUTH",
                        Boolean.TRUE,
                        false,
                        "LATEST",
                        3,
                        LocalDateTime.of(2026, 5, 3, 8, 30),
                        LocalDateTime.of(2026, 5, 3, 9, 15)
                )
        ));
        given(adminDashboardSearchReadRepository.fetchRecoveredSearchGroups(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(3)
        )).willReturn(List.of(
                new AdminDashboardReadRows.RecoveredSearchGroupRow(
                        "USER_KEY",
                        "user-key-1",
                        "대출",
                        "서울",
                        "관악구",
                        "UNEMPLOYED",
                        "HOUSING",
                        "YOUTH",
                        Boolean.TRUE,
                        false,
                        "LATEST",
                        2,
                        1,
                        LocalDateTime.of(2026, 5, 3, 9, 25)
                )
        ));

        AdminSearchFailureResponse response = adminDashboardSearchService.getSearchFailures(14, 3);

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
        assertThat(response.retryGroups()).singleElement().satisfies(group -> {
            assertThat(group.actorType()).isEqualTo("USER_KEY");
            assertThat(group.actorKey()).isEqualTo("user-key-1");
            assertThat(group.keyword()).isEqualTo("대출");
            assertThat(group.retryCount()).isEqualTo(3);
            assertThat(group.firstSearchedAt()).isEqualTo(LocalDateTime.of(2026, 5, 3, 8, 30));
            assertThat(group.latestSearchedAt()).isEqualTo(LocalDateTime.of(2026, 5, 3, 9, 15));
        });
        assertThat(response.recoveredSearchGroups()).singleElement().satisfies(group -> {
            assertThat(group.actorType()).isEqualTo("USER_KEY");
            assertThat(group.actorKey()).isEqualTo("user-key-1");
            assertThat(group.keyword()).isEqualTo("대출");
            assertThat(group.zeroResultCount()).isEqualTo(2);
            assertThat(group.recoveredResultCount()).isEqualTo(1);
            assertThat(group.latestRecoveredAt()).isEqualTo(LocalDateTime.of(2026, 5, 3, 9, 25));
        });
    }
}
