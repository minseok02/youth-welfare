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
                    "수집 변경 차이 확인",
                    "실패 %d건, 부분 성공 %d건, 열린 회로 %d개".formatted(
                            collectFailures.failedJobsInWindow(),
                            collectFailures.partialSuccessJobsInWindow(),
                            openCircuitCount
                    ),
                    "admin-collect-triage",
                    "collect",
                    "수집 실패 샘플과 열린 회로를 확인하고, 같은 출처의 반복 실패면 출처별 수동 재수집과 회로 상태를 점검합니다."
            ));
        }
        boolean standardCodeNeedsOperatorAction = standardCodeCoverage.safeReconcileCandidateRows() > 0
                || standardCodeCoverage.conflictingValueGapRows() > 0;
        if (standardCodeNeedsOperatorAction) {
            items.add(new AdminDashboardAttentionResponse.AttentionItem(
                    "standard-code-backlog",
                    "warning",
                    "선택 프로필 코드 보정 필요",
                    "자동 보정 후보 %d건, 기본/상세 값 충돌 %d건. 전부 미입력 사용자는 %d명입니다."
                            .formatted(
                                    standardCodeCoverage.safeReconcileCandidateRows(),
                                    standardCodeCoverage.conflictingValueGapRows(),
                                    standardCodeCoverage.usersMissingAllStandardCodes()
                            ),
                    "admin-standard-code-coverage",
                    "user-profile-standard-codes",
                    "자동 보정 후보와 기본/상세 값 충돌을 먼저 검토하고 안전 후보만 보정합니다."
            ));
        }
        boolean notificationNeedsOperatorAction = dashboardSummary.notification().staleUnread14d() > 0
                || dashboardSummary.notification().retryableFailedNotifications() > 0
                || dashboardSummary.notification().terminalFailedNotifications() > 0;
        if (notificationNeedsOperatorAction) {
            items.add(new AdminDashboardAttentionResponse.AttentionItem(
                    "notification-backlog",
                    dashboardSummary.notification().terminalFailedNotifications() > 0 ? "warning" : "info",
                    "알림 대기 항목 확인",
                    "안 읽은 알림 %d건 · 7일 이상 미열람 %d건 · 14일 이상 미열람 %d건 · 재시도 대기 %d건 · 종결 실패 %d건".formatted(
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
                    ? "대표 오래된 알림 대상 없음"
                    : staleNotificationTargets.recentTargets().get(0).deeplinkUrl();
            items.add(new AdminDashboardAttentionResponse.AttentionItem(
                    "notification-stale-backlog",
                    "warning",
                    "오래된 미열람 알림 정리 필요",
                    "오래된 미열람 %d건 · %d묶음 · 대표 대상: %s".formatted(
                            staleNotificationTargets.staleRowCount(),
                            staleNotificationTargets.staleGroupCount(),
                            headline
                    ),
                    "admin-notification-stale-targets",
                    "notification",
                    "대표 이동 경로를 확인한 뒤 같은 묶음만 제한적으로 숨김 처리하고 미열람 총량 변화를 재확인합니다."
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
                    "현재 우선순위와 기준 요약을 비교하고 선택 프로필/추천 관측 변화 원인을 확인합니다."
            ));
        }
        if (duplicateGroups.openGroupCount() > 0) {
            String headline = duplicateGroups.recentGroups().isEmpty()
                    ? "중복 묶음 있음"
                    : duplicateGroups.recentGroups().get(0).title();
            items.add(new AdminDashboardAttentionResponse.AttentionItem(
                    "policy-duplicate-backlog",
                    "warning",
                    "정책 중복 검토 대기",
                    "%d묶음 열림 · 최근 24시간 %d묶음 · 관련 정책 %d건 · 대표 정책: %s".formatted(
                            duplicateGroups.openGroupCount(),
                            duplicateGroups.recentOpenGroupCount24h(),
                            duplicateGroups.openDuplicateRowCount(),
                            headline
                    ),
                    "admin-policy-duplicate-groups",
                    "policy-duplicate-groups",
                    "중복 건수가 큰 묶음부터 오탐 여부를 판단해 중복/링크 검토 우선순위를 기록합니다."
            ));
        }
        if (policyErrorReports.openCount() > 0) {
            String headline = policyErrorReports.recentReports().isEmpty()
                    ? "최근 제보 있음"
                    : policyErrorReports.recentReports().get(0).policyTitle();
            items.add(new AdminDashboardAttentionResponse.AttentionItem(
                    "policy-error-report-backlog",
                    "warning",
                    "정책 오류 제보 대기",
                    "%d건 열림 · 최근 24시간 %d건 · 최근 정책: %s".formatted(
                            policyErrorReports.openCount(),
                            policyErrorReports.recentOpenCount24h(),
                            headline
                    ),
                    "admin-policy-error-reports",
                    "policy-error-reports",
                    "최근 제보의 정책 원문과 링크를 확인하고 보정 또는 처리완료 메모를 남깁니다."
            ));
        }
        if (policyLinkReviews.openCount() > 0) {
            String headline = policyLinkReviews.recentReviews().isEmpty()
                    ? "최근 링크 검토 있음"
                    : policyLinkReviews.recentReviews().get(0).policyTitle();
            items.add(new AdminDashboardAttentionResponse.AttentionItem(
                    "policy-link-review-backlog",
                    "warning",
                    "정책 링크 검토 대기",
                    "%d건 열림 · 최근 24시간 %d건 · 최근 정책: %s".formatted(
                            policyLinkReviews.openCount(),
                            policyLinkReviews.recentOpenCount24h(),
                            headline
                    ),
                    "admin-policy-link-reviews",
                    "policy-link-reviews",
                    "분류별 대표 링크를 열어 공식 상세 링크 여부를 확인하고 처리완료 메모를 남깁니다."
            ));
        }
        if (supportInquiries.openCount() > 0) {
            String headline = supportInquiries.recentInquiries().isEmpty()
                    ? "최근 문의 있음"
                    : supportInquiries.recentInquiries().get(0).categoryLabel();
            items.add(new AdminDashboardAttentionResponse.AttentionItem(
                    "support-inquiry-backlog",
                    "info",
                    "서비스 문의 대기",
                    "%d건 열림 · 최근 24시간 %d건 · 최근 문의 유형: %s".formatted(
                            supportInquiries.openCount(),
                            supportInquiries.recentOpenCount24h(),
                            headline
                    ),
                    "admin-support-inquiries",
                    "support-inquiries",
                    "최근 문의 유형과 화면 경로를 보고 재현/응답 여부를 메모한 뒤 처리완료로 닫습니다."
            ));
        }
        return new AdminDashboardAttentionResponse(
                LocalDateTime.now(),
                items.size(),
                items
        );
    }

    private String notificationBacklogNextAction(AdminDashboardResponse.NotificationSection notification) {
        if (notification.terminalFailedNotifications() > 0 || notification.retryableFailedNotifications() > 0) {
            return "알림 발송 실패 상세와 최근 실패 수신처를 확인한 뒤 재시도/구독 비활성 원인을 분리합니다.";
        }
        if (notification.staleUnread14d() > 0) {
            return "14일 이상 오래된 알림 묶음을 확인하고 숨김 후보만 제한적으로 처리합니다.";
        }
        return "전체 미열람 규모만 관찰하고 오래된 알림 묶음이 생길 때 정리합니다.";
    }
}
