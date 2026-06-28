import { useState } from "react";
import {
  applyAdminDashboardPolicyFieldCorrection,
  applyAdminDashboardPolicyRegionCorrection,
  hideAdminDashboardNotificationStaleBacklog,
  reviewAdminDashboardPolicyDuplicateGroup,
  reviewAdminDashboardPolicyErrorReport,
  reviewAdminDashboardPolicyLink,
  reviewAdminDashboardSupportInquiry,
  revertAdminDashboardPolicyRegionCorrection,
  runAdminDashboardPolicyRegionAudit,
} from "./adminDashboardApi";
import {
  parseRegionCorrectionCodes,
  resolveFieldCorrectionType,
} from "./adminDashboardDisplay";

export function useAdminDashboardActions({
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
}) {
  const [policyErrorReviewNotes, setPolicyErrorReviewNotes] = useState({});
  const [policyRegionCorrectionInputs, setPolicyRegionCorrectionInputs] = useState({});
  const [policyFieldCorrectionInputs, setPolicyFieldCorrectionInputs] = useState({});
  const [latestRegionAuditResult, setLatestRegionAuditResult] = useState(null);
  const [supportInquiryReviewNotes, setSupportInquiryReviewNotes] = useState({});
  const [policyDuplicateReviewNotes, setPolicyDuplicateReviewNotes] = useState({});
  const [policyLinkReviewNotes, setPolicyLinkReviewNotes] = useState({});
  const [reviewSubmittingKey, setReviewSubmittingKey] = useState(null);

  const updatePolicyRegionCorrectionInput = (reportId, patch) => {
    setPolicyRegionCorrectionInputs((prev) => ({
      ...prev,
      [reportId]: {
        ...(prev[reportId] ?? {}),
        ...patch,
      },
    }));
  };

  const updatePolicyFieldCorrectionInput = (reportId, patch) => {
    setPolicyFieldCorrectionInputs((prev) => ({
      ...prev,
      [reportId]: {
        ...(prev[reportId] ?? {}),
        ...patch,
      },
    }));
  };

  const clearPolicyErrorInputs = (reportId) => {
    setPolicyErrorReviewNotes((prev) => {
      const next = { ...prev };
      delete next[reportId];
      return next;
    });
    setPolicyRegionCorrectionInputs((prev) => {
      const next = { ...prev };
      delete next[reportId];
      return next;
    });
    setPolicyFieldCorrectionInputs((prev) => {
      const next = { ...prev };
      delete next[reportId];
      return next;
    });
  };

  const handleReviewPolicyErrorReport = async (reportId) => {
    setReviewSubmittingKey(`policy-${reportId}`);
    try {
      await reviewAdminDashboardPolicyErrorReport(reportId, {
        reviewNote: policyErrorReviewNotes[reportId]?.trim() || null,
      });
      setPolicyErrorReviewNotes((prev) => {
        const next = { ...prev };
        delete next[reportId];
        return next;
      });
      policyErrorReportsQuery.refetch();
      policyRegionCorrectionsQuery.refetch();
      attentionFeedQuery.refetch();
    } finally {
      setReviewSubmittingKey(null);
    }
  };

  const handleApplyPolicyRegionCorrection = async (item, nationwide = false) => {
    const input = policyRegionCorrectionInputs[item.reportId] ?? {};
    const selectedRegionCodes = Array.isArray(input.selectedRegionCodes) ? input.selectedRegionCodes : [];
    const regionCodes = nationwide ? [] : (
      selectedRegionCodes.length > 0 ? selectedRegionCodes : parseRegionCorrectionCodes(input.regionCodes)
    );
    if (!nationwide && regionCodes.length === 0) {
      window.alert("지역코드를 입력하세요.");
      return;
    }
    const requestKey = `policy-region-${item.reportId}-${nationwide ? "nationwide" : "regions"}`;
    setReviewSubmittingKey(requestKey);
    try {
      await applyAdminDashboardPolicyRegionCorrection({
        policyId: item.policyId,
        nationwide,
        regionCodes,
        reportId: item.reportId,
        correctionNote: input.correctionNote?.trim()
          || policyErrorReviewNotes[item.reportId]?.trim()
          || null,
      });
      clearPolicyErrorInputs(item.reportId);
      policyErrorReportsQuery.refetch();
      policyRegionCorrectionsQuery.refetch();
      attentionFeedQuery.refetch();
    } finally {
      setReviewSubmittingKey(null);
    }
  };

  const handleRunPolicyRegionAudit = async () => {
    setReviewSubmittingKey("policy-region-audit");
    try {
      const result = await runAdminDashboardPolicyRegionAudit(50000);
      setLatestRegionAuditResult(result ?? null);
      policyErrorReportsQuery.refetch();
      attentionFeedQuery.refetch();
    } finally {
      setReviewSubmittingKey(null);
    }
  };

  const handleApplySuggestedRegionCorrection = async (item, regionCodes) => {
    if (!regionCodes || regionCodes.length === 0) {
      return;
    }
    const requestKey = `policy-region-${item.reportId}-suggested`;
    setReviewSubmittingKey(requestKey);
    try {
      await applyAdminDashboardPolicyRegionCorrection({
        policyId: item.policyId,
        nationwide: false,
        regionCodes,
        reportId: item.reportId,
        correctionNote: policyErrorReviewNotes[item.reportId]?.trim() || "자동 지역감사 후보 적용",
      });
      clearPolicyErrorInputs(item.reportId);
      policyErrorReportsQuery.refetch();
      policyRegionCorrectionsQuery.refetch();
      attentionFeedQuery.refetch();
    } finally {
      setReviewSubmittingKey(null);
    }
  };

  const handleApplyPolicyFieldCorrection = async (item) => {
    const correctionType = resolveFieldCorrectionType(item.reasonCode);
    if (!correctionType) {
      return;
    }
    const input = policyFieldCorrectionInputs[item.reportId] ?? {};
    const requestKey = `policy-field-${item.reportId}`;
    setReviewSubmittingKey(requestKey);
    try {
      await applyAdminDashboardPolicyFieldCorrection({
        policyId: item.policyId,
        reportId: item.reportId,
        correctionType,
        applyStartDate: input.applyStartDate || null,
        applyEndDate: input.applyEndDate || null,
        detailUrl: input.detailUrl?.trim() || null,
        eligibilityText: input.eligibilityText?.trim() || null,
        duplicateOfPolicyId: input.duplicateOfPolicyId ? Number(input.duplicateOfPolicyId) : null,
        correctionNote: input.correctionNote?.trim()
          || policyErrorReviewNotes[item.reportId]?.trim()
          || null,
      });
      clearPolicyErrorInputs(item.reportId);
      policyErrorReportsQuery.refetch();
      policyFieldCorrectionsQuery.refetch();
      attentionFeedQuery.refetch();
    } finally {
      setReviewSubmittingKey(null);
    }
  };

  const handleRevertPolicyRegionCorrection = async (correctionId) => {
    setReviewSubmittingKey(`policy-region-revert-${correctionId}`);
    try {
      await revertAdminDashboardPolicyRegionCorrection(correctionId, {
        reviewNote: "관리자 대시보드에서 수동 보정 되돌림",
      });
      policyRegionCorrectionsQuery.refetch();
      policyErrorReportsQuery.refetch();
      attentionFeedQuery.refetch();
    } finally {
      setReviewSubmittingKey(null);
    }
  };

  const handleReviewSupportInquiry = async (inquiryId) => {
    setReviewSubmittingKey(`support-${inquiryId}`);
    try {
      await reviewAdminDashboardSupportInquiry(inquiryId, {
        reviewNote: supportInquiryReviewNotes[inquiryId]?.trim() || null,
      });
      setSupportInquiryReviewNotes((prev) => {
        const next = { ...prev };
        delete next[inquiryId];
        return next;
      });
      supportInquiriesQuery.refetch();
      attentionFeedQuery.refetch();
    } finally {
      setReviewSubmittingKey(null);
    }
  };

  const handleReviewPolicyDuplicateGroup = async (item) => {
    const requestKey = `duplicate-${item.sourceType}-${item.title}-${item.hostOrgKey ?? ""}`;
    setReviewSubmittingKey(requestKey);
    try {
      await reviewAdminDashboardPolicyDuplicateGroup({
        sourceType: item.sourceType,
        title: item.title,
        hostOrgKey: item.hostOrgKey ?? "",
        hostOrgLabel: item.hostOrgLabel ?? null,
        reviewNote: policyDuplicateReviewNotes[requestKey]?.trim() || null,
      });
      setPolicyDuplicateReviewNotes((prev) => {
        const next = { ...prev };
        delete next[requestKey];
        return next;
      });
      policyDuplicateGroupsQuery.refetch();
      attentionFeedQuery.refetch();
    } finally {
      setReviewSubmittingKey(null);
    }
  };

  const handleReviewPolicyLink = async (serviceId) => {
    setReviewSubmittingKey(`policy-link-${serviceId}`);
    try {
      await reviewAdminDashboardPolicyLink(serviceId, {
        reviewNote: policyLinkReviewNotes[serviceId]?.trim() || null,
      });
      setPolicyLinkReviewNotes((prev) => {
        const next = { ...prev };
        delete next[serviceId];
        return next;
      });
      policyLinkReviewsQuery.refetch();
      attentionFeedQuery.refetch();
    } finally {
      setReviewSubmittingKey(null);
    }
  };

  const handleHideNotificationStaleTarget = async (item) => {
    const requestKey = `notification-stale-${item.kind}-${item.deeplinkUrl}`;
    setReviewSubmittingKey(requestKey);
    try {
      await hideAdminDashboardNotificationStaleBacklog({
        kind: item.kind,
        title: item.title,
        deeplinkUrl: item.deeplinkUrl,
        olderThanDays: notificationStaleTargets?.olderThanDays ?? notificationStaleDays,
      });
      notificationStaleTargetsQuery.refetch();
      summaryQuery.refetch();
      attentionFeedQuery.refetch();
    } finally {
      setReviewSubmittingKey(null);
    }
  };

  return {
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
  };
}
