package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminCollectFailureResponse;
import com.example.welfare.admin.dashboard.repository.AdminDashboardCollectReadRepository;
import com.example.welfare.admin.dashboard.repository.AdminDashboardReadRows;
import com.example.welfare.collect.service.CollectRuntimeLaneCatalog;
import com.example.welfare.collect.service.CollectRuntimeLaneConfigCatalog;
import com.example.welfare.collect.service.CollectRuntimeStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardCollectService {

    private final AdminDashboardCollectReadRepository adminDashboardCollectReadRepository;
    private final CollectRuntimeStatusService collectRuntimeStatusService;
    private final CollectRuntimeLaneConfigCatalog collectRuntimeLaneConfigCatalog;

    public AdminCollectFailureResponse getCollectFailures(Integer requestedSummaryWindowDays, Integer requestedLimit) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        int summaryWindowDays = AdminDashboardQueryPolicy.resolveSummaryWindowDays(requestedSummaryWindowDays);
        int patternLimit = AdminDashboardQueryPolicy.resolveCollectFailurePatternLimit(requestedLimit);
        java.time.LocalDateTime summaryWindowAgo = now.minusDays(summaryWindowDays);
        Map<String, AdminDashboardReadRows.CollectJobSnapshotRow> latestRunsByJobName =
                adminDashboardCollectReadRepository.fetchLatestCollectJobs().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                AdminDashboardReadRows.CollectJobSnapshotRow::jobName,
                                Function.identity()
                        ));

        AdminDashboardReadRows.CollectFailureSummaryRow collectFailureSummary =
                adminDashboardCollectReadRepository.fetchCollectFailureSummary(summaryWindowAgo);

        return new AdminCollectFailureResponse(
                now,
                summaryWindowDays,
                collectFailureSummary.totalFailedJobs(),
                collectFailureSummary.totalPartialSuccessJobs(),
                adminDashboardCollectReadRepository.fetchCollectFailureJobBreakdowns(summaryWindowAgo, patternLimit).stream()
                        .map(row -> new AdminCollectFailureResponse.JobBreakdown(
                                row.jobName(),
                                row.failedCount(),
                                row.partialSuccessCount(),
                                row.latestStartedAt()
                        ))
                        .toList(),
                adminDashboardCollectReadRepository.fetchCurrentCollectJobStreaks(patternLimit).stream()
                        .map(row -> new AdminCollectFailureResponse.JobStreak(
                                row.jobName(),
                                row.streakStatus(),
                                row.streakCount(),
                                row.latestStartedAt()
                        ))
                        .toList(),
                adminDashboardCollectReadRepository.fetchCollectFailureErrorCodeBreakdowns(summaryWindowAgo, patternLimit).stream()
                        .map(row -> new AdminCollectFailureResponse.ErrorCodeBreakdown(
                                row.errorCode(),
                                row.failedCount()
                        ))
                        .toList(),
                adminDashboardCollectReadRepository.fetchRecentCollectFailureSamples(summaryWindowAgo, patternLimit).stream()
                        .map(row -> new AdminCollectFailureResponse.FailureSample(
                                row.jobName(),
                                row.status(),
                                row.errorCode(),
                                row.errorMessage(),
                                row.startedAt(),
                                row.finishedAt(),
                                row.requestedCount(),
                                row.savedCount(),
                                row.failedCount()
                        ))
                        .toList(),
                collectRuntimeStatusService.getCircuitStatuses().stream()
                        .map(this::toCircuitStatus)
                        .toList(),
                CollectRuntimeLaneCatalog.currentLanes().stream()
                        .map(lane -> toCollectLane(lane, latestRunsByJobName.get(lane.laneKey())))
                        .toList()
        );
    }

    private AdminCollectFailureResponse.CircuitStatus toCircuitStatus(
            CollectRuntimeStatusService.CircuitStatusSnapshot status
    ) {
        return new AdminCollectFailureResponse.CircuitStatus(
                status.circuitKey(),
                status.open(),
                status.remainingMs(),
                status.openUntil()
        );
    }

    private AdminCollectFailureResponse.CollectLane toCollectLane(
            CollectRuntimeLaneCatalog.CollectLaneSpec lane,
            AdminDashboardReadRows.CollectJobSnapshotRow latestRun
    ) {
        return new AdminCollectFailureResponse.CollectLane(
                lane.laneKey(),
                lane.label(),
                lane.executionMode(),
                lane.laneType(),
                lane.triggerPath(),
                lane.scheduleLabel(),
                lane.resourceProfile(),
                lane.governanceReason(),
                collectRuntimeLaneConfigCatalog.configEntriesFor(lane.laneKey()).stream()
                        .map(entry -> new AdminCollectFailureResponse.ConfigEntry(entry.label(), entry.value()))
                        .toList(),
                toLatestRun(latestRun)
        );
    }

    private AdminCollectFailureResponse.LatestRun toLatestRun(
            AdminDashboardReadRows.CollectJobSnapshotRow latestRun
    ) {
        if (latestRun == null) {
            return null;
        }
        return new AdminCollectFailureResponse.LatestRun(
                latestRun.status(),
                latestRun.startedAt(),
                latestRun.finishedAt(),
                latestRun.requestedCount(),
                latestRun.savedCount(),
                latestRun.skippedCount(),
                latestRun.filteredCount(),
                latestRun.failedCount()
        );
    }
}
