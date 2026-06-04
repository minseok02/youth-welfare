package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminCollectFailureResponse;
import com.example.welfare.admin.dashboard.dto.AdminDashboardAttentionResponse;
import com.example.welfare.admin.dashboard.dto.AdminPolicyErrorReportResponse;
import com.example.welfare.admin.dashboard.dto.AdminSupportInquiryResponse;
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
    private final AdminPolicyErrorReportService adminPolicyErrorReportService;
    private final AdminSupportInquiryService adminSupportInquiryService;

    public AdminDashboardAttentionResponse getAttentionFeed() {
        AdminCollectFailureResponse collectFailures = adminDashboardCollectService.getCollectFailures(null, null);
        AdminUserProfileStandardCodeCoverageResponse standardCodeCoverage =
                adminDashboardUserProfileService.getUserProfileStandardCodeCoverage();
        AdminWrapperObservationResponse wrapperObservation =
                adminDashboardWrapperObservationService.getLatestObservation();
        AdminPolicyErrorReportResponse policyErrorReports = adminPolicyErrorReportService.getRecentReports(1);
        AdminSupportInquiryResponse supportInquiries = adminSupportInquiryService.getRecentInquiries(1);

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
        if (policyErrorReports.openCount() > 0) {
            String headline = policyErrorReports.recentReports().isEmpty()
                    ? "최근 제보 있음"
                    : policyErrorReports.recentReports().get(0).policyTitle();
            items.add(new AdminDashboardAttentionResponse.AttentionItem(
                    "policy-error-report-backlog",
                    "warning",
                    "정책 오류 제보 backlog",
                    "%d건 열림 · 최근 24시간 %d건 · 최근 정책: %s".formatted(
                            policyErrorReports.openCount(),
                            policyErrorReports.recentOpenCount24h(),
                            headline
                    ),
                    "admin-policy-error-reports",
                    "policy-error-reports"
            ));
        }
        if (supportInquiries.openCount() > 0) {
            String headline = supportInquiries.recentInquiries().isEmpty()
                    ? "최근 문의 있음"
                    : supportInquiries.recentInquiries().get(0).categoryLabel();
            items.add(new AdminDashboardAttentionResponse.AttentionItem(
                    "support-inquiry-backlog",
                    "info",
                    "서비스 문의 backlog",
                    "%d건 열림 · 최근 24시간 %d건 · 최근 문의 유형: %s".formatted(
                            supportInquiries.openCount(),
                            supportInquiries.recentOpenCount24h(),
                            headline
                    ),
                    "admin-support-inquiries",
                    "support-inquiries"
            ));
        }
        return new AdminDashboardAttentionResponse(
                LocalDateTime.now(),
                items.size(),
                items
        );
    }
}
