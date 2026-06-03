package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminCollectFailureResponse;
import com.example.welfare.admin.dashboard.dto.AdminDashboardAttentionResponse;
import com.example.welfare.admin.dashboard.dto.AdminUserProfileStandardCodeCoverageResponse;
import com.example.welfare.admin.dashboard.dto.AdminWrapperObservationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardAttentionService {

    private final AdminDashboardCollectService adminDashboardCollectService;
    private final AdminDashboardUserProfileService adminDashboardUserProfileService;
    private final AdminDashboardWrapperObservationService adminDashboardWrapperObservationService;

    public AdminDashboardAttentionResponse getAttentionFeed() {
        AdminCollectFailureResponse collectFailures = adminDashboardCollectService.getCollectFailures(null, null);
        AdminUserProfileStandardCodeCoverageResponse standardCodeCoverage =
                adminDashboardUserProfileService.getUserProfileStandardCodeCoverage();
        AdminWrapperObservationResponse wrapperObservation =
                adminDashboardWrapperObservationService.getLatestObservation();

        List<AdminDashboardAttentionResponse.AttentionItem> items = new ArrayList<>();
        if (collectFailures.failedJobsInWindow() > 0
                || collectFailures.partialSuccessJobsInWindow() > 0
                || collectFailures.circuitStatuses().stream().anyMatch(AdminCollectFailureResponse.CircuitStatus::open)) {
            long openCircuitCount = collectFailures.circuitStatuses().stream()
                    .filter(AdminCollectFailureResponse.CircuitStatus::open)
                    .count();
            items.add(new AdminDashboardAttentionResponse.AttentionItem(
                    "collect-drift",
                    "warning",
                    "수집 drift 확인",
                    "실패 %d건, 부분 성공 %d건, 열린 회로 %d개".formatted(
                            collectFailures.failedJobsInWindow(),
                            collectFailures.partialSuccessJobsInWindow(),
                            openCircuitCount
                    ),
                    "admin-collect-triage",
                    "collect"
            ));
        }
        if (standardCodeCoverage.usersMissingAllStandardCodes() > 0) {
            items.add(new AdminDashboardAttentionResponse.AttentionItem(
                    "standard-code-backlog",
                    "warning",
                    "표준코드 입력 backlog",
                    "%d명이 주거·복지 표준코드 4개를 모두 비워둔 상태입니다."
                            .formatted(standardCodeCoverage.usersMissingAllStandardCodes()),
                    "admin-standard-code-coverage",
                    "user-profile-standard-codes"
            ));
        }
        if (wrapperObservation.promotedAlert() != null && "warning".equals(wrapperObservation.promotedAlert().severity())) {
            items.add(new AdminDashboardAttentionResponse.AttentionItem(
                    "wrapper-warning",
                    wrapperObservation.promotedAlert().severity(),
                    wrapperObservation.promotedAlert().title(),
                    wrapperObservation.promotedAlert().message(),
                    "admin-wrapper-observation",
                    "wrapper-observation"
            ));
        }
        return new AdminDashboardAttentionResponse(
                LocalDateTime.now(),
                items.size(),
                items
        );
    }
}
