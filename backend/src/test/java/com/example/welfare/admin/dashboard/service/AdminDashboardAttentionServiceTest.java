package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminCollectFailureResponse;
import com.example.welfare.admin.dashboard.dto.AdminDashboardResponse;
import com.example.welfare.admin.dashboard.dto.AdminPolicyDuplicateGroupResponse;
import com.example.welfare.admin.dashboard.dto.AdminPolicyErrorReportResponse;
import com.example.welfare.admin.dashboard.dto.AdminPolicyLinkReviewResponse;
import com.example.welfare.admin.dashboard.dto.AdminNotificationStaleTargetResponse;
import com.example.welfare.admin.dashboard.dto.AdminSupportInquiryResponse;
import com.example.welfare.admin.dashboard.dto.AdminUserProfileStandardCodeCoverageResponse;
import com.example.welfare.admin.dashboard.dto.AdminWrapperObservationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AdminDashboardAttentionServiceTest {

    private final AdminDashboardCollectService collectService = mock(AdminDashboardCollectService.class);
    private final AdminDashboardSummaryService summaryService = mock(AdminDashboardSummaryService.class);
    private final AdminDashboardUserProfileService userProfileService = mock(AdminDashboardUserProfileService.class);
    private final AdminDashboardWrapperObservationService wrapperObservationService = mock(AdminDashboardWrapperObservationService.class);
    private final AdminPolicyDuplicateGroupService policyDuplicateGroupService = mock(AdminPolicyDuplicateGroupService.class);
    private final AdminPolicyErrorReportService policyErrorReportService = mock(AdminPolicyErrorReportService.class);
    private final AdminPolicyLinkReviewService adminPolicyLinkReviewService = mock(AdminPolicyLinkReviewService.class);
    private final AdminNotificationStaleTargetService adminNotificationStaleTargetService = mock(AdminNotificationStaleTargetService.class);
    private final AdminSupportInquiryService supportInquiryService = mock(AdminSupportInquiryService.class);
    private final AdminDashboardAttentionService service = new AdminDashboardAttentionService(
            collectService,
            summaryService,
            userProfileService,
            wrapperObservationService,
            policyDuplicateGroupService,
            policyErrorReportService,
            adminPolicyLinkReviewService,
            adminNotificationStaleTargetService,
            supportInquiryService
    );

    @Test
    @DisplayName("collect drift와 wrapper warning을 attention feed로 묶고 관찰용 표준코드는 승격하지 않는다")
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
        given(summaryService.getSummary(null, null)).willReturn(new AdminDashboardResponse(
                LocalDateTime.of(2026, 6, 3, 14, 0),
                null,
                null,
                new AdminDashboardResponse.NotificationSection(0, 0, 7, 0, 0, 0, 0, 0, 0, 0),
                null,
                null,
                null,
                null
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
        given(policyDuplicateGroupService.getRecentGroups(1)).willReturn(new AdminPolicyDuplicateGroupResponse(0, 0, 0, List.of()));
        given(policyErrorReportService.getRecentReports(1)).willReturn(new AdminPolicyErrorReportResponse(0, 0, List.of()));
        given(adminPolicyLinkReviewService.getRecentReviews(1)).willReturn(new AdminPolicyLinkReviewResponse(0, 0, List.of()));
        given(adminNotificationStaleTargetService.getRecentTargets(1, 14)).willReturn(new AdminNotificationStaleTargetResponse(14, 0, 0, List.of()));
        given(supportInquiryService.getRecentInquiries(1)).willReturn(new AdminSupportInquiryResponse(0, 0, List.of()));

        var response = service.getAttentionFeed();

        assertThat(response.itemCount()).isEqualTo(2);
        assertThat(response.items()).extracting("key")
                .containsExactly("collect-drift", "wrapper-warning");
        assertThat(response.items()).extracting("key", "severity")
                .contains(
                        tuple("collect-drift", "warning"),
                        tuple("wrapper-warning", "warning")
                );
        assertThat(response.items()).extracting("nextAction", String.class)
                .contains(
                        "수집 실패 샘플과 열린 회로를 확인하고, 같은 출처의 반복 실패면 출처별 수동 재수집과 회로 상태를 점검합니다.",
                        "현재 우선순위와 기준 요약을 비교하고 선택 프로필/추천 관측 변화 원인을 확인합니다."
                );
    }

    @Test
    @DisplayName("선택 프로필 자동 보정 후보가 있으면 attention feed에서 warning으로 승격한다")
    void promotesStandardCodeBacklogWhenReconcileCandidatesExist() {
        given(collectService.getCollectFailures(null, null)).willReturn(new AdminCollectFailureResponse(
                LocalDateTime.of(2026, 6, 4, 9, 0),
                7,
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));
        given(userProfileService.getUserProfileStandardCodeCoverage()).willReturn(new AdminUserProfileStandardCodeCoverageResponse(
                LocalDateTime.of(2026, 6, 4, 9, 0),
                10,
                10,
                0,
                5,
                0,
                5,
                5,
                2,
                2,
                1,
                5,
                0,
                5,
                0,
                0,
                2,
                0
        ));
        given(summaryService.getSummary(null, null)).willReturn(new AdminDashboardResponse(
                LocalDateTime.of(2026, 6, 4, 9, 0),
                null,
                null,
                new AdminDashboardResponse.NotificationSection(0, 0, 7, 0, 0, 0, 0, 0, 0, 0),
                null,
                null,
                null,
                null
        ));
        given(wrapperObservationService.getLatestObservation()).willReturn(new AdminWrapperObservationResponse(
                false,
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                null,
                null,
                0,
                0,
                null,
                0,
                0,
                0.0,
                false,
                null,
                null,
                null,
                false,
                null,
                0,
                0,
                null,
                null,
                0,
                0,
                null,
                0,
                0,
                0,
                0.0,
                false,
                null,
                null,
                0,
                0,
                "이전값 없음",
                null,
                false,
                "이전 상태 없음",
                null
        ));
        given(policyDuplicateGroupService.getRecentGroups(1)).willReturn(new AdminPolicyDuplicateGroupResponse(0, 0, 0, List.of()));
        given(policyErrorReportService.getRecentReports(1)).willReturn(new AdminPolicyErrorReportResponse(0, 0, List.of()));
        given(adminPolicyLinkReviewService.getRecentReviews(1)).willReturn(new AdminPolicyLinkReviewResponse(0, 0, List.of()));
        given(adminNotificationStaleTargetService.getRecentTargets(1, 14)).willReturn(new AdminNotificationStaleTargetResponse(14, 0, 0, List.of()));
        given(supportInquiryService.getRecentInquiries(1)).willReturn(new AdminSupportInquiryResponse(0, 0, List.of()));

        var response = service.getAttentionFeed();

        assertThat(response.items()).extracting("key", "severity")
                .contains(tuple("standard-code-backlog", "warning"));
        assertThat(response.items()).extracting("message", String.class)
                .anySatisfy(message -> assertThat(message)
                        .contains("자동 보정 후보 2건")
                        .contains("기본/상세 값 충돌 0건")
                        .contains("전부 미입력 사용자는 5명"));
        assertThat(response.items()).extracting("nextAction", String.class)
                .contains("자동 보정 후보와 기본/상세 값 충돌을 먼저 검토하고 안전 후보만 보정합니다.");
    }

    @Test
    @DisplayName("정책 오류 제보와 서비스 문의 대기 항목이 있으면 attention feed에 함께 승격한다")
    void includesReportBacklogs() {
        given(collectService.getCollectFailures(null, null)).willReturn(new AdminCollectFailureResponse(
                LocalDateTime.of(2026, 6, 4, 10, 0),
                7,
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));
        given(userProfileService.getUserProfileStandardCodeCoverage()).willReturn(new AdminUserProfileStandardCodeCoverageResponse(
                LocalDateTime.of(2026, 6, 4, 10, 0),
                10,
                10,
                0,
                10,
                0,
                0,
                10,
                10,
                10,
                10,
                10,
                0,
                0,
                0,
                0,
                0,
                0
        ));
        given(summaryService.getSummary(null, null)).willReturn(new AdminDashboardResponse(
                LocalDateTime.of(2026, 6, 4, 10, 0),
                null,
                null,
                new AdminDashboardResponse.NotificationSection(0, 0, 7, 0, 0, 0, 0, 0, 0, 0),
                null,
                null,
                null,
                null
        ));
        given(wrapperObservationService.getLatestObservation()).willReturn(new AdminWrapperObservationResponse(
                false,
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                null,
                null,
                0,
                0,
                null,
                0,
                0,
                0.0,
                false,
                null,
                null,
                null,
                false,
                null,
                0,
                0,
                null,
                null,
                0,
                0,
                null,
                0,
                0,
                0,
                0.0,
                false,
                null,
                null,
                0,
                0,
                "이전값 없음",
                null,
                false,
                "이전 상태 없음",
                null
        ));
        given(policyDuplicateGroupService.getRecentGroups(1)).willReturn(new AdminPolicyDuplicateGroupResponse(
                3,
                1,
                12,
                List.of(new AdminPolicyDuplicateGroupResponse.Item(
                        "YOUTH",
                        "청년문화예술패스",
                        "",
                        null,
                        "exact_duplicate_candidate",
                        3,
                        "A, B, C",
                        LocalDateTime.of(2026, 6, 4, 9, 40),
                        "OPEN",
                        null,
                        null,
                        null
                ))
        ));
        given(policyErrorReportService.getRecentReports(1)).willReturn(new AdminPolicyErrorReportResponse(
                2,
                1,
                List.of(new AdminPolicyErrorReportResponse.Item(
                        9L, 33L, "청년 교통비 지원", "GOV24", "SRC-33",
                        "BROKEN_LINK", "링크나 원문이 열리지 않습니다", "원문 링크가 404입니다.",
                        "user-key-1", LocalDateTime.of(2026, 6, 4, 9, 45),
                        "OPEN", null, null, null
                ))
        ));
        given(adminPolicyLinkReviewService.getRecentReviews(1)).willReturn(new AdminPolicyLinkReviewResponse(
                4,
                2,
                List.of(new AdminPolicyLinkReviewResponse.Item(
                        88L,
                        "청년 창업 실험실 지원사업",
                        "YOUTH",
                        "20260504005400113130",
                        "program_event",
                        "청년정책관",
                        "충청남도 및 충남경제진흥원",
                        "일자리",
                        "취업",
                        null,
                        null,
                        LocalDateTime.of(2026, 6, 4, 9, 48),
                        "OPEN",
                        null,
                        null,
                        null
                ))
        ));
        given(adminNotificationStaleTargetService.getRecentTargets(1, 14)).willReturn(new AdminNotificationStaleTargetResponse(14, 0, 0, List.of()));
        given(supportInquiryService.getRecentInquiries(1)).willReturn(new AdminSupportInquiryResponse(
                1,
                1,
                List.of(new AdminSupportInquiryResponse.Item(
                        21L, "BUG_ERROR", "오류/버그", "user@example.com",
                        "필터가 왜 바로 적용되는지 헷갈립니다.", "/policies", "user-key-21",
                        LocalDateTime.of(2026, 6, 4, 9, 50),
                        "OPEN", null, null, null
                ))
        ));

        var response = service.getAttentionFeed();

        assertThat(response.items()).extracting("key")
                .contains("policy-duplicate-backlog", "policy-error-report-backlog", "policy-link-review-backlog", "support-inquiry-backlog");
        assertThat(response.items()).extracting("message", String.class)
                .anySatisfy(message -> assertThat(message).contains("최근 24시간 1건"));
        assertThat(response.items()).extracting("nextAction", String.class)
                .contains(
                        "중복 건수가 큰 묶음부터 오탐 여부를 판단해 중복/링크 검토 우선순위를 기록합니다.",
                        "최근 제보의 정책 원문과 링크를 확인하고 보정 또는 처리완료 메모를 남깁니다.",
                        "분류별 대표 링크를 열어 공식 상세 링크 여부를 확인하고 처리완료 메모를 남깁니다.",
                        "최근 문의 유형과 화면 경로를 보고 재현/응답 여부를 메모한 뒤 처리완료로 닫습니다."
                );
    }

    @Test
    @DisplayName("14일 이상 미열람이나 실패 대기 항목이 있으면 attention feed에 알림 항목을 승격한다")
    void includesNotificationBacklog() {
        given(collectService.getCollectFailures(null, null)).willReturn(new AdminCollectFailureResponse(
                LocalDateTime.of(2026, 6, 4, 11, 0),
                7,
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));
        given(userProfileService.getUserProfileStandardCodeCoverage()).willReturn(new AdminUserProfileStandardCodeCoverageResponse(
                LocalDateTime.of(2026, 6, 4, 11, 0),
                10,
                10,
                0,
                10,
                0,
                0,
                10,
                10,
                10,
                10,
                10,
                0,
                0,
                0,
                0,
                0,
                0
        ));
        given(summaryService.getSummary(null, null)).willReturn(new AdminDashboardResponse(
                LocalDateTime.of(2026, 6, 4, 11, 0),
                null,
                null,
                new AdminDashboardResponse.NotificationSection(2, 1, 7, 14, 3, 11, 7, 2, 2, 1),
                null,
                null,
                null,
                null
        ));
        given(wrapperObservationService.getLatestObservation()).willReturn(new AdminWrapperObservationResponse(
                false,
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                null,
                null,
                0,
                0,
                null,
                0,
                0,
                0.0,
                false,
                null,
                null,
                null,
                false,
                null,
                0,
                0,
                null,
                null,
                0,
                0,
                null,
                0,
                0,
                0,
                0.0,
                false,
                null,
                null,
                0,
                0,
                "이전값 없음",
                null,
                false,
                "이전 상태 없음",
                null
        ));
        given(policyDuplicateGroupService.getRecentGroups(1)).willReturn(new AdminPolicyDuplicateGroupResponse(0, 0, 0, List.of()));
        given(policyErrorReportService.getRecentReports(1)).willReturn(new AdminPolicyErrorReportResponse(0, 0, List.of()));
        given(adminPolicyLinkReviewService.getRecentReviews(1)).willReturn(new AdminPolicyLinkReviewResponse(0, 0, List.of()));
        given(adminNotificationStaleTargetService.getRecentTargets(1, 14)).willReturn(new AdminNotificationStaleTargetResponse(14, 0, 0, List.of()));
        given(supportInquiryService.getRecentInquiries(1)).willReturn(new AdminSupportInquiryResponse(0, 0, List.of()));

        var response = service.getAttentionFeed();

        assertThat(response.items()).extracting("key").contains("notification-backlog");
        assertThat(response.items()).extracting("message", String.class)
                .anySatisfy(message -> assertThat(message)
                        .contains("안 읽은 알림 11건")
                        .contains("7일 이상 미열람 7건")
                        .contains("14일 이상 미열람 2건")
                        .contains("재시도 대기 2건")
                        .contains("종결 실패 1건"));
        assertThat(response.items()).extracting("nextAction", String.class)
                .contains("알림 발송 실패 상세와 최근 실패 수신처를 확인한 뒤 재시도/구독 비활성 원인을 분리합니다.");
    }

    @Test
    @DisplayName("안 읽은 알림과 7일 이상 미열람만 있으면 attention feed에 알림 대기 항목을 승격하지 않는다")
    void doesNotPromoteNotificationObservationOnlyTail() {
        given(collectService.getCollectFailures(null, null)).willReturn(new AdminCollectFailureResponse(
                LocalDateTime.of(2026, 6, 4, 11, 15),
                7,
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));
        given(userProfileService.getUserProfileStandardCodeCoverage()).willReturn(new AdminUserProfileStandardCodeCoverageResponse(
                LocalDateTime.of(2026, 6, 4, 11, 15),
                10,
                10,
                0,
                10,
                0,
                0,
                10,
                10,
                10,
                10,
                10,
                0,
                0,
                0,
                0,
                0,
                0
        ));
        given(summaryService.getSummary(null, null)).willReturn(new AdminDashboardResponse(
                LocalDateTime.of(2026, 6, 4, 11, 15),
                null,
                null,
                new AdminDashboardResponse.NotificationSection(2, 0, 7, 14, 0, 11, 7, 0, 0, 0),
                null,
                null,
                null,
                null
        ));
        given(wrapperObservationService.getLatestObservation()).willReturn(new AdminWrapperObservationResponse(
                false,
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                null,
                null,
                0,
                0,
                null,
                0,
                0,
                0.0,
                false,
                null,
                null,
                null,
                false,
                null,
                0,
                0,
                null,
                null,
                0,
                0,
                null,
                0,
                0,
                0,
                0.0,
                false,
                null,
                null,
                0,
                0,
                "이전값 없음",
                null,
                false,
                "이전 상태 없음",
                null
        ));
        given(policyDuplicateGroupService.getRecentGroups(1)).willReturn(new AdminPolicyDuplicateGroupResponse(0, 0, 0, List.of()));
        given(policyErrorReportService.getRecentReports(1)).willReturn(new AdminPolicyErrorReportResponse(0, 0, List.of()));
        given(adminPolicyLinkReviewService.getRecentReviews(1)).willReturn(new AdminPolicyLinkReviewResponse(0, 0, List.of()));
        given(adminNotificationStaleTargetService.getRecentTargets(1, 14)).willReturn(new AdminNotificationStaleTargetResponse(14, 0, 0, List.of()));
        given(supportInquiryService.getRecentInquiries(1)).willReturn(new AdminSupportInquiryResponse(0, 0, List.of()));

        var response = service.getAttentionFeed();

        assertThat(response.items()).extracting("key").doesNotContain("notification-backlog");
    }

    @Test
    @DisplayName("2주 이상 오래된 알림 묶음이 있으면 attention feed에 오래된 알림 항목을 승격한다")
    void includesNotificationStaleBacklog() {
        given(collectService.getCollectFailures(null, null)).willReturn(new AdminCollectFailureResponse(
                LocalDateTime.of(2026, 6, 4, 11, 30),
                7,
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));
        given(userProfileService.getUserProfileStandardCodeCoverage()).willReturn(new AdminUserProfileStandardCodeCoverageResponse(
                LocalDateTime.of(2026, 6, 4, 11, 30),
                10, 10, 0, 10, 0, 0, 10, 10, 10, 10, 10, 0, 0, 0, 0, 0, 0
        ));
        given(summaryService.getSummary(null, null)).willReturn(new AdminDashboardResponse(
                LocalDateTime.of(2026, 6, 4, 11, 30),
                null,
                null,
                new AdminDashboardResponse.NotificationSection(0, 0, 4, 0, 0, 0, 0, 0, 0, 0),
                null,
                null,
                null,
                null
        ));
        given(wrapperObservationService.getLatestObservation()).willReturn(new AdminWrapperObservationResponse(
                false, null, null, null, null, null, 0, 0, null, null, 0, 0, null, 0, 0, 0.0,
                false, null, null, null, false, null, 0, 0, null, null, 0, 0, null, 0, 0, 0, 0.0,
                false, null, null, 0, 0, "이전값 없음", null, false, "이전 상태 없음", null
        ));
        given(policyDuplicateGroupService.getRecentGroups(1)).willReturn(new AdminPolicyDuplicateGroupResponse(0, 0, 0, List.of()));
        given(policyErrorReportService.getRecentReports(1)).willReturn(new AdminPolicyErrorReportResponse(0, 0, List.of()));
        given(adminPolicyLinkReviewService.getRecentReviews(1)).willReturn(new AdminPolicyLinkReviewResponse(0, 0, List.of()));
        given(adminNotificationStaleTargetService.getRecentTargets(1, 14)).willReturn(new AdminNotificationStaleTargetResponse(
                14,
                9,
                3,
                List.of(new AdminNotificationStaleTargetResponse.Item(
                        "DEADLINE_REMINDER",
                        "북마크한 정책 마감이 임박했어요",
                        "/policies/2622",
                        5,
                        5,
                        LocalDateTime.of(2026, 5, 16, 5, 55, 31),
                        LocalDateTime.of(2026, 5, 17, 4, 13, 16)
                ))
        ));
        given(supportInquiryService.getRecentInquiries(1)).willReturn(new AdminSupportInquiryResponse(0, 0, List.of()));

        var response = service.getAttentionFeed();

        assertThat(response.items()).extracting("key").contains("notification-stale-backlog");
        assertThat(response.items()).extracting("nextAction", String.class)
                .contains("대표 이동 경로를 확인한 뒤 같은 묶음만 제한적으로 숨김 처리하고 미열람 총량 변화를 재확인합니다.");
    }
}
