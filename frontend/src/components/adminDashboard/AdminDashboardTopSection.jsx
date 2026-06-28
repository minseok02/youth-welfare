import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  MenuItem,
  Select,
  Stack,
  Typography,
} from "@mui/material";
import {
  AttentionNextAction,
  MetricCard,
  QuickJumpButton,
  ToneChip,
} from "./AdminDashboardUi";
import {
  ACCENT,
  ATTENTION_SEVERITY_TONE,
  INK,
  INK2,
  INK3,
  PANEL_BG,
  PANEL_LINE,
  WARNING_BORDER,
  WARNING_TEXT,
  WRAPPER_STATUS_TONE,
} from "./AdminDashboardUiTokens";
import {
  formatDateTime,
  formatNumber,
  formatRelativeDateTime,
} from "../../lib/adminDashboardDisplay";
import {
  ADMIN_DASHBOARD_ACTION_KEYS,
  ADMIN_DASHBOARD_FOCUS_KEYS,
  ADMIN_DASHBOARD_TEST_ATTRS,
  buildDashboardDataAttr,
} from "../../lib/adminDashboardTestHooks";

export default function AdminDashboardTopSection({
  windowDays,
  onWindowDaysChange,
  isRefreshing,
  onRefreshAll,
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
  onJumpToSection,
}) {
  return (
    <>
      <Stack direction={{ xs: "column", lg: "row" }} justifyContent="space-between" alignItems={{ xs: "flex-start", lg: "center" }} spacing={2}>
        <Box>
          <Typography sx={{ fontSize: 32, fontWeight: 900, color: INK, letterSpacing: "-0.03em" }}>
            운영 추천 대시보드
          </Typography>
          <Typography sx={{ fontSize: 14, color: INK3, mt: 1 }}>
            추천 검토 상태, 1순위 선두 신호, 사용자 구성을 한 화면에서 확인합니다.
          </Typography>
        </Box>
        <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5} alignItems={{ xs: "stretch", sm: "center" }}>
          <Stack spacing={0.5} sx={{ minWidth: { xs: "100%", sm: 180 } }}>
            <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK2 }}>요약 기간</Typography>
            <Select
              size="small"
              value={windowDays}
              onChange={(event) => onWindowDaysChange(Number(event.target.value))}
              sx={{ minWidth: 120, bgcolor: PANEL_BG }}
            >
              <MenuItem value={7}>최근 7일</MenuItem>
              <MenuItem value={14}>최근 14일</MenuItem>
              <MenuItem value={30}>최근 30일</MenuItem>
            </Select>
          </Stack>
          <Button
            variant="contained"
            onClick={onRefreshAll}
            disabled={isRefreshing}
            sx={{
              alignSelf: { xs: "stretch", sm: "flex-end" },
              borderRadius: 999,
              px: 2.25,
              py: 1.1,
              textTransform: "none",
              fontWeight: 800,
              bgcolor: ACCENT,
              boxShadow: "none",
              "&:hover": { bgcolor: "#1d4ed8", boxShadow: "none" },
            }}
          >
            {isRefreshing ? "갱신 중..." : "전체 새로고침"}
          </Button>
        </Stack>
      </Stack>

      {promotedAttentionItems.length > 0 && (
        <Card
          id="admin-ops-alerts"
          sx={{
            mt: 3,
            background: "#fffaf0",
            border: `1px solid ${WARNING_BORDER}`,
            boxShadow: "0 10px 28px rgba(15,23,42,0.04)",
          }}
        >
          <CardContent sx={{ p: 2.5 }}>
            <Stack spacing={2}>
              <Stack direction={{ xs: "column", md: "row" }} justifyContent="space-between" spacing={1.5}>
                <Box>
                  <Typography sx={{ fontSize: 12, fontWeight: 800, color: WARNING_TEXT, textTransform: "uppercase", letterSpacing: "0.08em" }}>
                    운영 알림
                  </Typography>
                  <Typography sx={{ fontSize: 20, fontWeight: 900, color: INK, mt: 0.75, letterSpacing: "-0.02em" }}>
                    지금 바로 볼 우선 신호 {formatNumber(attentionQueueItems.length)}건
                  </Typography>
                  <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
                    상위 경고와 표준코드 backlog, collect drift를 먼저 읽고 아래 상세 섹션으로 내려가면 됩니다.
                  </Typography>
                </Box>
                <Button
                  size="small"
                  variant="outlined"
                  sx={{ alignSelf: { xs: "flex-start", md: "center" }, textTransform: "none", borderRadius: 999 }}
                  onClick={() => onJumpToSection("admin-attention-queue", { tone: "warning", preset: "attention" })}
                >
                  주의 항목 큐 보기
                </Button>
              </Stack>

              <Box sx={{ display: "grid", gap: 1.25, gridTemplateColumns: { xs: "1fr", xl: "repeat(3, 1fr)" } }}>
                {promotedAttentionItems.map((item) => (
                  <Box
                    key={item.key}
                    {...buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.attentionKey, item.key)}
                    sx={{
                      p: 1.75,
                      borderRadius: 2,
                      border: `1px solid ${ATTENTION_SEVERITY_TONE[item.severity]?.border ?? PANEL_LINE}`,
                      bgcolor: "#ffffff",
                    }}
                  >
                    <Stack spacing={1}>
                      <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                        <ToneChip toneMap={ATTENTION_SEVERITY_TONE} value={item.severity} />
                        <Typography sx={{ fontSize: 14, fontWeight: 800, color: INK }}>
                          {item.title}
                        </Typography>
                      </Stack>
                      <Typography sx={{ fontSize: 13, color: INK2 }}>
                        {item.message}
                      </Typography>
                      <AttentionNextAction nextAction={item.nextAction} />
                      {item.targetId ? (
                        <Button
                          size="small"
                          variant="text"
                          {...buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.attentionActionKey, item.key)}
                          sx={{ alignSelf: "flex-start", px: 0, textTransform: "none", fontWeight: 700 }}
                          onClick={() => onJumpToSection(item.targetId, { tone: item.severity, preset: "attention" })}
                        >
                          해당 섹션 보기
                        </Button>
                      ) : null}
                    </Stack>
                  </Box>
                ))}
              </Box>
            </Stack>
          </CardContent>
        </Card>
      )}

      <Card sx={{ mt: 3, background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 10px 28px rgba(15,23,42,0.04)" }}>
        <CardContent sx={{ p: 2.5 }}>
          <Stack spacing={2.5}>
            <Stack direction={{ xs: "column", lg: "row" }} justifyContent="space-between" spacing={2}>
              <Box>
                <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT, textTransform: "uppercase", letterSpacing: "0.08em" }}>
                  운영 스냅샷
                </Typography>
                <Typography sx={{ fontSize: 20, fontWeight: 900, color: INK, mt: 0.75, letterSpacing: "-0.02em" }}>
                  오늘 볼 운영 신호를 먼저 모았습니다
                </Typography>
                <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
                  실패 섹션, 열린 회로, 표준코드 미입력 규모, current priority 관측 상태를 먼저 확인하고 아래 상세로 내려가면 됩니다.
                </Typography>
              </Box>
              <Stack spacing={0.5} alignItems={{ xs: "flex-start", lg: "flex-end" }}>
                <Typography sx={{ fontSize: 12, fontWeight: 700, color: INK2 }}>
                  {formatRelativeDateTime(latestGeneratedAt)}
                </Typography>
                <Typography sx={{ fontSize: 12, color: INK3 }}>
                  마지막 시각 {formatDateTime(latestGeneratedAt)}
                </Typography>
              </Stack>
            </Stack>

            <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", sm: "repeat(2, 1fr)", xl: "repeat(7, 1fr)" } }}>
              <MetricCard
                title="실패 섹션"
                value={formatNumber(failedSectionCount)}
                description={failedSectionCount > 0 ? "부분 실패 있음" : "전체 섹션 응답 정상"}
              />
              <MetricCard
                title="열린 회로"
                value={formatNumber(openCircuitCount)}
                description="외부 수집 안정성 저하 신호"
              />
              <MetricCard
                title="재시도 묶음"
                value={formatNumber(retryGroupCount)}
                description="같은 조건 반복 검색"
              />
              <MetricCard
                title="복구된 묶음"
                value={formatNumber(recoveredGroupCount)}
                description="0건 후 결과가 생긴 검색"
              />
              <MetricCard
                title="표준코드 미입력"
                value={formatNumber(wrapperMissingAllStandardCodes)}
                description={`current priority 승격 기준 · ${wrapperMissingDeltaLabel}`}
                descriptionColor={wrapperMissingDeltaColor}
                chip={<ToneChip toneMap={{ active: wrapperMissingStandardCodeTone }} value="active" />}
              />
              <MetricCard
                title="attention 대표"
                value={wrapperAttentionFeedPrimaryTitle}
                description={`current priority 승격 기준 · ${formatNumber(wrapperAttentionFeedItemCount)}건 · ${wrapperAttentionFeedPrimarySource} / ${wrapperAttentionFeedPrimarySeverity}`}
                chip={<ToneChip toneMap={{ active: wrapperAttentionFeedTone }} value="active" />}
                actionLabel={wrapperAttentionFeedPrimaryActionLabel}
                cardProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.attentionPrimaryKey, wrapperAttentionFeedPrimaryKey || "unknown")}
                actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.attentionPrimaryActionKey, wrapperAttentionFeedPrimaryKey || "unknown")}
                onAction={() => onJumpToSection(wrapperAttentionFeedPrimaryTargetId, { tone: wrapperAttentionFeedPrimarySeverity, preset: "attention" })}
              />
              <MetricCard
                title="priority 관측"
                value={wrapperRecommendationObservationStatus}
                description={`active baseline ${wrapperActiveBaselineReuseLabel} · ${wrapperObservationChangeLabel} · attention ${wrapperAttentionFeedStatus}`}
                descriptionColor={wrapperObservationChangeColor}
                chip={<ToneChip toneMap={WRAPPER_STATUS_TONE} value={wrapperRecommendationObservationStatus} />}
              />
            </Box>

            {wrapperSnapshotAlert && (
              <Alert severity={wrapperSnapshotAlert.severity}>
                <strong>{wrapperSnapshotAlert.title}</strong>
                {` · ${wrapperSnapshotAlert.message}`}
              </Alert>
            )}

            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <QuickJumpButton
                label="추천 개요"
                targetId="admin-recommendation-overview"
                subtle={false}
                onJump={onJumpToSection}
                tone="info"
                actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminActionKey, ADMIN_DASHBOARD_ACTION_KEYS.quickJumpRecommendationOverview)}
              />
              <QuickJumpButton
                label="주의 항목"
                targetId="admin-attention-queue"
                count={attentionQueueItems.length}
                subtle
                onJump={onJumpToSection}
                tone="warning"
                actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminActionKey, ADMIN_DASHBOARD_ACTION_KEYS.quickJumpAttentionQueue)}
              />
              <QuickJumpButton
                label="표준코드 입력률"
                targetId="admin-standard-code-coverage"
                count={standardCodeCoverage?.usersMissingAllStandardCodes ?? 0}
                subtle
                onJump={onJumpToSection}
                tone={standardCodeCoverage?.usersMissingAllStandardCodes > 0 ? "warning" : "success"}
                actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminActionKey, ADMIN_DASHBOARD_ACTION_KEYS.quickJumpStandardCodeCoverage)}
              />
              <QuickJumpButton
                label="표준코드 효과"
                targetId="admin-standard-code-effect"
                count={standardCodeEffectObservation?.welfareScenarioCount ?? 0}
                subtle
                onJump={onJumpToSection}
                tone="info"
                actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminActionKey, ADMIN_DASHBOARD_ACTION_KEYS.quickJumpStandardCodeEffect)}
              />
              <QuickJumpButton
                label="상위 wrapper"
                targetId="admin-wrapper-observation"
                count={wrapperObservation?.currentPriorityUsersMissingAllStandardCodes ?? 0}
                subtle
                onJump={onJumpToSection}
                tone={wrapperSnapshotAlert?.severity ?? "info"}
                actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminActionKey, ADMIN_DASHBOARD_ACTION_KEYS.quickJumpWrapperObservation)}
              />
              <QuickJumpButton
                label="공식 코드북"
                targetId="admin-reference-codebooks"
                count={officialCodebooks.length}
                subtle
                onJump={onJumpToSection}
                tone="info"
                actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminActionKey, ADMIN_DASHBOARD_ACTION_KEYS.quickJumpReferenceCodebooks)}
              />
              <QuickJumpButton
                label="추천 상세"
                targetId="admin-recommendation-breakdowns"
                count={breakdowns?.topRepeatedServices?.length ?? 0}
                subtle
                onJump={onJumpToSection}
                tone="info"
                actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminActionKey, ADMIN_DASHBOARD_ACTION_KEYS.quickJumpRecommendationBreakdowns)}
              />
              <QuickJumpButton
                label="수집 실패"
                targetId="admin-collect-triage"
                count={collectFailures?.failedJobsInWindow ?? 0}
                subtle
                onJump={onJumpToSection}
                tone={collectFailures?.failedJobsInWindow > 0 ? "warning" : "success"}
                focusKey={ADMIN_DASHBOARD_FOCUS_KEYS.default}
                actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminActionKey, ADMIN_DASHBOARD_ACTION_KEYS.quickJumpCollectTriage)}
              />
              <QuickJumpButton
                label="검색 실패"
                targetId="admin-search-triage"
                count={searchFailures?.zeroResultSearchesInWindow ?? 0}
                subtle
                onJump={onJumpToSection}
                tone={searchFailures?.zeroResultSearchesInWindow > 0 ? "warning" : "success"}
                focusKey={ADMIN_DASHBOARD_FOCUS_KEYS.searchWarning}
                actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminActionKey, ADMIN_DASHBOARD_ACTION_KEYS.quickJumpSearchTriage)}
              />
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      {failedSectionCount > 0 && (
        <Alert severity="warning" sx={{ mt: 3 }}>
          일부 섹션만 불러오지 못했습니다. 성공한 데이터는 그대로 표시하고, 실패한 섹션만 다시 시도할 수 있습니다.
        </Alert>
      )}
    </>
  );
}
