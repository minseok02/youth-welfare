import {
  Alert,
  Box,
  Card,
  CardContent,
  Chip,
  Stack,
  Typography,
} from "@mui/material";
import {
  MetricCard,
  SectionErrorCard,
  SectionLoadingCard,
  SectionStateCard,
} from "./AdminDashboardUi";
import {
  ACCENT,
  INK,
  INK3,
  PANEL_BG,
  PANEL_LINE,
} from "./AdminDashboardUiTokens";
import {
  formatDateTime,
  formatNumber,
} from "../../lib/adminDashboardDisplay";
import {
  ADMIN_DASHBOARD_CARD_KEYS,
  ADMIN_DASHBOARD_TEST_ATTRS,
  buildDashboardDataAttr,
} from "../../lib/adminDashboardTestHooks";

export default function AdminWrapperObservationSection({
  wrapperObservationQuery,
  wrapperObservationErrorMessage,
  wrapperObservation,
}) {
  return (
    <Box id="admin-wrapper-observation" sx={{ scrollMarginTop: 96 }}>
      <Card sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 10px 28px rgba(15,23,42,0.04)" }}>
        <CardContent sx={{ p: 2.5 }}>
          <Stack spacing={2}>
            <Box>
              <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT, textTransform: "uppercase", letterSpacing: "0.08em" }}>
                상위 요약
              </Typography>
              <Typography sx={{ fontSize: 20, fontWeight: 900, color: INK, mt: 0.75, letterSpacing: "-0.02em" }}>
                active baseline / current priority
              </Typography>
              <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
                운영 상위 wrapper latest summary 기준으로, 표준코드 coverage와 recommendation observation 핵심값이 최상단 baseline/current priority handoff에 올라왔는지 확인합니다.
              </Typography>
            </Box>

            {wrapperObservationQuery.isLoading && (
              <SectionLoadingCard
                title="상위 wrapper 관측 로딩 중"
                description="active baseline / current priority latest summary를 읽는 중입니다."
              />
            )}

            {wrapperObservationQuery.isError && (
              <SectionErrorCard
                title="상위 wrapper 관측 로드 실패"
                description="latest active baseline/current priority summary를 읽지 못했습니다."
                message={wrapperObservationErrorMessage}
                onRetry={() => wrapperObservationQuery.refetch()}
              />
            )}

            {wrapperObservation && !wrapperObservationQuery.isLoading && !wrapperObservationQuery.isError && (
              <Stack spacing={2}>
                <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", xl: "1fr 1fr" } }}>
                  <SectionStateCard
                    title="active baseline"
                    description={wrapperObservation.activeBaselineSummaryPath}
                  >
                    {!wrapperObservation.activeBaselineAvailable ? (
                      <Alert severity="warning">latest active baseline summary가 아직 없습니다.</Alert>
                    ) : (
                      <Stack spacing={2}>
                        <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", sm: "repeat(2, 1fr)" } }}>
                          <MetricCard title="suite" value={wrapperObservation.activeBaselineStatus || "-"} description={formatDateTime(wrapperObservation.activeBaselineGeneratedAt)} />
                          <MetricCard title="ops observation" value={wrapperObservation.activeBaselineOpsObservationStatus || "-"} description="상위 wrapper 내 ops observation 상태" />
                          <MetricCard title="attention feed" value={wrapperObservation.activeBaselineAttentionFeedStatus || "-"} description={`${formatNumber(wrapperObservation.activeBaselineAttentionFeedItemCount)}개 항목`} />
                          <MetricCard title="1개 이상 입력" value={formatNumber(wrapperObservation.activeBaselineUsersWithAnyStandardCode)} description="ops summary 승격값" />
                          <MetricCard title="전부 미입력" value={formatNumber(wrapperObservation.activeBaselineUsersMissingAllStandardCodes)} description="ops summary 승격값" />
                        </Box>
                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                          <Chip label={`attention ${wrapperObservation.activeBaselineAttentionFeedItemTitles || "-"}`} size="small" variant="outlined" />
                          <Chip label={`주거 rule 상승 ${formatNumber(wrapperObservation.activeBaselineRecommendationHousingPositiveRuleDeltaRows)}`} size="small" variant="outlined" />
                          <Chip label={`복지 positive scenarios ${formatNumber(wrapperObservation.activeBaselineRecommendationWelfarePositiveRuleScenarios)}`} size="small" variant="outlined" />
                          <Chip label={`복지 max delta ${wrapperObservation.activeBaselineRecommendationWelfareMaxRuleDelta}`} size="small" variant="outlined" />
                        </Stack>
                      </Stack>
                    )}
                  </SectionStateCard>

                  <SectionStateCard
                    title="current priority"
                    description={wrapperObservation.currentPrioritySummaryPath}
                  >
                    {!wrapperObservation.currentPriorityAvailable ? (
                      <Alert severity="warning">latest current priority summary가 아직 없습니다.</Alert>
                    ) : (
                      <Stack spacing={2}>
                        <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", sm: "repeat(2, 1fr)" } }}>
                          <MetricCard title="suite" value={wrapperObservation.currentPriorityStatus || "-"} description={formatDateTime(wrapperObservation.currentPriorityGeneratedAt)} />
                          <MetricCard title="active baseline reused" value={wrapperObservation.currentPriorityActiveBaselineReused ? "true" : "false"} description="same-config latest 재사용 여부" />
                          <MetricCard title="attention feed" value={wrapperObservation.currentPriorityAttentionFeedStatus || "-"} description={`${formatNumber(wrapperObservation.currentPriorityAttentionFeedItemCount)}개 항목`} />
                          <MetricCard title="1개 이상 입력" value={formatNumber(wrapperObservation.currentPriorityUsersWithAnyStandardCode)} description="current priority 승격값" />
                          <MetricCard
                            title="전부 미입력"
                            value={formatNumber(wrapperObservation.currentPriorityUsersMissingAllStandardCodes)}
                            description="current priority 승격값"
                            focusTarget
                            cardProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminCardKey, ADMIN_DASHBOARD_CARD_KEYS.currentPriorityMissingStandardCodes)}
                          />
                        </Box>
                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                          <Chip label={`attention ${wrapperObservation.currentPriorityAttentionFeedItemTitles || "-"}`} size="small" variant="outlined" />
                          <Chip label={`recommendation status ${wrapperObservation.currentPriorityRecommendationObservationStatus || "-"}`} size="small" variant="outlined" />
                          <Chip label={`주거 rule 상승 ${formatNumber(wrapperObservation.currentPriorityRecommendationHousingPositiveRuleDeltaRows)}`} size="small" variant="outlined" />
                          <Chip label={`복지 scenarios ${formatNumber(wrapperObservation.currentPriorityRecommendationWelfareScenarioCount)}`} size="small" variant="outlined" />
                          <Chip label={`복지 positive ${formatNumber(wrapperObservation.currentPriorityRecommendationWelfarePositiveRuleScenarios)}`} size="small" variant="outlined" />
                          <Chip label={`복지 max delta ${wrapperObservation.currentPriorityRecommendationWelfareMaxRuleDelta}`} size="small" variant="outlined" />
                        </Stack>
                      </Stack>
                    )}
                  </SectionStateCard>
                </Box>
              </Stack>
            )}
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
}
