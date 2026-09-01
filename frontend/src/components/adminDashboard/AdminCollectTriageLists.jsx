import {
  Box,
  Stack,
  Typography,
} from "@mui/material";
import {
  CompactListCard,
  ToneChip,
} from "./AdminDashboardUi";
import {
  ACCENT,
  COLLECT_EXECUTION_TONE,
  COLLECT_LANE_TONE,
  COLLECT_RESOURCE_TONE,
  INK,
  INK2,
  INK3,
  PANEL_LINE,
  SUCCESS_TEXT,
  WARNING_TEXT,
} from "./AdminDashboardUiTokens";
import {
  formatAdminOperationalText,
  formatAdminMessage,
  formatCodeOrStatus,
  formatCollectJobName,
  formatConfigLabel,
  formatConfigValue,
  formatDateTime,
  formatNumber,
  formatAdminRoutePath,
  formatStatusLabel,
} from "../../lib/adminDashboardDisplay";
import {
  ADMIN_DASHBOARD_FOCUS_KEYS,
  ADMIN_DASHBOARD_LIST_KEYS,
  ADMIN_DASHBOARD_TEST_ATTRS,
  buildDashboardDataAttr,
} from "../../lib/adminDashboardTestHooks";

export default function AdminCollectTriageLists({ collectFailures }) {
  const firstCollectCircuitKey =
    collectFailures?.circuitStatuses?.find((item) => item.open)?.circuitKey
    ?? collectFailures?.circuitStatuses?.[0]?.circuitKey
    ?? null;

  return (
    <>
      <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", xl: "1.1fr 0.9fr" } }}>
        <CompactListCard
          title="수집 레인 구성"
          description="야간 자동수집과 수동 레인 구성을 같은 화면에서 봅니다."
          items={collectFailures.collectSourceLanes}
          renderItem={(item) => (
            <Box key={item.laneKey} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
              <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" spacing={1.5}>
                <Box sx={{ minWidth: 0 }}>
                  <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK, overflowWrap: "anywhere", wordBreak: "break-word" }}>
                    {formatAdminOperationalText(item.label)}
                  </Typography>
                  <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25, overflowWrap: "anywhere", wordBreak: "break-word" }}>
                    {formatCollectJobName(item.laneKey)} · 실행 경로 {formatAdminRoutePath(item.triggerPath, "경로 정보 없음")}
                  </Typography>
                  <Typography sx={{ fontSize: 12, color: INK2, mt: 0.75, lineHeight: 1.5, overflowWrap: "anywhere", wordBreak: "break-word" }}>
                    {formatAdminOperationalText(item.governanceReason)}
                  </Typography>
                  {item.latestRun ? (
                    <Box mt={1}>
                      <Typography sx={{ fontSize: 12, fontWeight: 700, color: INK2 }}>
                        최근 실행 {formatStatusLabel(item.latestRun.status)} · {formatDateTime(item.latestRun.startedAt)}
                      </Typography>
                      <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25 }}>
                        요청 {formatNumber(item.latestRun.requestedCount)} / 저장 {formatNumber(item.latestRun.savedCount)} / 건너뜀 {formatNumber(item.latestRun.skippedCount)} / 실패 {formatNumber(item.latestRun.failedCount)}
                      </Typography>
                    </Box>
                  ) : (
                    <Typography sx={{ fontSize: 12, color: INK3, mt: 0.75 }}>
                      최근 실행 기록 없음
                    </Typography>
                  )}
                  {item.configEntries?.length ? (
                    <Box mt={1}>
                      {item.configEntries.map((entry) => (
                        <Typography
                          key={`${item.laneKey}-${entry.label}`}
                          sx={{ fontSize: 12, color: INK3, mt: 0.25, overflowWrap: "anywhere", wordBreak: "break-word" }}
                        >
                          {formatConfigLabel(entry.label)} · {formatConfigValue(entry.value)}
                        </Typography>
                      ))}
                    </Box>
                  ) : null}
                  {item.scheduleLabel && (
                    <Typography sx={{ fontSize: 12, color: INK3, mt: 0.75 }}>
                      {formatAdminOperationalText(item.scheduleLabel)}
                    </Typography>
                  )}
                </Box>
                <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap alignSelf="flex-start">
                  <ToneChip toneMap={COLLECT_EXECUTION_TONE} value={item.executionMode} />
                  <ToneChip toneMap={COLLECT_LANE_TONE} value={item.laneType} />
                  <ToneChip toneMap={COLLECT_RESOURCE_TONE} value={item.resourceProfile} />
                </Stack>
              </Stack>
            </Box>
          )}
        />
        <CompactListCard
          title="작업별 현황"
          description="실패/부분 성공이 많은 수집 작업"
          cardProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminListKey, ADMIN_DASHBOARD_LIST_KEYS.collectJobBreakdowns)}
          items={collectFailures.jobBreakdowns}
          renderItem={(item, index) => (
            <Box
              key={`${item.jobName}-${item.latestStartedAt}`}
              {...(index === 0 ? buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTarget, ADMIN_DASHBOARD_FOCUS_KEYS.collectPartial) : {})}
              {...(index === 0 ? buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTone, "info") : {})}
              sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}
            >
              <Stack direction="row" justifyContent="space-between" spacing={2}>
                <Box>
                  <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{formatCollectJobName(item.jobName)}</Typography>
                  <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25 }}>
                    최근 실행 {formatDateTime(item.latestStartedAt)}
                  </Typography>
                </Box>
                <Stack spacing={0.5} alignItems="flex-end">
                  <Typography sx={{ fontSize: 12, fontWeight: 800, color: WARNING_TEXT }}>
                    실패 {formatNumber(item.failedCount)}
                  </Typography>
                  <Typography sx={{ fontSize: 12, fontWeight: 700, color: INK2 }}>
                    부분 성공 {formatNumber(item.partialSuccessCount)}
                  </Typography>
                </Stack>
              </Stack>
            </Box>
          )}
        />
        <CompactListCard
          title="회로 상태"
          description="열린 회로는 외부 수집 안정성 저하를 뜻합니다."
          cardProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminListKey, ADMIN_DASHBOARD_LIST_KEYS.collectCircuitStatuses)}
          items={collectFailures.circuitStatuses}
          renderItem={(item) => (
            <Box
              key={item.circuitKey}
              {...(item.circuitKey === firstCollectCircuitKey ? buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTarget, ADMIN_DASHBOARD_FOCUS_KEYS.collectCircuit) : {})}
              {...(item.circuitKey === firstCollectCircuitKey ? buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTone, item.open ? "warning" : "success") : {})}
              sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: item.open ? "#fff7ed" : "#f8fafc" }}
            >
              <Stack direction="row" justifyContent="space-between" spacing={2}>
                <Box>
                  <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{formatCollectJobName(item.circuitKey)}</Typography>
                  <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25 }}>
                    {item.open ? "해제 예정" : "마지막 확인"} {formatDateTime(item.openUntil)}
                  </Typography>
                </Box>
                <Typography sx={{ fontSize: 12, fontWeight: 800, color: item.open ? WARNING_TEXT : SUCCESS_TEXT }}>
                  {item.open ? `열림 · ${formatNumber(item.remainingMs)}ms 남음` : "닫힘"}
                </Typography>
              </Stack>
            </Box>
          )}
        />
      </Box>

      <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", xl: "1fr 1fr 1fr" } }}>
        <CompactListCard
          title="오류 코드"
          description="실패 원인 상위 집계"
          items={collectFailures.errorCodeBreakdowns}
          renderItem={(item) => (
            <Box key={item.errorCode || "UNKNOWN"} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
              <Stack direction="row" justifyContent="space-between" spacing={2}>
                <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{formatCodeOrStatus(item.errorCode) || "미분류"}</Typography>
                <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT }}>{formatNumber(item.failedCount)}</Typography>
              </Stack>
            </Box>
          )}
        />
        <CompactListCard
          title="현재 연속 상태"
          description="같은 상태가 연속되는 작업"
          items={collectFailures.currentJobStreaks}
          renderItem={(item) => (
            <Box key={`${item.jobName}-${item.streakStatus}`} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
              <Stack direction="row" justifyContent="space-between" spacing={2}>
                <Box>
                  <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{formatCollectJobName(item.jobName)}</Typography>
                  <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25 }}>
                    {formatStatusLabel(item.streakStatus)} · 최근 {formatDateTime(item.latestStartedAt)}
                  </Typography>
                </Box>
                <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT }}>{formatNumber(item.streakCount)}</Typography>
              </Stack>
            </Box>
          )}
        />
        <CompactListCard
          title="최근 실패 샘플"
          description="에러 메시지와 저장 실패 규모"
          cardProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminListKey, ADMIN_DASHBOARD_LIST_KEYS.collectRecentFailureSamples)}
          items={collectFailures.recentSamples}
          renderItem={(item, index) => (
            <Box
              key={`${item.jobName}-${item.startedAt}-${item.errorCode}`}
              {...(index === 0 ? buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTarget, ADMIN_DASHBOARD_FOCUS_KEYS.default) : {})}
              {...(index === 0 ? buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTone, "warning") : {})}
              sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}
            >
              <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{formatCollectJobName(item.jobName)}</Typography>
              <Typography sx={{ fontSize: 12, color: INK3, mt: 0.35 }}>
                {formatCodeOrStatus(item.errorCode || item.status)} · {formatDateTime(item.startedAt)}
              </Typography>
              <Typography sx={{ fontSize: 12, color: INK2, mt: 0.75 }}>
                요청 {formatNumber(item.requestedCount)} / 저장 {formatNumber(item.savedCount)} / 실패 {formatNumber(item.failedCount)}
              </Typography>
              <Typography sx={{ fontSize: 12, color: INK3, mt: 0.75, lineHeight: 1.5 }}>
                {formatAdminMessage(item.errorMessage)}
              </Typography>
            </Box>
          )}
        />
      </Box>
    </>
  );
}
