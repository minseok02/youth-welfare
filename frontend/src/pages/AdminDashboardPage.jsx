import {
  useCallback,
  useEffect,
  useRef,
} from "react";
import {
  Box,
  Stack,
  Tab,
  Tabs,
  Typography,
} from "@mui/material";
import { useSearchParams } from "react-router-dom";
import Header from "../components/Header";
import FloatingNav from "../components/FloatingNav";
import AdminDashboardTopSection from "../components/adminDashboard/AdminDashboardTopSection";
import AdminCollectTriageSection from "../components/adminDashboard/AdminCollectTriageSection";
import AdminAttentionQueueSection from "../components/adminDashboard/AdminAttentionQueueSection";
import AdminNotificationStaleTargetsSection from "../components/adminDashboard/AdminNotificationStaleTargetsSection";
import AdminPolicyDuplicateGroupsSection from "../components/adminDashboard/AdminPolicyDuplicateGroupsSection";
import AdminPolicyErrorReportsSection from "../components/adminDashboard/AdminPolicyErrorReportsSection";
import AdminPolicyLinkReviewsSection from "../components/adminDashboard/AdminPolicyLinkReviewsSection";
import AdminReferenceCodebooksSection from "../components/adminDashboard/AdminReferenceCodebooksSection";
import AdminRecommendationBreakdownsSection from "../components/adminDashboard/AdminRecommendationBreakdownsSection";
import AdminRecommendationOverviewSection from "../components/adminDashboard/AdminRecommendationOverviewSection";
import AdminRecommendationRunSummarySection from "../components/adminDashboard/AdminRecommendationRunSummarySection";
import AdminSearchTriageSection from "../components/adminDashboard/AdminSearchTriageSection";
import AdminStandardCodeEffectSection from "../components/adminDashboard/AdminStandardCodeEffectSection";
import AdminStandardCodeCoverageSection from "../components/adminDashboard/AdminStandardCodeCoverageSection";
import AdminSupportInquiriesSection from "../components/adminDashboard/AdminSupportInquiriesSection";
import AdminWrapperObservationSection from "../components/adminDashboard/AdminWrapperObservationSection";
import { PAGE_BG } from "../components/adminDashboard/AdminDashboardUiTokens";
import { useAdminDashboardActions } from "../lib/useAdminDashboardActions";
import { useAdminDashboardFilters } from "../lib/useAdminDashboardFilters";
import { useAdminDashboardJump } from "../lib/useAdminDashboardJump";
import { useAdminDashboardTopDerived } from "../lib/useAdminDashboardTopDerived";
import { useAdminDashboardQueries } from "../lib/useAdminDashboardQueries";

const DEFAULT_ADMIN_DASHBOARD_VIEW = "today";

const ADMIN_DASHBOARD_VIEWS = [
  {
    value: "today",
    label: "오늘 볼 것",
    description: "오늘 운영자가 직접 판단하거나 닫아야 할 주의 항목만 먼저 확인합니다.",
  },
  {
    value: "queue",
    label: "처리 대기",
    description: "정책 오류 제보, 문의, 중복, 링크, 오래된 알림처럼 사람이 처리해야 하는 항목을 모았습니다.",
  },
  {
    value: "recommendation",
    label: "추천 품질",
    description: "추천 분포, 실행 로그, 알림 발송 상태를 점검합니다.",
  },
  {
    value: "policy-data",
    label: "정책 데이터",
    description: "정책 수집 실패와 검색 실패를 원인별로 확인합니다.",
  },
  {
    value: "profile-code",
    label: "프로필 관찰",
    description: "선택 프로필 입력 현황, 추천 영향, 공식 코드북을 낮은 빈도로 확인합니다.",
  },
  {
    value: "system",
    label: "시스템",
    description: "상위 요약 관측값과 기준 재사용 상태를 낮은 빈도로 점검합니다.",
  },
];

const ADMIN_DASHBOARD_VIEW_VALUES = new Set(ADMIN_DASHBOARD_VIEWS.map((view) => view.value));

const ADMIN_DASHBOARD_VIEW_SECTION_IDS = {
  today: new Set([
    "admin-attention-queue",
  ]),
  queue: new Set([
    "admin-notification-stale-targets",
    "admin-policy-error-reports",
    "admin-support-inquiries",
    "admin-policy-duplicate-groups",
    "admin-policy-link-reviews",
  ]),
  recommendation: new Set([
    "admin-recommendation-overview",
    "admin-notification-summary",
    "admin-notification-attempt-summary",
    "admin-notification-stale-targets",
    "admin-policy-triage-summary",
    "admin-recommendation-run-summary",
    "admin-recommendation-breakdowns",
  ]),
  "policy-data": new Set([
    "admin-collect-triage",
    "admin-search-triage",
  ]),
  "profile-code": new Set([
    "admin-standard-code-coverage",
    "admin-standard-code-effect",
    "admin-reference-codebooks",
  ]),
  system: new Set([
    "admin-wrapper-observation",
  ]),
};

const ADMIN_DASHBOARD_SECTION_VIEW = {
  "admin-attention-queue": "today",
  "admin-notification-stale-targets": "queue",
  "admin-policy-error-reports": "queue",
  "admin-support-inquiries": "queue",
  "admin-policy-duplicate-groups": "queue",
  "admin-policy-link-reviews": "queue",
  "admin-recommendation-overview": "recommendation",
  "admin-notification-summary": "recommendation",
  "admin-notification-attempt-summary": "recommendation",
  "admin-policy-triage-summary": "recommendation",
  "admin-recommendation-run-summary": "recommendation",
  "admin-recommendation-breakdowns": "recommendation",
  "admin-collect-triage": "policy-data",
  "admin-search-triage": "policy-data",
  "admin-standard-code-coverage": "profile-code",
  "admin-standard-code-effect": "profile-code",
  "admin-reference-codebooks": "profile-code",
  "admin-wrapper-observation": "system",
};

function normalizeAdminDashboardView(value) {
  return ADMIN_DASHBOARD_VIEW_VALUES.has(value) ? value : DEFAULT_ADMIN_DASHBOARD_VIEW;
}

export default function AdminDashboardPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const activeView = normalizeAdminDashboardView(searchParams.get("view"));
  const activeViewMeta = ADMIN_DASHBOARD_VIEWS.find((view) => view.value === activeView) ?? ADMIN_DASHBOARD_VIEWS[0];
  const pendingJumpRef = useRef(null);
  const jumpRetryTimeoutIdsRef = useRef([]);
  const baseJumpToSection = useAdminDashboardJump();
  const clearScheduledJumps = useCallback(() => {
    jumpRetryTimeoutIdsRef.current.forEach((timeoutId) => window.clearTimeout(timeoutId));
    jumpRetryTimeoutIdsRef.current = [];
  }, []);
  const scheduleJumpToSection = useCallback((targetId, options = {}) => {
    clearScheduledJumps();
    jumpRetryTimeoutIdsRef.current = [0, 120, 360, 800, 1400].map((delay) => (
      window.setTimeout(() => baseJumpToSection(targetId, options), delay)
    ));
  }, [baseJumpToSection, clearScheduledJumps]);
  const setDashboardView = useCallback((nextView, options = {}) => {
    const normalizedView = normalizeAdminDashboardView(nextView);
    setSearchParams((previousParams) => {
      const nextParams = new URLSearchParams(previousParams);
      if (normalizedView === DEFAULT_ADMIN_DASHBOARD_VIEW) {
        nextParams.delete("view");
      } else {
        nextParams.set("view", normalizedView);
      }
      return nextParams;
    }, { replace: options.replace ?? false });
  }, [setSearchParams]);
  const handleDashboardViewChange = useCallback((_event, nextView) => {
    setDashboardView(nextView);
  }, [setDashboardView]);
  const jumpToSection = useCallback((targetId, options = {}) => {
    const targetIsVisible = ADMIN_DASHBOARD_VIEW_SECTION_IDS[activeView]?.has(targetId);
    const nextView = targetIsVisible ? activeView : (ADMIN_DASHBOARD_SECTION_VIEW[targetId] ?? activeView);
    pendingJumpRef.current = { targetId, options };
    if (nextView !== activeView) {
      setDashboardView(nextView);
      return;
    }
    pendingJumpRef.current = null;
    scheduleJumpToSection(targetId, options);
  }, [activeView, scheduleJumpToSection, setDashboardView]);

  useEffect(() => {
    const pendingJump = pendingJumpRef.current;
    if (!pendingJump) {
      return undefined;
    }
    const timeoutId = window.setTimeout(() => {
      pendingJumpRef.current = null;
      scheduleJumpToSection(pendingJump.targetId, pendingJump.options);
    }, 80);
    return () => window.clearTimeout(timeoutId);
  }, [activeView, scheduleJumpToSection]);

  useEffect(() => () => clearScheduledJumps(), [clearScheduledJumps]);

  const {
    windowDays,
    setWindowDays,
    selectedCodeSetKey,
    setSelectedCodeSetKey,
    codebookQueryText,
    setCodebookQueryText,
    policyErrorStatusFilter,
    setPolicyErrorStatusFilter,
    supportInquiryStatusFilter,
    setSupportInquiryStatusFilter,
    policyDuplicateStatusFilter,
    setPolicyDuplicateStatusFilter,
    policyLinkStatusFilter,
    setPolicyLinkStatusFilter,
    notificationStaleDays,
    setNotificationStaleDays,
  } = useAdminDashboardFilters();
  const {
    summaryQuery,
    breakdownQuery,
    recommendationRunSummaryQuery,
    collectFailuresQuery,
    searchFailuresQuery,
    standardCodeCoverageQuery,
    attentionFeedQuery,
    standardCodeEffectObservationQuery,
    wrapperObservationQuery,
    policyErrorReportsQuery,
    regionOptionsQuery,
    policyRegionCorrectionsQuery,
    policyFieldCorrectionsQuery,
    supportInquiriesQuery,
    policyDuplicateGroupsQuery,
    policyLinkReviewsQuery,
    notificationStaleTargetsQuery,
    notificationAttemptSummaryQuery,
    officialCodebooksQuery,
    officialCodebookDetailQuery,
    summaryData,
    recommendationSummary,
    concentration,
    breakdowns,
    recommendationRunSummary,
    collectFailures,
    searchFailures,
    standardCodeCoverage,
    attentionFeed,
    standardCodeEffectObservation,
    wrapperObservation,
    policyErrorReports,
    regionOptions,
    policyRegionCorrections,
    policyFieldCorrections,
    supportInquiries,
    policyDuplicateGroups,
    policyLinkReviews,
    notificationStaleTargets,
    notificationAttemptSummary,
    officialCodebooks,
    effectiveSelectedCodeSetKey,
    selectedCodebookSummary,
    officialCodebookDetail,
    detailRows,
    failedSectionCount,
    isRefreshing,
    refetchAll,
    summaryErrorMessage,
    breakdownErrorMessage,
    recommendationRunSummaryErrorMessage,
    collectErrorMessage,
    searchErrorMessage,
    standardCodeCoverageErrorMessage,
    attentionFeedErrorMessage,
    standardCodeEffectObservationErrorMessage,
    wrapperObservationErrorMessage,
    policyErrorReportsErrorMessage,
    regionOptionsErrorMessage,
    policyRegionCorrectionsErrorMessage,
    policyFieldCorrectionsErrorMessage,
    supportInquiriesErrorMessage,
    policyDuplicateGroupsErrorMessage,
    policyLinkReviewsErrorMessage,
    notificationStaleTargetsErrorMessage,
    notificationAttemptSummaryErrorMessage,
    officialCodebooksErrorMessage,
    officialCodebookDetailErrorMessage,
  } = useAdminDashboardQueries({
    activeView,
    windowDays,
    selectedCodeSetKey,
    codebookQueryText,
    policyErrorStatusFilter,
    supportInquiryStatusFilter,
    policyDuplicateStatusFilter,
    policyLinkStatusFilter,
    notificationStaleDays,
  });
  const {
    policyErrorReviewNotes,
    setPolicyErrorReviewNotes,
    policyRegionCorrectionInputs,
    updatePolicyRegionCorrectionInput,
    policyFieldCorrectionInputs,
    updatePolicyFieldCorrectionInput,
    latestRegionAuditResult,
    supportInquiryReviewNotes,
    setSupportInquiryReviewNotes,
    policyDuplicateReviewNotes,
    setPolicyDuplicateReviewNotes,
    policyLinkReviewNotes,
    setPolicyLinkReviewNotes,
    reviewSubmittingKey,
    handleReviewPolicyErrorReport,
    handleApplyPolicyRegionCorrection,
    handleRunPolicyRegionAudit,
    handleApplySuggestedRegionCorrection,
    handleApplyPolicyFieldCorrection,
    handleRevertPolicyRegionCorrection,
    handleReviewSupportInquiry,
    handleReviewPolicyDuplicateGroup,
    handleReviewPolicyLink,
    handleHideNotificationStaleTarget,
  } = useAdminDashboardActions({
    summaryQuery,
    attentionFeedQuery,
    policyErrorReportsQuery,
    policyRegionCorrectionsQuery,
    policyFieldCorrectionsQuery,
    supportInquiriesQuery,
    policyDuplicateGroupsQuery,
    policyLinkReviewsQuery,
    notificationStaleTargetsQuery,
    notificationStaleTargets,
    notificationStaleDays,
  });
  const {
    localAttentionQueueItems,
    attentionQueueItems,
  } = useAdminDashboardTopDerived({
    summaryData,
    breakdowns,
    collectFailures,
    searchFailures,
    standardCodeCoverage,
    attentionFeed,
    wrapperObservation,
    failedSectionCount,
  });

  const dashboardTopSectionProps = {
    windowDays,
    onWindowDaysChange: setWindowDays,
    isRefreshing,
    onRefreshAll: refetchAll,
  };

  const attentionQueueSectionProps = {
    attentionFeedQuery,
    localAttentionQueueItems,
    attentionQueueItems,
    attentionFeedErrorMessage,
    onJumpToSection: jumpToSection,
  };

  const standardCodeCoverageSectionProps = {
    standardCodeCoverageQuery,
    standardCodeCoverageErrorMessage,
    standardCodeCoverage,
  };

  const policyErrorReportsSectionProps = {
    filters: {
      policyErrorStatusFilter,
      onPolicyErrorStatusFilterChange: setPolicyErrorStatusFilter,
    },
    queries: {
      regionOptionsQuery,
      regionOptionsErrorMessage,
      policyRegionCorrectionsQuery,
      policyRegionCorrectionsErrorMessage,
      policyFieldCorrectionsQuery,
      policyFieldCorrectionsErrorMessage,
      policyErrorReportsQuery,
      policyErrorReportsErrorMessage,
    },
    data: {
      policyErrorReports,
      regionOptions,
      policyRegionCorrections,
      policyFieldCorrections,
    },
    state: {
      reviewSubmittingKey,
      latestRegionAuditResult,
      policyRegionCorrectionInputs,
      policyFieldCorrectionInputs,
      policyErrorReviewNotes,
    },
    actions: {
      onRunPolicyRegionAudit: handleRunPolicyRegionAudit,
      updatePolicyRegionCorrectionInput,
      updatePolicyFieldCorrectionInput,
      setPolicyErrorReviewNotes,
      onApplySuggestedRegionCorrection: handleApplySuggestedRegionCorrection,
      onApplyPolicyRegionCorrection: handleApplyPolicyRegionCorrection,
      onApplyPolicyFieldCorrection: handleApplyPolicyFieldCorrection,
      onReviewPolicyErrorReport: handleReviewPolicyErrorReport,
      onRevertPolicyRegionCorrection: handleRevertPolicyRegionCorrection,
    },
  };

  const supportInquiriesSectionProps = {
    filters: {
      supportInquiryStatusFilter,
      onSupportInquiryStatusFilterChange: setSupportInquiryStatusFilter,
    },
    queries: {
      supportInquiriesQuery,
      supportInquiriesErrorMessage,
    },
    data: { supportInquiries },
    state: {
      supportInquiryReviewNotes,
      reviewSubmittingKey,
    },
    actions: {
      setSupportInquiryReviewNotes,
      onReviewSupportInquiry: handleReviewSupportInquiry,
    },
  };

  const policyDuplicateGroupsSectionProps = {
    filters: {
      policyDuplicateStatusFilter,
      onPolicyDuplicateStatusFilterChange: setPolicyDuplicateStatusFilter,
    },
    queries: {
      policyDuplicateGroupsQuery,
      policyDuplicateGroupsErrorMessage,
    },
    data: { policyDuplicateGroups },
    state: {
      policyDuplicateReviewNotes,
      reviewSubmittingKey,
    },
    actions: {
      setPolicyDuplicateReviewNotes,
      onReviewPolicyDuplicateGroup: handleReviewPolicyDuplicateGroup,
    },
  };

  const policyLinkReviewsSectionProps = {
    filters: {
      policyLinkStatusFilter,
      onPolicyLinkStatusFilterChange: setPolicyLinkStatusFilter,
    },
    queries: {
      policyLinkReviewsQuery,
      policyLinkReviewsErrorMessage,
    },
    data: { policyLinkReviews },
    state: {
      policyLinkReviewNotes,
      reviewSubmittingKey,
    },
    actions: {
      setPolicyLinkReviewNotes,
      onReviewPolicyLink: handleReviewPolicyLink,
    },
  };

  const standardCodeEffectSectionProps = {
    standardCodeEffectObservationQuery,
    standardCodeEffectObservationErrorMessage,
    standardCodeEffectObservation,
  };

  const wrapperObservationSectionProps = {
    wrapperObservationQuery,
    wrapperObservationErrorMessage,
    wrapperObservation,
  };

  const referenceCodebooksSectionProps = {
    officialCodebooksQuery,
    officialCodebooksErrorMessage,
    officialCodebooks,
    effectiveSelectedCodeSetKey,
    onSelectedCodeSetKeyChange: setSelectedCodeSetKey,
    codebookQueryText,
    onCodebookQueryTextChange: setCodebookQueryText,
    selectedCodebookSummary,
    officialCodebookDetailQuery,
    officialCodebookDetailErrorMessage,
    officialCodebookDetail,
    detailRows,
  };

  const recommendationOverviewSectionProps = {
    summaryQuery,
    summaryErrorMessage,
    summaryData,
    recommendationSummary,
    concentration,
    notificationAttemptSummaryQuery,
    notificationAttemptSummaryErrorMessage,
    notificationAttemptSummary,
    notificationStaleTargetsQuery,
    notificationStaleTargetsErrorMessage,
    notificationStaleTargets,
    notificationStaleDays,
    onNotificationStaleDaysChange: setNotificationStaleDays,
    reviewSubmittingKey,
    onHideNotificationStaleTarget: handleHideNotificationStaleTarget,
  };

  const notificationStaleTargetsSectionProps = {
    notificationStaleTargetsQuery,
    notificationStaleTargetsErrorMessage,
    notificationStaleTargets,
    notificationStaleDays,
    onNotificationStaleDaysChange: setNotificationStaleDays,
    reviewSubmittingKey,
    onHideNotificationStaleTarget: handleHideNotificationStaleTarget,
  };

  const recommendationRunSummarySectionProps = {
    recommendationRunSummaryQuery,
    recommendationRunSummaryErrorMessage,
    recommendationRunSummary,
  };

  const recommendationBreakdownsSectionProps = {
    breakdownQuery,
    breakdownErrorMessage,
    breakdowns,
  };

  const collectTriageSectionProps = {
    collectFailuresQuery,
    collectErrorMessage,
    collectFailures,
    jumpToSection,
  };

  const searchTriageSectionProps = {
    searchFailuresQuery,
    searchErrorMessage,
    searchFailures,
    jumpToSection,
  };

  return (
    <div style={{ minHeight: "100vh", background: PAGE_BG }}>
      <Header />
      <FloatingNav />
      <Box sx={{ maxWidth: 1280, mx: "auto", px: { xs: 2, lg: 4 }, py: 4 }}>
        <AdminDashboardTopSection {...dashboardTopSectionProps} />

        <Box
          sx={{
            mt: 3,
            bgcolor: "#ffffff",
            border: "1px solid rgba(148,163,184,0.28)",
            borderRadius: 2,
            boxShadow: "0 10px 28px rgba(15,23,42,0.04)",
            overflow: "hidden",
          }}
        >
          <Tabs
            value={activeView}
            onChange={handleDashboardViewChange}
            variant="scrollable"
            scrollButtons="auto"
            allowScrollButtonsMobile
            aria-label="관리자 대시보드 보기"
            sx={{
              minHeight: 64,
              px: 1.25,
              pt: 1.25,
              borderBottom: "1px solid rgba(148,163,184,0.22)",
              "& .MuiTab-root": {
                minHeight: 48,
                mr: 0.75,
                px: 2.5,
                borderRadius: 1,
                border: "1px solid transparent",
                fontSize: { xs: 15, sm: 16 },
                fontWeight: 900,
                color: "#334155",
                textTransform: "none",
                bgcolor: "#f8fafc",
                "&:hover": {
                  color: "#1d4ed8",
                  bgcolor: "#eef4ff",
                },
              },
              "& .Mui-selected": {
                color: "#0f3d91",
                bgcolor: "#dbeafe",
                borderColor: "#93c5fd",
                boxShadow: "inset 0 -2px 0 #2563eb",
              },
              "& .MuiTabs-indicator": {
                height: 4,
                bgcolor: "#2563eb",
              },
            }}
          >
            {ADMIN_DASHBOARD_VIEWS.map((view) => (
              <Tab key={view.value} value={view.value} label={view.label} />
            ))}
          </Tabs>
          <Box sx={{ px: 2.75, py: 2 }}>
            <Typography sx={{ fontSize: { xs: 14, sm: 15 }, color: "#334155", fontWeight: 800 }}>
              {activeViewMeta.description}
            </Typography>
          </Box>
        </Box>

        <Stack spacing={3} mt={3}>
          {activeView === "today" && <AdminAttentionQueueSection {...attentionQueueSectionProps} />}

          {activeView === "queue" && <AdminNotificationStaleTargetsSection {...notificationStaleTargetsSectionProps} />}
          {activeView === "queue" && <AdminPolicyErrorReportsSection {...policyErrorReportsSectionProps} />}
          {activeView === "queue" && <AdminSupportInquiriesSection {...supportInquiriesSectionProps} />}
          {activeView === "queue" && <AdminPolicyDuplicateGroupsSection {...policyDuplicateGroupsSectionProps} />}
          {activeView === "queue" && <AdminPolicyLinkReviewsSection {...policyLinkReviewsSectionProps} />}

          {activeView === "recommendation" && <AdminRecommendationOverviewSection {...recommendationOverviewSectionProps} />}
          {activeView === "recommendation" && <AdminRecommendationRunSummarySection {...recommendationRunSummarySectionProps} />}
          {activeView === "recommendation" && <AdminRecommendationBreakdownsSection {...recommendationBreakdownsSectionProps} />}

          {activeView === "policy-data" && <AdminCollectTriageSection {...collectTriageSectionProps} />}
          {activeView === "policy-data" && <AdminSearchTriageSection {...searchTriageSectionProps} />}

          {activeView === "profile-code" && <AdminStandardCodeCoverageSection {...standardCodeCoverageSectionProps} />}
          {activeView === "profile-code" && <AdminStandardCodeEffectSection {...standardCodeEffectSectionProps} />}
          {activeView === "profile-code" && <AdminReferenceCodebooksSection {...referenceCodebooksSectionProps} />}

          {activeView === "system" && <AdminWrapperObservationSection {...wrapperObservationSectionProps} />}
        </Stack>
      </Box>
    </div>
  );
}
