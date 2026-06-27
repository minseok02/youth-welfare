package com.example.welfare.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AdminDashboardAlertSurfaceContractTest {

    private static final Path SURFACE_DOC = Path.of("../docs/core/admin-dashboard-alert-surface-contract.md");
    private static final Path VERIFY_SCRIPT = Path.of("../deploy/smoke/verify-admin-dashboard-alert-surface.sh");
    private static final Path ADMIN_PAGE = Path.of("../frontend/src/pages/AdminDashboardPage.jsx");
    private static final Path COLLECT_DTO = Path.of("../backend/src/main/java/com/example/welfare/admin/dashboard/dto/AdminCollectFailureResponse.java");
    private static final Path SEARCH_DTO = Path.of("../backend/src/main/java/com/example/welfare/admin/dashboard/dto/AdminSearchFailureResponse.java");
    private static final Path RECOMMENDATION_DTO = Path.of("../backend/src/main/java/com/example/welfare/admin/dashboard/dto/AdminRecommendationRunSummaryResponse.java");
    private static final Path NOTIFICATION_DTO = Path.of("../backend/src/main/java/com/example/welfare/admin/dashboard/dto/AdminNotificationAttemptSummaryResponse.java");
    private static final Path ATTENTION_DTO = Path.of("../backend/src/main/java/com/example/welfare/admin/dashboard/dto/AdminDashboardAttentionResponse.java");

    @Test
    @DisplayName("admin dashboard alert surface 문서는 evaluator 권위와 dashboard triage 경계를 구분한다")
    void documentSeparatesEvaluatorAuthorityFromDashboardTriageSurface() throws IOException {
        String doc = Files.readString(SURFACE_DOC);

        assertThat(doc)
                .contains("ADMIN_DASHBOARD_ALERT_SURFACE_AUTHORITY")
                .contains("ADMIN_DASHBOARD_ALERT_SURFACE_MAP")
                .contains("ADMIN_DASHBOARD_ALERT_SURFACE_LIMITS")
                .contains("ADMIN_DASHBOARD_ALERT_SURFACE_VERIFICATION")
                .contains("evaluate-operational-alert-thresholds.sh")
                .contains("dashboard 화면은 alert status를 직접 계산하지 않습니다.")
                .contains("Dashboard-only fields must not be used as the final alert status")
                .contains("attention item의 `nextAction`");
    }

    @Test
    @DisplayName("admin dashboard alert surface 문서는 모든 evaluator alert_id를 dashboard 확인 지점에 매핑한다")
    void documentMapsEvaluatorAlertsToDashboardSections() throws IOException {
        String doc = Files.readString(SURFACE_DOC);

        assertThat(doc)
                .contains("COLLECT_FAILED_JOB_RATE")
                .contains("SEARCH_ZERO_RESULT_RATE")
                .contains("RECOMMENDATION_RUN_FAILURE_RATE")
                .contains("NOTIFICATION_RETRY_BACKLOG")
                .contains("WEB_PUSH_DISABLED_RATIO")
                .contains("GET /api/admin/dashboard/collect-failures")
                .contains("GET /api/admin/dashboard/search-failures")
                .contains("GET /api/admin/dashboard/recommendation-run-summary")
                .contains("GET /api/admin/dashboard/notification-attempt-summary")
                .contains("GET /api/admin/dashboard/summary")
                .contains("admin-collect-triage")
                .contains("admin-search-triage")
                .contains("admin-recommendation-run-summary")
                .contains("admin-notification-attempt-summary");
    }

    @Test
    @DisplayName("admin dashboard page는 alert surface 문서의 섹션과 API를 유지한다")
    void dashboardPageKeepsAlertSurfaceSectionsAndApis() throws IOException {
        String page = Files.readString(ADMIN_PAGE);

        assertThat(page)
                .contains("/api/admin/dashboard/collect-failures")
                .contains("/api/admin/dashboard/search-failures")
                .contains("/api/admin/dashboard/recommendation-run-summary")
                .contains("/api/admin/dashboard/notification-attempt-summary")
                .contains("admin-collect-triage")
                .contains("admin-search-triage")
                .contains("admin-recommendation-run-summary")
                .contains("admin-notification-attempt-summary")
                .contains("failedJobsInWindow")
                .contains("partialSuccessJobsInWindow")
                .contains("zeroResultSearchesInWindow")
                .contains("errorRuns")
                .contains("noCandidateRuns")
                .contains("averageDurationMs")
                .contains("disabledAttempts")
                .contains("endpointHost")
                .contains("nextAction")
                .contains("다음 조치");
    }

    @Test
    @DisplayName("admin dashboard DTO는 alert triage에 필요한 raw field를 노출한다")
    void dashboardDtosExposeAlertTriageFields() throws IOException {
        assertThat(Files.readString(COLLECT_DTO))
                .contains("failedJobsInWindow")
                .contains("partialSuccessJobsInWindow")
                .contains("circuitStatuses")
                .contains("collectSourceLanes")
                .contains("recentSamples");

        assertThat(Files.readString(SEARCH_DTO))
                .contains("zeroResultSearchesInWindow")
                .contains("zeroResultRegions")
                .contains("zeroResultFilterPatterns")
                .contains("retryGroups")
                .contains("recoveredSearchGroups")
                .contains("recentSamples");

        assertThat(Files.readString(RECOMMENDATION_DTO))
                .contains("totalRuns")
                .contains("successRuns")
                .contains("errorRuns")
                .contains("noCandidateRuns")
                .contains("averageDurationMs")
                .contains("outcomeBreakdowns")
                .contains("recentRuns");

        assertThat(Files.readString(NOTIFICATION_DTO))
                .contains("failedAttempts")
                .contains("disabledAttempts")
                .contains("breakdowns")
                .contains("recentFailures")
                .contains("endpointHost")
                .contains("errorType");

        assertThat(Files.readString(ATTENTION_DTO))
                .contains("AttentionItem")
                .contains("nextAction");
    }

    @Test
    @DisplayName("admin dashboard alert surface 검증 스크립트는 같은 계약 식별자와 필드를 확인한다")
    void verificationScriptChecksSurfaceContract() throws IOException {
        String script = Files.readString(VERIFY_SCRIPT);

        assertThat(script)
                .contains("ADMIN_DASHBOARD_ALERT_SURFACE_AUTHORITY")
                .contains("ADMIN_DASHBOARD_ALERT_SURFACE_MAP")
                .contains("COLLECT_FAILED_JOB_RATE")
                .contains("SEARCH_ZERO_RESULT_RATE")
                .contains("RECOMMENDATION_RUN_FAILURE_RATE")
                .contains("NOTIFICATION_RETRY_BACKLOG")
                .contains("WEB_PUSH_DISABLED_RATIO")
                .contains("failedJobsInWindow")
                .contains("zeroResultSearchesInWindow")
                .contains("disabledAttempts")
                .contains("nextAction")
                .contains("다음 조치")
                .contains("admin dashboard alert surface contract passed");
    }
}
