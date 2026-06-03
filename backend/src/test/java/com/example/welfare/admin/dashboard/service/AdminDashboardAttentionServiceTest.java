package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminCollectFailureResponse;
import com.example.welfare.admin.dashboard.dto.AdminUserProfileStandardCodeCoverageResponse;
import com.example.welfare.admin.dashboard.dto.AdminWrapperObservationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AdminDashboardAttentionServiceTest {

    private final AdminDashboardCollectService collectService = mock(AdminDashboardCollectService.class);
    private final AdminDashboardUserProfileService userProfileService = mock(AdminDashboardUserProfileService.class);
    private final AdminDashboardWrapperObservationService wrapperObservationService = mock(AdminDashboardWrapperObservationService.class);
    private final AdminDashboardAttentionService service = new AdminDashboardAttentionService(
            collectService,
            userProfileService,
            wrapperObservationService
    );

    @Test
    @DisplayName("collect drift, 표준코드 backlog, wrapper warning을 attention feed로 묶는다")
    void buildsAttentionFeed() {
        given(collectService.getCollectFailures(null, null)).willReturn(new AdminCollectFailureResponse(
                LocalDateTime.of(2026, 6, 3, 14, 0),
                14,
                2,
                1,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new AdminCollectFailureResponse.CircuitStatus("gov24", true, 1200, LocalDateTime.of(2026, 6, 3, 14, 5))),
                List.of()
        ));
        given(userProfileService.getUserProfileStandardCodeCoverage()).willReturn(new AdminUserProfileStandardCodeCoverageResponse(
                LocalDateTime.of(2026, 6, 3, 14, 0),
                845,
                845,
                0,
                52,
                0,
                793,
                52,
                20,
                17,
                16,
                52,
                0,
                793,
                0,
                0,
                0,
                0
        ));
        given(wrapperObservationService.getLatestObservation()).willReturn(new AdminWrapperObservationResponse(
                true,
                LocalDateTime.of(2026, 6, 3, 13, 20),
                "/tmp/active-baseline-suite/latest-active-baseline-summary.txt",
                "passed",
                "passed",
                "ok",
                1,
                1,
                "standard-code-backlog",
                "표준코드 입력 backlog",
                52,
                793,
                "passed",
                2,
                4,
                54.0,
                true,
                LocalDateTime.of(2026, 6, 3, 13, 21),
                "/tmp/current-priority-suite/latest-current-priority-summary.txt",
                "passed",
                true,
                "ok",
                1,
                1,
                "standard-code-backlog",
                "표준코드 입력 backlog",
                52,
                801,
                "failed",
                2,
                4,
                4,
                54.0,
                true,
                LocalDateTime.of(2026, 6, 3, 13, 19),
                "/tmp/current-priority-suite/20260603T041240Z/current-priority-summary.txt",
                796,
                5,
                "5 증가",
                "passed",
                true,
                "passed -> failed",
                new AdminWrapperObservationResponse.SnapshotAlert(
                        "warning",
                        "운영 주시 포인트",
                        "표준코드 미입력 5 증가, priority 관측 passed -> failed"
                )
        ));

        var response = service.getAttentionFeed();

        assertThat(response.itemCount()).isEqualTo(3);
        assertThat(response.items()).extracting("key")
                .containsExactly("collect-drift", "standard-code-backlog", "wrapper-warning");
    }
}
