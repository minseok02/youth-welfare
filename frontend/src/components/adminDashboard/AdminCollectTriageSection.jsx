import { Box } from "@mui/material";
import {
  MetricCard,
  SectionErrorCard,
  SectionLoadingCard,
  TriageSectionTitle,
} from "./AdminDashboardUi";
import AdminCollectTriageLists from "./AdminCollectTriageLists";
import {
  formatNumber,
} from "../../lib/adminDashboardDisplay";
import {
  ADMIN_DASHBOARD_ACTION_KEYS,
  ADMIN_DASHBOARD_FOCUS_KEYS,
  ADMIN_DASHBOARD_TEST_ATTRS,
  buildDashboardDataAttr,
} from "../../lib/adminDashboardTestHooks";

export default function AdminCollectTriageSection({
  collectFailuresQuery,
  collectErrorMessage,
  collectFailures,
  jumpToSection,
}) {
  return (
    <Box id="admin-collect-triage" sx={{ scrollMarginTop: 96 }}>
      <TriageSectionTitle
        eyebrow="수집 진단"
        title="수집 실패 상세"
        description="실패 작업, 회로 열림 상태, 최근 실패 샘플을 요약 카드 아래에서 바로 확인합니다."
      />

      {collectFailuresQuery.isLoading && (
        <SectionLoadingCard
          title="수집 실패 상세 로딩 중"
          description="수집 실패 API를 불러오는 중입니다."
        />
      )}

      {collectFailuresQuery.isError && (
        <SectionErrorCard
          title="수집 실패 상세 로드 실패"
          description="수집 진단만 실패한 경우 추천/검색 섹션은 그대로 확인할 수 있습니다."
          message={collectErrorMessage}
          onRetry={() => collectFailuresQuery.refetch()}
        />
      )}

      {collectFailures && (
        <>
          <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(4, 1fr)" } }}>
            <MetricCard
              title="실패 작업"
              value={formatNumber(collectFailures.failedJobsInWindow)}
              description={`최근 ${collectFailures.windowDays}일`}
            />
            <MetricCard
              title="부분 성공"
              value={formatNumber(collectFailures.partialSuccessJobsInWindow)}
              description="일부 저장 후 종료된 작업"
              actionLabel="부분 성공 보기"
              actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminActionKey, ADMIN_DASHBOARD_ACTION_KEYS.collectPartialView)}
              onAction={() => jumpToSection("admin-collect-triage", { tone: "info", focusKey: ADMIN_DASHBOARD_FOCUS_KEYS.collectPartial, preset: "metricAction" })}
            />
            <MetricCard
              title="열린 회로"
              value={formatNumber(collectFailures.circuitStatuses?.filter((item) => item.open).length)}
              description={`추적 중 ${formatNumber(collectFailures.circuitStatuses?.length)}`}
              actionLabel="회로 상태 보기"
              actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminActionKey, ADMIN_DASHBOARD_ACTION_KEYS.collectCircuitView)}
              onAction={() => jumpToSection("admin-collect-triage", { tone: "warning", focusKey: ADMIN_DASHBOARD_FOCUS_KEYS.collectCircuit, preset: "metricAction" })}
            />
            <MetricCard
              title="최근 실패 샘플"
              value={formatNumber(collectFailures.recentSamples?.length)}
              description="상세 샘플 미리보기"
              actionLabel="실패 샘플 보기"
              actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminActionKey, ADMIN_DASHBOARD_ACTION_KEYS.collectFailureSampleView)}
              onAction={() => jumpToSection("admin-collect-triage", { tone: "warning", focusKey: ADMIN_DASHBOARD_FOCUS_KEYS.default, preset: "metricAction" })}
            />
          </Box>

          <AdminCollectTriageLists collectFailures={collectFailures} />
        </>
      )}
    </Box>
  );
}
