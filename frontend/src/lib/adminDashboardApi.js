import api from "./axios";

const E2E_FAILURE_STORAGE_KEY = "__ADMIN_DASHBOARD_E2E_FAIL__";

const readE2EFailureMode = () => {
  if (!import.meta.env.DEV || typeof window === "undefined") return null;
  try {
    return window.localStorage.getItem(E2E_FAILURE_STORAGE_KEY);
  } catch {
    return null;
  }
};

const maybeThrowE2EFailure = (section) => {
  if (readE2EFailureMode() !== section) return;
  const message = `${section} forced failure`;
  const error = new Error(message);
  error.response = { data: { message } };
  throw error;
};

export const fetchAdminDashboardSummary = async (windowDays) => {
  maybeThrowE2EFailure("summary");
  const { data } = await api.get("/api/admin/dashboard/summary", {
    params: { summaryWindowDays: windowDays, trendWindowDays: [1, 7, 30] },
  });
  return data?.data;
};

export const fetchAdminDashboardBreakdowns = async (windowDays) => {
  maybeThrowE2EFailure("breakdown");
  const { data } = await api.get("/api/admin/dashboard/recommendation-breakdowns", {
    params: { summaryWindowDays: windowDays, limit: 3 },
  });
  return data?.data;
};

export const fetchAdminDashboardRecommendationRunSummary = async (windowDays) => {
  const { data } = await api.get("/api/admin/dashboard/recommendation-run-summary", {
    params: { summaryWindowDays: windowDays, limit: 5 },
  });
  return data?.data;
};

export const fetchAdminDashboardCollectFailures = async (windowDays) => {
  const { data } = await api.get("/api/admin/dashboard/collect-failures", {
    params: { summaryWindowDays: windowDays, limit: 5 },
  });
  return data?.data;
};

export const fetchAdminDashboardSearchFailures = async (windowDays) => {
  const { data } = await api.get("/api/admin/dashboard/search-failures", {
    params: { summaryWindowDays: windowDays, limit: 5 },
  });
  return data?.data;
};

export const fetchAdminDashboardUserProfileStandardCodeCoverage = async () => {
  const { data } = await api.get("/api/admin/dashboard/user-profile-standard-code-coverage");
  return data?.data;
};

export const fetchAdminDashboardAttentionFeed = async () => {
  const { data } = await api.get("/api/admin/dashboard/attention-feed");
  return data?.data;
};

export const fetchAdminDashboardStandardCodeEffectObservation = async () => {
  const { data } = await api.get("/api/admin/dashboard/standard-code-effect-observation");
  return data?.data;
};

export const fetchAdminDashboardWrapperObservation = async () => {
  const { data } = await api.get("/api/admin/dashboard/wrapper-observation");
  return data?.data;
};

export const fetchAdminDashboardPolicyErrorReports = async (status = "OPEN") => {
  const { data } = await api.get("/api/admin/dashboard/policy-error-reports", {
    params: { limit: 5, status },
  });
  return data?.data;
};

export const fetchAdminDashboardRegionOptions = async () => {
  const { data } = await api.get("/api/admin/dashboard/region-options");
  return data?.data;
};

export const fetchAdminDashboardPolicyRegionCorrections = async () => {
  const { data } = await api.get("/api/admin/dashboard/policy-region-corrections", {
    params: { limit: 20, activeOnly: false },
  });
  return data?.data;
};

export const fetchAdminDashboardPolicyFieldCorrections = async () => {
  const { data } = await api.get("/api/admin/dashboard/policy-field-corrections", {
    params: { limit: 20 },
  });
  return data?.data;
};

export const fetchAdminDashboardSupportInquiries = async (status = "OPEN") => {
  const { data } = await api.get("/api/admin/dashboard/support-inquiries", {
    params: { limit: 5, status },
  });
  return data?.data;
};

export const fetchAdminDashboardPolicyDuplicateGroups = async (status = "OPEN") => {
  const { data } = await api.get("/api/admin/dashboard/policy-duplicate-groups", {
    params: { limit: 5, status },
  });
  return data?.data;
};

export const fetchAdminDashboardPolicyLinkReviews = async (status = "OPEN") => {
  const { data } = await api.get("/api/admin/dashboard/policy-link-reviews", {
    params: { limit: 5, status },
  });
  return data?.data;
};

export const fetchAdminDashboardNotificationStaleTargets = async (olderThanDays = 14) => {
  const { data } = await api.get("/api/admin/dashboard/notification-stale-targets", {
    params: { limit: 5, olderThanDays },
  });
  return data?.data;
};

export const fetchAdminDashboardNotificationAttemptSummary = async (windowDays) => {
  const { data } = await api.get("/api/admin/dashboard/notification-attempt-summary", {
    params: { summaryWindowDays: windowDays, limit: 5 },
  });
  return data?.data;
};

export const fetchOfficialCodebooks = async () => {
  const { data } = await api.get("/api/reference/official-codes");
  return data?.data ?? [];
};

export const fetchOfficialCodebookDetail = async (codeSetKey, queryText) => {
  const { data } = await api.get(`/api/reference/official-codes/${codeSetKey}`, {
    params: {
      q: queryText?.trim() || undefined,
      limit: 20,
    },
  });
  return data?.data;
};

export const reviewAdminDashboardPolicyErrorReport = async (reportId, request) => {
  const { data } = await api.post(`/api/admin/dashboard/policy-error-reports/${reportId}/review`, request);
  return data?.data;
};

export const applyAdminDashboardPolicyRegionCorrection = async (request) => {
  const { data } = await api.post("/api/admin/dashboard/policy-region-corrections", request);
  return data?.data;
};

export const runAdminDashboardPolicyRegionAudit = async (limit = 50000) => {
  const { data } = await api.post("/api/admin/dashboard/policy-region-audit/run", null, {
    params: { limit },
  });
  return data?.data;
};

export const applyAdminDashboardPolicyFieldCorrection = async (request) => {
  const { data } = await api.post("/api/admin/dashboard/policy-field-corrections", request);
  return data?.data;
};

export const revertAdminDashboardPolicyRegionCorrection = async (correctionId, request) => {
  const { data } = await api.post(`/api/admin/dashboard/policy-region-corrections/${correctionId}/revert`, request);
  return data?.data;
};

export const reviewAdminDashboardSupportInquiry = async (inquiryId, request) => {
  const { data } = await api.post(`/api/admin/dashboard/support-inquiries/${inquiryId}/review`, request);
  return data?.data;
};

export const reviewAdminDashboardPolicyDuplicateGroup = async (request) => {
  const { data } = await api.post("/api/admin/dashboard/policy-duplicate-groups/review", request);
  return data?.data;
};

export const reviewAdminDashboardPolicyLink = async (serviceId, request) => {
  const { data } = await api.post(`/api/admin/dashboard/policy-link-reviews/${serviceId}/review`, request);
  return data?.data;
};

export const hideAdminDashboardNotificationStaleBacklog = async (request) => {
  const { data } = await api.post("/api/admin/dashboard/notification-backlog/hide-stale", request);
  return data?.data;
};
