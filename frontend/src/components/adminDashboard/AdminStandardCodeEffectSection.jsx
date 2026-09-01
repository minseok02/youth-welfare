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
  CompactListCard,
  MetricCard,
  SectionErrorCard,
  SectionLoadingCard,
} from "./AdminDashboardUi";
import {
  ACCENT,
  INK,
  INK2,
  INK3,
  PANEL_BG,
  PANEL_LINE,
} from "./AdminDashboardUiTokens";
import {
  formatAdminOperationalText,
  formatNumber,
  formatStatusLabel,
} from "../../lib/adminDashboardDisplay";
import {
  ADMIN_DASHBOARD_CARD_KEYS,
  ADMIN_DASHBOARD_TEST_ATTRS,
  buildDashboardDataAttr,
} from "../../lib/adminDashboardTestHooks";

export default function AdminStandardCodeEffectSection({
  standardCodeEffectObservationQuery,
  standardCodeEffectObservationErrorMessage,
  standardCodeEffectObservation,
}) {
  return (
    <Box id="admin-standard-code-effect" sx={{ scrollMarginTop: 96 }}>
      <Card sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 10px 28px rgba(15,23,42,0.04)" }}>
        <CardContent sx={{ p: 2.5 }}>
          <Stack spacing={2}>
            <Box>
              <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT, textTransform: "uppercase", letterSpacing: "0.08em" }}>
                추천 관측
              </Typography>
              <Typography sx={{ fontSize: 20, fontWeight: 900, color: INK, mt: 0.75, letterSpacing: "-0.02em" }}>
                선택 프로필 추천 영향
              </Typography>
              <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
                최신 추천 관측 요약 기준으로, 주거·복지 선택 프로필이 실제 추천 점수 변화에 반영되는지 낮은 빈도로 확인합니다.
              </Typography>
            </Box>

            {standardCodeEffectObservationQuery.isLoading && (
              <SectionLoadingCard
                title="선택 프로필 추천 영향 로딩 중"
                description="최신 추천 관측 요약을 읽는 중입니다."
              />
            )}

            {standardCodeEffectObservationQuery.isError && (
              <SectionErrorCard
                title="선택 프로필 추천 영향 로드 실패"
                description="최신 추천 관측 요약을 읽지 못했습니다."
                message={standardCodeEffectObservationErrorMessage}
                onRetry={() => standardCodeEffectObservationQuery.refetch()}
              />
            )}

            {standardCodeEffectObservation && !standardCodeEffectObservationQuery.isLoading && !standardCodeEffectObservationQuery.isError && (
              <>
                {!standardCodeEffectObservation.available ? (
                  <Alert severity="warning">
                    최신 추천 관측 요약이 아직 없습니다. 먼저 추천 관측 배치를 실행해야 합니다.
                  </Alert>
                ) : (
                  <Stack spacing={2}>
                    <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", sm: "repeat(2, 1fr)", xl: "repeat(4, 1fr)" } }}>
                      <MetricCard
                        title="주거 규칙 상승"
                        value={formatNumber(standardCodeEffectObservation.housingPositiveRuleDeltaRows)}
                        description={formatStatusLabel(standardCodeEffectObservation.housingEffectStatus)}
                      />
                      <MetricCard
                        title="주거 최대 규칙 변화"
                        value={String(standardCodeEffectObservation.housingMaxRuleDelta)}
                        description={`최종 변화 ${standardCodeEffectObservation.housingMaxFinalDelta}`}
                      />
                      <MetricCard
                        title="복지 조합 시나리오"
                        value={formatNumber(standardCodeEffectObservation.welfareScenarioCount)}
                        description={`상승 규칙 ${formatNumber(standardCodeEffectObservation.welfarePositiveRuleScenarios)}`}
                        focusTarget
                        cardProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminCardKey, ADMIN_DASHBOARD_CARD_KEYS.welfareScenarioCount)}
                      />
                      <MetricCard
                        title="최대 규칙 변화"
                        value={String(standardCodeEffectObservation.welfareMaxRuleDelta)}
                        description={standardCodeEffectObservation.welfareMaxRuleDeltaScenario || "시나리오 없음"}
                      />
                    </Box>

                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      <Chip label={`사전 점검 ${formatStatusLabel(standardCodeEffectObservation.precheckStatus)}`} size="small" variant="outlined" />
                      <Chip label={`판단 ${formatStatusLabel(standardCodeEffectObservation.decisionClass)}`} size="small" variant="outlined" />
                      <Chip label={`조합표 상태 ${formatStatusLabel(standardCodeEffectObservation.welfareMatrixStatus)}`} size="small" variant="outlined" />
                      <Chip label={`최종 변화 ${standardCodeEffectObservation.welfareMaxFinalDelta}`} size="small" variant="outlined" />
                    </Stack>

                    <CompactListCard
                      title="관측 스냅샷"
                      description={standardCodeEffectObservation.sourceSummaryPath ? "내부 결과 파일 저장됨" : "결과 파일 정보 없음"}
                      items={[
                        {
                          label: "주거 상위 변화",
                          value: formatAdminOperationalText(standardCodeEffectObservation.housingTopPositiveRuleDeltaRows, "내용 없음"),
                        },
                        {
                          label: "복지 시나리오 스냅샷",
                          value: formatAdminOperationalText(standardCodeEffectObservation.welfareScenarioRuleDeltaSnapshot, "내용 없음"),
                        },
                      ]}
                      renderItem={(item) => (
                        <Box key={item.label} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
                          <Typography sx={{ fontSize: 12, fontWeight: 800, color: INK2 }}>{item.label}</Typography>
                          <Typography sx={{ fontSize: 13, color: INK, mt: 0.75, overflowWrap: "anywhere", wordBreak: "break-word" }}>
                            {item.value}
                          </Typography>
                        </Box>
                      )}
                    />
                  </Stack>
                )}
              </>
            )}
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
}
