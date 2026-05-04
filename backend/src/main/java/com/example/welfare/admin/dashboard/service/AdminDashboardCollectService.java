package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminCollectFailureResponse;
import com.example.welfare.admin.dashboard.repository.AdminDashboardCollectReadRepository;
import com.example.welfare.admin.dashboard.repository.AdminDashboardReadRows;
import com.example.welfare.collect.entity.ApiSyncLog;
import com.example.welfare.collect.service.CollectRuntimeStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardCollectService {

    private final AdminDashboardCollectReadRepository adminDashboardCollectReadRepository;
    private final CollectRuntimeStatusService collectRuntimeStatusService;

    public AdminCollectFailureResponse getCollectFailures(Integer requestedSummaryWindowDays, Integer requestedLimit) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        int summaryWindowDays = AdminDashboardQueryPolicy.resolveSummaryWindowDays(requestedSummaryWindowDays);
        int patternLimit = AdminDashboardQueryPolicy.resolveCollectFailurePatternLimit(requestedLimit);
        java.time.LocalDateTime summaryWindowAgo = now.minusDays(summaryWindowDays);

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
                buildCollectJobStreaks(patternLimit),
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
                        .toList()
        );
    }

    private List<AdminCollectFailureResponse.JobStreak> buildCollectJobStreaks(int limit) {
        Map<String, List<AdminDashboardReadRows.CollectJobRunRow>> runsByJob = adminDashboardCollectReadRepository
                .fetchRecentCollectJobRuns(AdminDashboardQueryPolicy.COLLECT_STREAK_RUN_LIMIT)
                .stream()
                .collect(Collectors.groupingBy(
                        AdminDashboardReadRows.CollectJobRunRow::jobName,
                        java.util.LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<AdminCollectFailureResponse.JobStreak> streaks = new ArrayList<>();
        for (Map.Entry<String, List<AdminDashboardReadRows.CollectJobRunRow>> entry : runsByJob.entrySet()) {
            List<AdminDashboardReadRows.CollectJobRunRow> runs = entry.getValue();
            if (runs.isEmpty()) {
                continue;
            }

            ApiSyncLog.SyncStatus latestStatus = ApiSyncLog.SyncStatus.valueOf(runs.get(0).status());
            if (latestStatus != ApiSyncLog.SyncStatus.FAILED && latestStatus != ApiSyncLog.SyncStatus.PARTIAL_SUCCESS) {
                continue;
            }

            long streakCount = 0;
            for (AdminDashboardReadRows.CollectJobRunRow run : runs) {
                ApiSyncLog.SyncStatus status = ApiSyncLog.SyncStatus.valueOf(run.status());
                if (status != latestStatus) {
                    break;
                }
                streakCount++;
            }

            streaks.add(new AdminCollectFailureResponse.JobStreak(
                    entry.getKey(),
                    latestStatus.name(),
                    streakCount,
                    runs.get(0).startedAt()
            ));
        }

        return streaks.stream()
                .sorted(java.util.Comparator
                        .comparingLong(AdminCollectFailureResponse.JobStreak::streakCount).reversed()
                        .thenComparing(
                                AdminCollectFailureResponse.JobStreak::latestStartedAt,
                                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())
                        )
                        .thenComparing(AdminCollectFailureResponse.JobStreak::jobName))
                .limit(limit)
                .toList();
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
}
