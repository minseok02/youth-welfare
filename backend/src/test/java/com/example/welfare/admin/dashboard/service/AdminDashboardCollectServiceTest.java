package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminCollectFailureResponse;
import com.example.welfare.admin.dashboard.repository.AdminDashboardCollectReadRepository;
import com.example.welfare.admin.dashboard.repository.AdminDashboardReadRows;
import com.example.welfare.collect.service.CollectRuntimeStatusService;
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

@ExtendWith(MockitoExtension.class)
class AdminDashboardCollectServiceTest {

    @Mock
    private AdminDashboardCollectReadRepository adminDashboardCollectReadRepository;

    @Mock
    private CollectRuntimeStatusService collectRuntimeStatusService;

    @InjectMocks
    private AdminDashboardCollectService adminDashboardCollectService;

    @Test
    @DisplayName("collect 실패 상세는 failed/partial 총량, job 분포, error code 분포, 최근 샘플을 조합한다")
    void getCollectFailuresBuildsResponse() {
        given(adminDashboardCollectReadRepository.fetchCollectFailureSummary(org.mockito.ArgumentMatchers.any()))
                .willReturn(new AdminDashboardReadRows.CollectFailureSummaryRow(6, 2));
        given(adminDashboardCollectReadRepository.fetchCollectFailureJobBreakdowns(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(3)
        )).willReturn(List.of(
                new AdminDashboardReadRows.CollectFailureJobBreakdownRow(
                        "BOKJIRO_LOCAL",
                        4,
                        1,
                        LocalDateTime.of(2026, 5, 3, 9, 0)
                )
        ));
        given(adminDashboardCollectReadRepository.fetchCurrentCollectJobStreaks(
                org.mockito.ArgumentMatchers.eq(3)
        )).willReturn(List.of(
                new AdminDashboardReadRows.CollectJobStreakRow(
                        "BOKJIRO_LOCAL",
                        "FAILED",
                        24,
                        LocalDateTime.of(2026, 5, 3, 9, 0)
                )
        ));
        given(adminDashboardCollectReadRepository.fetchCollectFailureErrorCodeBreakdowns(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(3)
        )).willReturn(List.of(
                new AdminDashboardReadRows.CollectFailureErrorCodeBreakdownRow("COL001", 5)
        ));
        given(adminDashboardCollectReadRepository.fetchRecentCollectFailureSamples(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(3)
        )).willReturn(List.of(
                new AdminDashboardReadRows.CollectFailureSampleRow(
                        "BOKJIRO_LOCAL",
                        "FAILED",
                        "COL001",
                        "rate limited",
                        LocalDateTime.of(2026, 5, 3, 9, 0),
                        LocalDateTime.of(2026, 5, 3, 9, 1),
                        0,
                        0,
                        1
                )
        ));
        given(collectRuntimeStatusService.getCircuitStatuses())
                .willReturn(List.of(new CollectRuntimeStatusService.CircuitStatusSnapshot(
                        "BOKJIRO_LOCAL",
                        true,
                        60000L,
                        LocalDateTime.of(2026, 5, 3, 10, 0)
                )));

        AdminCollectFailureResponse response = adminDashboardCollectService.getCollectFailures(14, 3);

        assertThat(response.windowDays()).isEqualTo(14);
        assertThat(response.totalFailedJobs()).isEqualTo(6);
        assertThat(response.totalPartialSuccessJobs()).isEqualTo(2);
        assertThat(response.jobBreakdowns()).singleElement().satisfies(job -> {
            assertThat(job.jobName()).isEqualTo("BOKJIRO_LOCAL");
            assertThat(job.failedCount()).isEqualTo(4);
            assertThat(job.partialSuccessCount()).isEqualTo(1);
            assertThat(job.latestStartedAt()).isEqualTo(LocalDateTime.of(2026, 5, 3, 9, 0));
        });
        assertThat(response.jobStreaks()).singleElement().satisfies(streak -> {
            assertThat(streak.jobName()).isEqualTo("BOKJIRO_LOCAL");
            assertThat(streak.streakStatus()).isEqualTo("FAILED");
            assertThat(streak.streakCount()).isEqualTo(24);
            assertThat(streak.latestStartedAt()).isEqualTo(LocalDateTime.of(2026, 5, 3, 9, 0));
        });
        assertThat(response.errorCodeBreakdowns()).singleElement().satisfies(error -> {
            assertThat(error.errorCode()).isEqualTo("COL001");
            assertThat(error.failedCount()).isEqualTo(5);
        });
        assertThat(response.recentSamples()).singleElement().satisfies(sample -> {
            assertThat(sample.jobName()).isEqualTo("BOKJIRO_LOCAL");
            assertThat(sample.status()).isEqualTo("FAILED");
            assertThat(sample.errorCode()).isEqualTo("COL001");
            assertThat(sample.errorMessage()).isEqualTo("rate limited");
        });
        assertThat(response.circuitStatuses()).singleElement().satisfies(circuit -> {
            assertThat(circuit.circuitKey()).isEqualTo("BOKJIRO_LOCAL");
            assertThat(circuit.open()).isTrue();
            assertThat(circuit.remainingMs()).isEqualTo(60000L);
            assertThat(circuit.openUntil()).isEqualTo(LocalDateTime.of(2026, 5, 3, 10, 0));
        });
    }
}
