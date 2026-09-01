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
  formatBooleanLabel,
  formatAdminOperationalText,
  formatDateTime,
  formatNumber,
  formatStatusLabel,
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
                기준 요약 / 현재 우선순위
              </Typography>
              <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
                운영 요약 파일 기준으로, 선택 프로필 입력 현황과 추천 관측 핵심값이 기준 요약에 제대로 반영되는지 낮은 빈도로 확인합니다.
              </Typography>
            </Box>

            {wrapperObservationQuery.isLoading && (
              <SectionLoadingCard
                title="상위 요약 관측 로딩 중"
                description="기준 요약과 현재 우선순위 요약을 읽는 중입니다."
              />
            )}

            {wrapperObservationQuery.isError && (
              <SectionErrorCard
                title="상위 요약 관측 로드 실패"
                description="기준 요약 또는 현재 우선순위 요약을 읽지 못했습니다."
                message={wrapperObservationErrorMessage}
                onRetry={() => wrapperObservationQuery.refetch()}
              />
            )}

            {wrapperObservation && !wrapperObservationQuery.isLoading && !wrapperObservationQuery.isError && (
              <Stack spacing={2}>
                <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", xl: "1fr 1fr" } }}>
                  <SectionStateCard
                    title="기준 요약"
                    description={wrapperObservation.activeBaselineSummaryPath ? "내부 결과 파일 저장됨" : "결과 파일 정보 없음"}
                  >
                    {!wrapperObservation.activeBaselineAvailable ? (
                      <Alert severity="warning">최신 기준 요약이 아직 없습니다.</Alert>
                    ) : (
                      <Stack spacing={2}>
                        <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", sm: "repeat(2, 1fr)" } }}>
                          <MetricCard title="실행 결과" value={formatStatusLabel(wrapperObservation.activeBaselineStatus)} description={formatDateTime(wrapperObservation.activeBaselineGeneratedAt)} />
                          <MetricCard title="운영 관측 상태" value={formatStatusLabel(wrapperObservation.activeBaselineOpsObservationStatus)} description="상위 요약에 반영된 관측 상태" />
                          <MetricCard title="주의 항목 상태" value={formatStatusLabel(wrapperObservation.activeBaselineAttentionFeedStatus)} description={`${formatNumber(wrapperObservation.activeBaselineAttentionFeedItemCount)}개 항목`} />
                          <MetricCard title="1개 이상 입력" value={formatNumber(wrapperObservation.activeBaselineUsersWithAnyStandardCode)} description="상위 요약 반영값" />
                          <MetricCard title="전부 미입력" value={formatNumber(wrapperObservation.activeBaselineUsersMissingAllStandardCodes)} description="상위 요약 반영값" />
                        </Box>
                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                          <Chip label={`주의 항목 ${formatAdminOperationalText(wrapperObservation.activeBaselineAttentionFeedItemTitles, "-")}`} size="small" variant="outlined" />
                          <Chip label={`주거 규칙 상승 ${formatNumber(wrapperObservation.activeBaselineRecommendationHousingPositiveRuleDeltaRows)}`} size="small" variant="outlined" />
                          <Chip label={`복지 상승 시나리오 ${formatNumber(wrapperObservation.activeBaselineRecommendationWelfarePositiveRuleScenarios)}`} size="small" variant="outlined" />
                          <Chip label={`복지 최대 변화 ${wrapperObservation.activeBaselineRecommendationWelfareMaxRuleDelta}`} size="small" variant="outlined" />
                        </Stack>
                      </Stack>
                    )}
                  </SectionStateCard>

                  <SectionStateCard
                    title="현재 우선순위"
                    description={wrapperObservation.currentPrioritySummaryPath ? "내부 결과 파일 저장됨" : "결과 파일 정보 없음"}
                  >
                    {!wrapperObservation.currentPriorityAvailable ? (
                      <Alert severity="warning">최신 현재 우선순위 요약이 아직 없습니다.</Alert>
                    ) : (
                      <Stack spacing={2}>
                        <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", sm: "repeat(2, 1fr)" } }}>
                          <MetricCard title="실행 결과" value={formatStatusLabel(wrapperObservation.currentPriorityStatus)} description={formatDateTime(wrapperObservation.currentPriorityGeneratedAt)} />
                          <MetricCard title="기준 요약 재사용" value={formatBooleanLabel(wrapperObservation.currentPriorityActiveBaselineReused, "재사용", "새로 계산")} description="같은 설정의 최신 기준 요약 사용 여부" />
                          <MetricCard title="주의 항목 상태" value={formatStatusLabel(wrapperObservation.currentPriorityAttentionFeedStatus)} description={`${formatNumber(wrapperObservation.currentPriorityAttentionFeedItemCount)}개 항목`} />
                          <MetricCard title="1개 이상 입력" value={formatNumber(wrapperObservation.currentPriorityUsersWithAnyStandardCode)} description="현재 우선순위 반영값" />
                          <MetricCard
                            title="전부 미입력"
                            value={formatNumber(wrapperObservation.currentPriorityUsersMissingAllStandardCodes)}
                            description="현재 우선순위 반영값"
                            focusTarget
                            cardProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminCardKey, ADMIN_DASHBOARD_CARD_KEYS.currentPriorityMissingStandardCodes)}
                          />
                        </Box>
                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                          <Chip label={`주의 항목 ${formatAdminOperationalText(wrapperObservation.currentPriorityAttentionFeedItemTitles, "-")}`} size="small" variant="outlined" />
                          <Chip label={`추천 관측 ${formatStatusLabel(wrapperObservation.currentPriorityRecommendationObservationStatus)}`} size="small" variant="outlined" />
                          <Chip label={`주거 규칙 상승 ${formatNumber(wrapperObservation.currentPriorityRecommendationHousingPositiveRuleDeltaRows)}`} size="small" variant="outlined" />
                          <Chip label={`복지 시나리오 ${formatNumber(wrapperObservation.currentPriorityRecommendationWelfareScenarioCount)}`} size="small" variant="outlined" />
                          <Chip label={`복지 상승 ${formatNumber(wrapperObservation.currentPriorityRecommendationWelfarePositiveRuleScenarios)}`} size="small" variant="outlined" />
                          <Chip label={`복지 최대 변화 ${wrapperObservation.currentPriorityRecommendationWelfareMaxRuleDelta}`} size="small" variant="outlined" />
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
