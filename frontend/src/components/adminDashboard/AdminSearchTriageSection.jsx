import { Box } from "@mui/material";
import {
  MetricCard,
  SectionErrorCard,
  SectionLoadingCard,
  TriageSectionTitle,
} from "./AdminDashboardUi";
import AdminSearchTriageLists from "./AdminSearchTriageLists";
import { formatNumber } from "../../lib/adminDashboardDisplay";
import {
  ADMIN_DASHBOARD_ACTION_KEYS,
  ADMIN_DASHBOARD_FOCUS_KEYS,
  ADMIN_DASHBOARD_TEST_ATTRS,
  buildDashboardDataAttr,
} from "../../lib/adminDashboardTestHooks";

export default function AdminSearchTriageSection({
  searchFailuresQuery,
  searchErrorMessage,
  searchFailures,
  jumpToSection,
}) {
  return (
    <Box id="admin-search-triage" sx={{ scrollMarginTop: 96 }}>
      <TriageSectionTitle
        eyebrow="검색 진단"
        title="검색 실패 상세"
        description="0건 검색 패턴, 재시도 묶음, 이후 결과 복구 여부를 같은 페이지에서 바로 확인합니다."
      />

      {searchFailuresQuery.isLoading && (
        <SectionLoadingCard
          title="검색 실패 상세 로딩 중"
          description="검색 실패 API를 불러오는 중입니다."
        />
      )}

      {searchFailuresQuery.isError && (
        <SectionErrorCard
          title="검색 실패 상세 로드 실패"
          description="검색 진단만 실패한 경우 추천/수집 섹션은 그대로 확인할 수 있습니다."
          message={searchErrorMessage}
          onRetry={() => searchFailuresQuery.refetch()}
        />
      )}

      {searchFailures && (
        <>
          <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(4, 1fr)" } }}>
            <MetricCard
              title="0건 검색"
              value={formatNumber(searchFailures.zeroResultSearchesInWindow)}
              description={`최근 ${searchFailures.windowDays}일`}
              actionLabel="실패 샘플 보기"
              actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminActionKey, ADMIN_DASHBOARD_ACTION_KEYS.searchWarningView)}
              onAction={() => jumpToSection("admin-search-triage", { tone: "warning", focusKey: ADMIN_DASHBOARD_FOCUS_KEYS.searchWarning, preset: "metricAction" })}
            />
            <MetricCard
              title="재시도 묶음"
              value={formatNumber(searchFailures.retryGroups?.length)}
              description="동일 조건 반복 검색"
            />
            <MetricCard
              title="복구된 묶음"
              value={formatNumber(searchFailures.recoveredSearchGroups?.length)}
              description="0건 후 결과 복구"
              actionLabel="복구 묶음 보기"
              actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminActionKey, ADMIN_DASHBOARD_ACTION_KEYS.searchRecoveredView)}
              onAction={() => jumpToSection("admin-search-triage", { tone: "success", focusKey: ADMIN_DASHBOARD_FOCUS_KEYS.searchRecovered, preset: "metricAction" })}
            />
            <MetricCard
              title="최근 검색 샘플"
              value={formatNumber(searchFailures.recentSamples?.length)}
              description="실패 샘플 미리보기"
            />
          </Box>

          <AdminSearchTriageLists searchFailures={searchFailures} />
        </>
      )}
    </Box>
  );
}
