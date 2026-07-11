import {
  Box,
  Stack,
} from "@mui/material";
import Header from "../components/Header";
import FloatingNav from "../components/FloatingNav";
import AdminDashboardTopSection from "../components/adminDashboard/AdminDashboardTopSection";
import AdminCollectTriageSection from "../components/adminDashboard/AdminCollectTriageSection";
import AdminAttentionQueueSection from "../components/adminDashboard/AdminAttentionQueueSection";
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

export default function AdminDashboardPage() {
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
  const jumpToSection = useAdminDashboardJump();
  const {
    latestGeneratedAt,
    openCircuitCount,
    retryGroupCount,
    recoveredGroupCount,
    wrapperMissingAllStandardCodes,
    wrapperRecommendationObservationStatus,
    wrapperActiveBaselineReuseLabel,
    wrapperAttentionFeedStatus,
    wrapperAttentionFeedItemCount,
    wrapperAttentionFeedPrimaryTitle,
    wrapperAttentionFeedPrimaryKey,
    wrapperAttentionFeedPrimarySeverity,
    wrapperAttentionFeedPrimarySource,
    wrapperAttentionFeedPrimaryTargetId,
    wrapperAttentionFeedPrimaryActionLabel,
    wrapperAttentionFeedTone,
    wrapperMissingStandardCodeTone,
    wrapperMissingDeltaLabel,
    wrapperMissingDeltaColor,
    wrapperObservationChangeLabel,
    wrapperObservationChangeColor,
    wrapperSnapshotAlert,
    localAttentionQueueItems,
    attentionQueueItems,
    promotedAttentionItems,
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
    promotedAttentionItems,
    attentionQueueItems,
    latestGeneratedAt,
    failedSectionCount,
    openCircuitCount,
    retryGroupCount,
    recoveredGroupCount,
    wrapperMissingAllStandardCodes,
    wrapperMissingDeltaLabel,
    wrapperMissingDeltaColor,
    wrapperMissingStandardCodeTone,
    wrapperAttentionFeedPrimaryTitle,
    wrapperAttentionFeedItemCount,
    wrapperAttentionFeedPrimarySource,
    wrapperAttentionFeedPrimarySeverity,
    wrapperAttentionFeedTone,
    wrapperAttentionFeedPrimaryActionLabel,
    wrapperAttentionFeedPrimaryKey,
    wrapperAttentionFeedPrimaryTargetId,
    wrapperRecommendationObservationStatus,
    wrapperActiveBaselineReuseLabel,
    wrapperObservationChangeLabel,
    wrapperAttentionFeedStatus,
    wrapperObservationChangeColor,
    wrapperSnapshotAlert,
    standardCodeCoverage,
    standardCodeEffectObservation,
    wrapperObservation,
    officialCodebooks,
    breakdowns,
    collectFailures,
    searchFailures,
    onJumpToSection: jumpToSection,
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

        <Stack spacing={3} mt={3}>
          <AdminAttentionQueueSection {...attentionQueueSectionProps} />

          <AdminStandardCodeCoverageSection {...standardCodeCoverageSectionProps} />

          <AdminPolicyErrorReportsSection {...policyErrorReportsSectionProps} />

          <AdminSupportInquiriesSection {...supportInquiriesSectionProps} />

          <AdminPolicyDuplicateGroupsSection {...policyDuplicateGroupsSectionProps} />

          <AdminPolicyLinkReviewsSection {...policyLinkReviewsSectionProps} />

          <AdminStandardCodeEffectSection {...standardCodeEffectSectionProps} />

          <AdminWrapperObservationSection {...wrapperObservationSectionProps} />

          <AdminReferenceCodebooksSection {...referenceCodebooksSectionProps} />

          <AdminRecommendationOverviewSection {...recommendationOverviewSectionProps} />
          <AdminRecommendationRunSummarySection {...recommendationRunSummarySectionProps} />

          <AdminRecommendationBreakdownsSection {...recommendationBreakdownsSectionProps} />

          <AdminCollectTriageSection {...collectTriageSectionProps} />

          <AdminSearchTriageSection {...searchTriageSectionProps} />
        </Stack>
      </Box>
    </div>
  );
}
