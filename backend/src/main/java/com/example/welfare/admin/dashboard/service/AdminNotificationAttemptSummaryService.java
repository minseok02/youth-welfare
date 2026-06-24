package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminNotificationAttemptSummaryResponse;
import com.example.welfare.admin.dashboard.repository.AdminDashboardNotificationReadRepository;
import com.example.welfare.admin.dashboard.repository.AdminDashboardReadRows;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminNotificationAttemptSummaryService {

    private final AdminDashboardNotificationReadRepository adminDashboardNotificationReadRepository;

    public AdminNotificationAttemptSummaryResponse getSummary(Integer requestedSummaryWindowDays, Integer requestedLimit) {
        LocalDateTime now = LocalDateTime.now();
        int summaryWindowDays = AdminDashboardQueryPolicy.resolveSummaryWindowDays(requestedSummaryWindowDays);
        int limit = AdminDashboardQueryPolicy.resolveRecommendationBreakdownLimit(requestedLimit);
        LocalDateTime windowAgo = now.minusDays(summaryWindowDays);
        AdminDashboardReadRows.NotificationAttemptSummaryRow summary =
                adminDashboardNotificationReadRepository.fetchNotificationAttemptSummary(windowAgo);

        return new AdminNotificationAttemptSummaryResponse(
                now,
                summaryWindowDays,
                summary.totalAttempts(),
                summary.successAttempts(),
                summary.failedAttempts(),
                summary.disabledAttempts(),
                summary.averageDurationMs(),
                summary.latestAttemptAt(),
                adminDashboardNotificationReadRepository.fetchNotificationAttemptBreakdowns(windowAgo, limit).stream()
                        .map(row -> new AdminNotificationAttemptSummaryResponse.Breakdown(
                                row.channel(),
                                row.kind(),
                                row.outcome(),
                                row.attemptCount(),
                                row.averageDurationMs(),
                                row.latestAttemptAt()
                        ))
                        .toList(),
                adminDashboardNotificationReadRepository.fetchRecentNotificationAttemptFailures(windowAgo, limit).stream()
                        .map(row -> new AdminNotificationAttemptSummaryResponse.RecentFailure(
                                row.id(),
                                row.channel(),
                                row.kind(),
                                row.outcome(),
                                row.itemCount(),
                                row.endpointHost(),
                                row.errorType(),
                                row.durationMs(),
                                row.createdAt()
                        ))
                        .toList()
        );
    }
}
