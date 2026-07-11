import { useQuery } from "@tanstack/react-query";
import {
  fetchAdminDashboardAttentionFeed,
  fetchAdminDashboardBreakdowns,
  fetchAdminDashboardCollectFailures,
  fetchAdminDashboardNotificationAttemptSummary,
  fetchAdminDashboardNotificationStaleTargets,
  fetchAdminDashboardPolicyDuplicateGroups,
  fetchAdminDashboardPolicyErrorReports,
  fetchAdminDashboardPolicyFieldCorrections,
  fetchAdminDashboardPolicyLinkReviews,
  fetchAdminDashboardPolicyRegionCorrections,
  fetchAdminDashboardRecommendationRunSummary,
  fetchAdminDashboardRegionOptions,
  fetchAdminDashboardSearchFailures,
  fetchAdminDashboardStandardCodeEffectObservation,
  fetchAdminDashboardSummary,
  fetchAdminDashboardSupportInquiries,
  fetchAdminDashboardUserProfileStandardCodeCoverage,
  fetchAdminDashboardWrapperObservation,
  fetchOfficialCodebookDetail,
  fetchOfficialCodebooks,
} from "./adminDashboardApi";

const QUERY_BASE_OPTIONS = {
  staleTime: 30_000,
  retry: false,
};

const errorMessage = (query, fallback) => query.error?.response?.data?.message ?? fallback;

export function useAdminDashboardQueries({
  windowDays,
  selectedCodeSetKey,
  codebookQueryText,
  policyErrorStatusFilter,
  supportInquiryStatusFilter,
  policyDuplicateStatusFilter,
  policyLinkStatusFilter,
  notificationStaleDays,
}) {
  const summaryQuery = useQuery({
    queryKey: ["admin-dashboard-summary", windowDays],
    queryFn: () => fetchAdminDashboardSummary(windowDays),
    ...QUERY_BASE_OPTIONS,
  });

  const breakdownQuery = useQuery({
    queryKey: ["admin-dashboard-breakdowns", windowDays],
    queryFn: () => fetchAdminDashboardBreakdowns(windowDays),
    ...QUERY_BASE_OPTIONS,
  });

  const recommendationRunSummaryQuery = useQuery({
    queryKey: ["admin-dashboard-recommendation-run-summary", windowDays],
    queryFn: () => fetchAdminDashboardRecommendationRunSummary(windowDays),
    ...QUERY_BASE_OPTIONS,
  });

  const collectFailuresQuery = useQuery({
    queryKey: ["admin-dashboard-collect-failures", windowDays],
    queryFn: () => fetchAdminDashboardCollectFailures(windowDays),
    ...QUERY_BASE_OPTIONS,
  });

  const searchFailuresQuery = useQuery({
    queryKey: ["admin-dashboard-search-failures", windowDays],
    queryFn: () => fetchAdminDashboardSearchFailures(windowDays),
    ...QUERY_BASE_OPTIONS,
  });

  const standardCodeCoverageQuery = useQuery({
    queryKey: ["admin-dashboard-standard-code-coverage"],
    queryFn: fetchAdminDashboardUserProfileStandardCodeCoverage,
    ...QUERY_BASE_OPTIONS,
  });

  const attentionFeedQuery = useQuery({
    queryKey: ["admin-dashboard-attention-feed"],
    queryFn: fetchAdminDashboardAttentionFeed,
    ...QUERY_BASE_OPTIONS,
  });

  const standardCodeEffectObservationQuery = useQuery({
    queryKey: ["admin-dashboard-standard-code-effect-observation"],
    queryFn: fetchAdminDashboardStandardCodeEffectObservation,
    ...QUERY_BASE_OPTIONS,
  });

  const wrapperObservationQuery = useQuery({
    queryKey: ["admin-dashboard-wrapper-observation"],
    queryFn: fetchAdminDashboardWrapperObservation,
    ...QUERY_BASE_OPTIONS,
  });

  const policyErrorReportsQuery = useQuery({
    queryKey: ["admin-dashboard-policy-error-reports", policyErrorStatusFilter],
    queryFn: () => fetchAdminDashboardPolicyErrorReports(policyErrorStatusFilter),
    ...QUERY_BASE_OPTIONS,
  });

  const regionOptionsQuery = useQuery({
    queryKey: ["admin-dashboard-region-options"],
    queryFn: fetchAdminDashboardRegionOptions,
    ...QUERY_BASE_OPTIONS,
  });

  const policyRegionCorrectionsQuery = useQuery({
    queryKey: ["admin-dashboard-policy-region-corrections"],
    queryFn: fetchAdminDashboardPolicyRegionCorrections,
    ...QUERY_BASE_OPTIONS,
  });

  const policyFieldCorrectionsQuery = useQuery({
    queryKey: ["admin-dashboard-policy-field-corrections"],
    queryFn: fetchAdminDashboardPolicyFieldCorrections,
    ...QUERY_BASE_OPTIONS,
  });

  const supportInquiriesQuery = useQuery({
    queryKey: ["admin-dashboard-support-inquiries", supportInquiryStatusFilter],
    queryFn: () => fetchAdminDashboardSupportInquiries(supportInquiryStatusFilter),
    ...QUERY_BASE_OPTIONS,
  });

  const policyDuplicateGroupsQuery = useQuery({
    queryKey: ["admin-dashboard-policy-duplicate-groups", policyDuplicateStatusFilter],
    queryFn: () => fetchAdminDashboardPolicyDuplicateGroups(policyDuplicateStatusFilter),
    ...QUERY_BASE_OPTIONS,
  });

  const policyLinkReviewsQuery = useQuery({
    queryKey: ["admin-dashboard-policy-link-reviews", policyLinkStatusFilter],
    queryFn: () => fetchAdminDashboardPolicyLinkReviews(policyLinkStatusFilter),
    ...QUERY_BASE_OPTIONS,
  });

  const notificationStaleTargetsQuery = useQuery({
    queryKey: ["admin-dashboard-notification-stale-targets", notificationStaleDays],
    queryFn: () => fetchAdminDashboardNotificationStaleTargets(notificationStaleDays),
    ...QUERY_BASE_OPTIONS,
  });

  const notificationAttemptSummaryQuery = useQuery({
    queryKey: ["admin-dashboard-notification-attempt-summary", windowDays],
    queryFn: () => fetchAdminDashboardNotificationAttemptSummary(windowDays),
    ...QUERY_BASE_OPTIONS,
  });

  const officialCodebooksQuery = useQuery({
    queryKey: ["official-codebooks"],
    queryFn: fetchOfficialCodebooks,
    ...QUERY_BASE_OPTIONS,
  });

  const officialCodebooks = officialCodebooksQuery.data ?? [];
  const effectiveSelectedCodeSetKey = selectedCodeSetKey || officialCodebooks[0]?.codeSetKey || "";

  const officialCodebookDetailQuery = useQuery({
    queryKey: ["official-codebook-detail", effectiveSelectedCodeSetKey, codebookQueryText],
    queryFn: () => fetchOfficialCodebookDetail(effectiveSelectedCodeSetKey, codebookQueryText),
    enabled: Boolean(effectiveSelectedCodeSetKey),
    ...QUERY_BASE_OPTIONS,
  });

  const summaryData = summaryQuery.data;
  const recommendationSummary = summaryData?.recommendation;
  const concentration = recommendationSummary?.latestBatchConcentration;
  const breakdowns = breakdownQuery.data;
  const recommendationRunSummary = recommendationRunSummaryQuery.data;
  const collectFailures = collectFailuresQuery.data;
  const searchFailures = searchFailuresQuery.data;
  const standardCodeCoverage = standardCodeCoverageQuery.data;
  const attentionFeed = attentionFeedQuery.data;
  const standardCodeEffectObservation = standardCodeEffectObservationQuery.data;
  const wrapperObservation = wrapperObservationQuery.data;
  const policyErrorReports = policyErrorReportsQuery.data;
  const regionOptions = regionOptionsQuery.data?.regions ?? [];
  const policyRegionCorrections = policyRegionCorrectionsQuery.data;
  const policyFieldCorrections = policyFieldCorrectionsQuery.data;
  const supportInquiries = supportInquiriesQuery.data;
  const policyDuplicateGroups = policyDuplicateGroupsQuery.data;
  const policyLinkReviews = policyLinkReviewsQuery.data;
  const notificationStaleTargets = notificationStaleTargetsQuery.data;
  const notificationAttemptSummary = notificationAttemptSummaryQuery.data;
  const selectedCodebookSummary = officialCodebooks.find((item) => item.codeSetKey === effectiveSelectedCodeSetKey) ?? null;
  const officialCodebookDetail = officialCodebookDetailQuery.data;
  const detailRows = officialCodebookDetail?.rows ?? officialCodebookDetail?.metadata?.sampleRows ?? [];

  const failedSectionCount = [
    summaryQuery.isError,
    breakdownQuery.isError,
    recommendationRunSummaryQuery.isError,
    collectFailuresQuery.isError,
    searchFailuresQuery.isError,
    standardCodeCoverageQuery.isError,
    attentionFeedQuery.isError,
    standardCodeEffectObservationQuery.isError,
    wrapperObservationQuery.isError,
    policyErrorReportsQuery.isError,
    regionOptionsQuery.isError,
    policyRegionCorrectionsQuery.isError,
    policyFieldCorrectionsQuery.isError,
    supportInquiriesQuery.isError,
    policyDuplicateGroupsQuery.isError,
    policyLinkReviewsQuery.isError,
    notificationStaleTargetsQuery.isError,
    notificationAttemptSummaryQuery.isError,
    officialCodebooksQuery.isError,
    officialCodebookDetailQuery.isError,
  ].filter(Boolean).length;

  const isRefreshing = [
    summaryQuery.isFetching,
    breakdownQuery.isFetching,
    recommendationRunSummaryQuery.isFetching,
    collectFailuresQuery.isFetching,
    searchFailuresQuery.isFetching,
    standardCodeCoverageQuery.isFetching,
    attentionFeedQuery.isFetching,
    standardCodeEffectObservationQuery.isFetching,
    wrapperObservationQuery.isFetching,
    policyErrorReportsQuery.isFetching,
    regionOptionsQuery.isFetching,
    policyRegionCorrectionsQuery.isFetching,
    policyFieldCorrectionsQuery.isFetching,
    supportInquiriesQuery.isFetching,
    policyDuplicateGroupsQuery.isFetching,
    policyLinkReviewsQuery.isFetching,
    notificationStaleTargetsQuery.isFetching,
    notificationAttemptSummaryQuery.isFetching,
    officialCodebooksQuery.isFetching,
    officialCodebookDetailQuery.isFetching,
  ].some(Boolean);

  const refetchAll = () => {
    summaryQuery.refetch();
    breakdownQuery.refetch();
    recommendationRunSummaryQuery.refetch();
    collectFailuresQuery.refetch();
    searchFailuresQuery.refetch();
    standardCodeCoverageQuery.refetch();
    attentionFeedQuery.refetch();
    standardCodeEffectObservationQuery.refetch();
    wrapperObservationQuery.refetch();
    policyErrorReportsQuery.refetch();
    regionOptionsQuery.refetch();
    policyRegionCorrectionsQuery.refetch();
    policyFieldCorrectionsQuery.refetch();
    supportInquiriesQuery.refetch();
    policyDuplicateGroupsQuery.refetch();
    policyLinkReviewsQuery.refetch();
    notificationStaleTargetsQuery.refetch();
    notificationAttemptSummaryQuery.refetch();
    officialCodebooksQuery.refetch();
    if (effectiveSelectedCodeSetKey) {
      officialCodebookDetailQuery.refetch();
    }
  };

  return {
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
    summaryErrorMessage: errorMessage(summaryQuery, "요약 데이터를 불러오지 못했습니다."),
    breakdownErrorMessage: errorMessage(breakdownQuery, "추천 상세 triage를 불러오지 못했습니다."),
    recommendationRunSummaryErrorMessage: errorMessage(recommendationRunSummaryQuery, "추천 실행 로그 요약을 불러오지 못했습니다."),
    collectErrorMessage: errorMessage(collectFailuresQuery, "수집 실패 상세를 불러오지 못했습니다."),
    searchErrorMessage: errorMessage(searchFailuresQuery, "검색 실패 상세를 불러오지 못했습니다."),
    standardCodeCoverageErrorMessage: errorMessage(standardCodeCoverageQuery, "표준코드 입력 현황을 불러오지 못했습니다."),
    attentionFeedErrorMessage: errorMessage(attentionFeedQuery, "운영 알림 항목을 불러오지 못했습니다."),
    standardCodeEffectObservationErrorMessage: errorMessage(standardCodeEffectObservationQuery, "표준코드 추천 효과 관측값을 불러오지 못했습니다."),
    wrapperObservationErrorMessage: errorMessage(wrapperObservationQuery, "상위 wrapper 관측값을 불러오지 못했습니다."),
    policyErrorReportsErrorMessage: errorMessage(policyErrorReportsQuery, "정책 오류 제보 목록을 불러오지 못했습니다."),
    regionOptionsErrorMessage: errorMessage(regionOptionsQuery, "지역 옵션을 불러오지 못했습니다."),
    policyRegionCorrectionsErrorMessage: errorMessage(policyRegionCorrectionsQuery, "지역 보정 목록을 불러오지 못했습니다."),
    policyFieldCorrectionsErrorMessage: errorMessage(policyFieldCorrectionsQuery, "필드 보정 목록을 불러오지 못했습니다."),
    supportInquiriesErrorMessage: errorMessage(supportInquiriesQuery, "서비스 문의 목록을 불러오지 못했습니다."),
    policyDuplicateGroupsErrorMessage: errorMessage(policyDuplicateGroupsQuery, "정책 중복 review 목록을 불러오지 못했습니다."),
    policyLinkReviewsErrorMessage: errorMessage(policyLinkReviewsQuery, "정책 링크 review 목록을 불러오지 못했습니다."),
    notificationStaleTargetsErrorMessage: errorMessage(notificationStaleTargetsQuery, "stale notification target 목록을 불러오지 못했습니다."),
    notificationAttemptSummaryErrorMessage: errorMessage(notificationAttemptSummaryQuery, "알림 attempt 요약을 불러오지 못했습니다."),
    officialCodebooksErrorMessage: errorMessage(officialCodebooksQuery, "공식 코드북 목록을 불러오지 못했습니다."),
    officialCodebookDetailErrorMessage: errorMessage(officialCodebookDetailQuery, "선택한 코드북 상세를 불러오지 못했습니다."),
  };
}
