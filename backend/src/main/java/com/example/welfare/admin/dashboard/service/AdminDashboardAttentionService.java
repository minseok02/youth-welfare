package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminCollectFailureResponse;
import com.example.welfare.admin.dashboard.dto.AdminDashboardAttentionResponse;
import com.example.welfare.admin.dashboard.dto.AdminDashboardResponse;
import com.example.welfare.admin.dashboard.dto.AdminPolicyDuplicateGroupResponse;
import com.example.welfare.admin.dashboard.dto.AdminPolicyErrorReportResponse;
import com.example.welfare.admin.dashboard.dto.AdminPolicyLinkReviewResponse;
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
    private final AdminDashboardSummaryService adminDashboardSummaryService;
    private final AdminDashboardUserProfileService adminDashboardUserProfileService;
    private final AdminDashboardWrapperObservationService adminDashboardWrapperObservationService;
    private final AdminPolicyDuplicateGroupService adminPolicyDuplicateGroupService;
    private final AdminPolicyErrorReportService adminPolicyErrorReportService;
    private final AdminPolicyLinkReviewService adminPolicyLinkReviewService;
    private final AdminNotificationStaleTargetService adminNotificationStaleTargetService;
    private final AdminSupportInquiryService adminSupportInquiryService;

    public AdminDashboardAttentionResponse getAttentionFeed() {
        AdminCollectFailureResponse collectFailures = adminDashboardCollectService.getCollectFailures(null, null);
        AdminUserProfileStandardCodeCoverageResponse standardCodeCoverage =
                adminDashboardUserProfileService.getUserProfileStandardCodeCoverage();
        var dashboardSummary = adminDashboardSummaryService.getSummary(null, null);
        AdminWrapperObservationResponse wrapperObservation =
                adminDashboardWrapperObservationService.getLatestObservation();
        AdminPolicyDuplicateGroupResponse duplicateGroups = adminPolicyDuplicateGroupService.getRecentGroups(1);
        AdminPolicyErrorReportResponse policyErrorReports = adminPolicyErrorReportService.getRecentReports(1);
        AdminPolicyLinkReviewResponse policyLinkReviews = adminPolicyLinkReviewService.getRecentReviews(1);
        var staleNotificationTargets = adminNotificationStaleTargetService.getRecentTargets(1, 14);
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
                    "collect",
                    "수집 실패 샘플과 열린 circuit을 확인하고, 같은 source의 반복 실패면 source별 수동 재수집과 회로 상태를 점검합니다."
            ));
        }
        if (standardCodeCoverage.usersMissingAllStandardCodes() > 0) {
            boolean standardCodeNeedsOperatorAction = standardCodeCoverage.safeReconcileCandidateRows() > 0
                    || standardCodeCoverage.conflictingValueGapRows() > 0;
            items.add(new AdminDashboardAttentionResponse.AttentionItem(
                    "standard-code-backlog",
                    standardCodeNeedsOperatorAction ? "warning" : "info",
                    "표준코드 입력 backlog",
                    "%d명이 주거·복지 표준코드 4개를 모두 비워둔 상태입니다. 자동 보정 후보 %d건, 충돌 gap %d건."
                            .formatted(
                                    standardCodeCoverage.usersMissingAllStandardCodes(),
                                    standardCodeCoverage.safeReconcileCandidateRows(),
                                    standardCodeCoverage.conflictingValueGapRows()
                            ),
                    "admin-standard-code-coverage",
                    "user-profile-standard-codes",
                    standardCodeNextAction(standardCodeNeedsOperatorAction)
            ));
        }
        if (dashboardSummary.notification().unreadAlerts() > 0
                || dashboardSummary.notification().retryableFailedNotifications() > 0
                || dashboardSummary.notification().terminalFailedNotifications() > 0) {
            items.add(new AdminDashboardAttentionResponse.AttentionItem(
                    "notification-backlog",
                    dashboardSummary.notification().terminalFailedNotifications() > 0 ? "warning" : "info",
                    "알림 backlog 확인",
                    "안 읽은 알림 %d건 · stale 7일 %d건 · stale 14일 %d건 · 재시도 대기 %d건 · 종결 실패 %d건".formatted(
                            dashboardSummary.notification().unreadAlerts(),
                            dashboardSummary.notification().staleUnread7d(),
                            dashboardSummary.notification().staleUnread14d(),
                            dashboardSummary.notification().retryableFailedNotifications(),
                            dashboardSummary.notification().terminalFailedNotifications()
                    ),
                    "admin-notification-summary",
                    "notification",
                    notificationBacklogNextAction(dashboardSummary.notification())
            ));
        }
        if (staleNotificationTargets.staleGroupCount() > 0) {
            String headline = staleNotificationTargets.recentTargets().isEmpty()
                    ? "대표 stale target 없음"
                    : staleNotificationTargets.recentTargets().get(0).deeplinkUrl();
            items.add(new AdminDashboardAttentionResponse.AttentionItem(
                    "notification-stale-backlog",
                    "warning",
                    "stale 알림 target backlog",
                    "%d건 stale unread · %d묶음 · 대표 target: %s".formatted(
                            staleNotificationTargets.staleRowCount(),
                            staleNotificationTargets.staleGroupCount(),
                            headline
                    ),
                    "admin-notification-stale-targets",
                    "notification",
                    "대표 deeplink target을 확인한 뒤 같은 cluster만 bounded hide 처리하고 unread 총량 변화를 재확인합니다."
            ));
        }
        if (wrapperObservation.promotedAlert() != null && "warning".equals(wrapperObservation.promotedAlert().severity())) {
            items.add(new AdminDashboardAttentionResponse.AttentionItem(
                    "wrapper-warning",
                    wrapperObservation.promotedAlert().severity(),
                    wrapperObservation.promotedAlert().title(),
                    wrapperObservation.promotedAlert().message(),
                    "admin-wrapper-observation",
                    "wrapper-observation",
                    "current priority와 active baseline summary를 비교하고 표준코드/추천 관측 변화 원인을 확인합니다."
            ));
        }
        if (duplicateGroups.openGroupCount() > 0) {
            String headline = duplicateGroups.recentGroups().isEmpty()
                    ? "중복 묶음 있음"
                    : duplicateGroups.recentGroups().get(0).title();
            items.add(new AdminDashboardAttentionResponse.AttentionItem(
                    "policy-duplicate-backlog",
                    "warning",
                    "정책 중복 review backlog",
                    "%d묶음 열림 · 최근 24시간 %d묶음 · 관련 row %d건 · 대표 정책: %s".formatted(
                            duplicateGroups.openGroupCount(),
                            duplicateGroups.recentOpenGroupCount24h(),
                            duplicateGroups.openDuplicateRowCount(),
                            headline
                    ),
                    "admin-policy-duplicate-groups",
                    "policy-duplicate-groups",
                    "duplicate count가 큰 묶음부터 false positive 여부를 판단해 duplicate/link 우선순위를 기록합니다."
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
                    "policy-error-reports",
                    "최근 제보의 정책 원문과 링크를 확인하고 보정 또는 REVIEWED 메모를 남깁니다."
            ));
        }
        if (policyLinkReviews.openCount() > 0) {
            String headline = policyLinkReviews.recentReviews().isEmpty()
                    ? "최근 링크 review 있음"
                    : policyLinkReviews.recentReviews().get(0).policyTitle();
            items.add(new AdminDashboardAttentionResponse.AttentionItem(
                    "policy-link-review-backlog",
                    "warning",
                    "정책 링크 review backlog",
                    "%d건 열림 · 최근 24시간 %d건 · 최근 정책: %s".formatted(
                            policyLinkReviews.openCount(),
                            policyLinkReviews.recentOpenCount24h(),
                            headline
                    ),
                    "admin-policy-link-reviews",
                    "policy-link-reviews",
                    "bucket별 대표 링크를 열어 공식 상세 URL 여부를 확인하고 REVIEWED 메모를 남깁니다."
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
                    "support-inquiries",
                    "최근 문의 유형과 route를 보고 재현/응답 여부를 메모한 뒤 처리완료로 닫습니다."
            ));
        }
        return new AdminDashboardAttentionResponse(
                LocalDateTime.now(),
                items.size(),
                items
        );
    }

    private String standardCodeNextAction(boolean needsOperatorAction) {
        if (needsOperatorAction) {
            return "자동 보정 후보와 충돌 gap을 먼저 검토하고 안전 후보만 reconcile합니다.";
        }
        return "자동 보정 후보가 없으므로 사용자 입력 유도/관찰 대상으로 유지합니다.";
    }

    private String notificationBacklogNextAction(AdminDashboardResponse.NotificationSection notification) {
        if (notification.terminalFailedNotifications() > 0 || notification.retryableFailedNotifications() > 0) {
            return "attempt 실패 breakdown과 최근 실패 endpoint를 확인한 뒤 재시도/구독 비활성 원인을 분리합니다.";
        }
        if (notification.staleUnread14d() > 0) {
            return "14일 이상 stale target cluster를 확인하고 숨김 후보만 bounded 처리합니다.";
        }
        return "broad unread 규모만 관찰하고 stale target cluster가 생길 때 정리합니다.";
    }
}
