import {
  Box,
} from "@mui/material";
import {
  SectionErrorCard,
  SectionLoadingCard,
} from "./AdminDashboardUi";
import AdminNotificationAttemptSummarySection from "./AdminNotificationAttemptSummarySection";
import AdminNotificationStaleTargetsSection from "./AdminNotificationStaleTargetsSection";
import {
  OpsSummaryMetrics,
  PolicyTriageSummary,
  RecentOpsSamples,
  RecommendationSignalMetrics,
  RecommendationStatusHero,
  TopLeaderUserMixCard,
} from "./AdminRecommendationOverviewPanels";

export default function AdminRecommendationOverviewSection({
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
  onNotificationStaleDaysChange,
  reviewSubmittingKey,
  onHideNotificationStaleTarget,
}) {
  return (
    <>
      {summaryQuery.isLoading && (
        <SectionLoadingCard
          title="운영 요약 로딩 중"
          description="추천 검토 상태, 최근 추천 집중도, 수집/검색 개요를 불러오는 중입니다."
        />
      )}

      {summaryQuery.isError && (
        <SectionErrorCard
          title="운영 요약 로드 실패"
          description="요약 API가 실패해도 수집/검색 상세 진단은 아래에서 계속 확인할 수 있습니다."
          message={summaryErrorMessage}
          onRetry={() => summaryQuery.refetch()}
        />
      )}

      {summaryData && recommendationSummary && concentration && (
        <Box id="admin-recommendation-overview" sx={{ scrollMarginTop: 96 }}>
          <RecommendationStatusHero
            recommendationSummary={recommendationSummary}
            concentration={concentration}
          />

          <RecommendationSignalMetrics
            recommendationSummary={recommendationSummary}
            concentration={concentration}
          />

          <OpsSummaryMetrics summaryData={summaryData} />

          <PolicyTriageSummary policyTriage={summaryData.policyTriage} />

          <AdminNotificationAttemptSummarySection
            notificationAttemptSummaryQuery={notificationAttemptSummaryQuery}
            notificationAttemptSummaryErrorMessage={notificationAttemptSummaryErrorMessage}
            notificationAttemptSummary={notificationAttemptSummary}
          />

          <AdminNotificationStaleTargetsSection
            notificationStaleTargetsQuery={notificationStaleTargetsQuery}
            notificationStaleTargetsErrorMessage={notificationStaleTargetsErrorMessage}
            notificationStaleTargets={notificationStaleTargets}
            notificationStaleDays={notificationStaleDays}
            onNotificationStaleDaysChange={onNotificationStaleDaysChange}
            reviewSubmittingKey={reviewSubmittingKey}
            onHideNotificationStaleTarget={onHideNotificationStaleTarget}
          />

          <TopLeaderUserMixCard
            recommendationSummary={recommendationSummary}
            concentration={concentration}
          />

          <RecentOpsSamples summaryData={summaryData} />
        </Box>
      )}
    </>
  );
}
