package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminCollectFailureResponse;
import com.example.welfare.admin.dashboard.dto.AdminDashboardResponse;
import com.example.welfare.admin.dashboard.dto.AdminRecommendationBreakdownResponse;
import com.example.welfare.admin.dashboard.dto.AdminSearchFailureResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock
    private AdminDashboardSummaryService adminDashboardSummaryService;

    @Mock
    private AdminDashboardSearchService adminDashboardSearchService;

    @Mock
    private AdminDashboardRecommendationService adminDashboardRecommendationService;

    @Mock
    private AdminDashboardCollectService adminDashboardCollectService;

    @InjectMocks
    private AdminDashboardService adminDashboardService;

    @Test
    @DisplayName("wrapper는 summary 요청을 summary service로 위임한다")
    void delegatesSummary() {
        AdminDashboardResponse response = new AdminDashboardResponse(
                LocalDateTime.of(2026, 5, 4, 10, 0),
                null,
                null,
                null,
                null,
                null,
                null
        );
        given(adminDashboardSummaryService.getSummary(14, List.of(3, 14))).willReturn(response);

        AdminDashboardResponse result = adminDashboardService.getSummary(14, List.of(3, 14));

        assertThat(result).isSameAs(response);
        verify(adminDashboardSummaryService).getSummary(14, List.of(3, 14));
    }

    @Test
    @DisplayName("wrapper는 search failure 요청을 search service로 위임한다")
    void delegatesSearchFailures() {
        AdminSearchFailureResponse response = new AdminSearchFailureResponse(
                LocalDateTime.of(2026, 5, 4, 10, 0),
                14,
                3,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        given(adminDashboardSearchService.getSearchFailures(14, 5)).willReturn(response);

        AdminSearchFailureResponse result = adminDashboardService.getSearchFailures(14, 5);

        assertThat(result).isSameAs(response);
        verify(adminDashboardSearchService).getSearchFailures(14, 5);
    }

    @Test
    @DisplayName("wrapper는 recommendation breakdown 요청을 recommendation service로 위임한다")
    void delegatesRecommendationBreakdowns() {
        AdminRecommendationBreakdownResponse response = new AdminRecommendationBreakdownResponse(
                LocalDateTime.of(2026, 5, 4, 10, 0),
                14,
                30,
                9,
                6,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        given(adminDashboardRecommendationService.getRecommendationBreakdowns(14, 5)).willReturn(response);

        AdminRecommendationBreakdownResponse result = adminDashboardService.getRecommendationBreakdowns(14, 5);

        assertThat(result).isSameAs(response);
        verify(adminDashboardRecommendationService).getRecommendationBreakdowns(14, 5);
    }

    @Test
    @DisplayName("wrapper는 collect failure 요청을 collect service로 위임한다")
    void delegatesCollectFailures() {
        AdminCollectFailureResponse response = new AdminCollectFailureResponse(
                LocalDateTime.of(2026, 5, 4, 10, 0),
                14,
                6,
                2,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        given(adminDashboardCollectService.getCollectFailures(14, 5)).willReturn(response);

        AdminCollectFailureResponse result = adminDashboardService.getCollectFailures(14, 5);

        assertThat(result).isSameAs(response);
        verify(adminDashboardCollectService).getCollectFailures(14, 5);
    }
}
