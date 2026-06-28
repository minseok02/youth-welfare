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
import { formatNumber } from "../../lib/adminDashboardDisplay";
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
                표준코드 추천 효과
              </Typography>
              <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
                latest recommendation observation artifact 기준으로, 주거 표준코드와 복지 표준코드가 실제 추천 점수 변화에 반영되는지 확인합니다.
              </Typography>
            </Box>

            {standardCodeEffectObservationQuery.isLoading && (
              <SectionLoadingCard
                title="표준코드 효과 관측 로딩 중"
                description="latest recommendation observation summary를 읽는 중입니다."
              />
            )}

            {standardCodeEffectObservationQuery.isError && (
              <SectionErrorCard
                title="표준코드 효과 관측 로드 실패"
                description="latest observation artifact를 읽지 못했습니다."
                message={standardCodeEffectObservationErrorMessage}
                onRetry={() => standardCodeEffectObservationQuery.refetch()}
              />
            )}

            {standardCodeEffectObservation && !standardCodeEffectObservationQuery.isLoading && !standardCodeEffectObservationQuery.isError && (
              <>
                {!standardCodeEffectObservation.available ? (
                  <Alert severity="warning">
                    latest recommendation observation artifact가 아직 없습니다. 먼저 `run-local-recommendation-observation-suite.sh`를 실행해야 합니다.
                  </Alert>
                ) : (
                  <Stack spacing={2}>
                    <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", sm: "repeat(2, 1fr)", xl: "repeat(4, 1fr)" } }}>
                      <MetricCard
                        title="주거 효과 rule 상승"
                        value={formatNumber(standardCodeEffectObservation.housingPositiveRuleDeltaRows)}
                        description={`status=${standardCodeEffectObservation.housingEffectStatus}`}
                      />
                      <MetricCard
                        title="주거 최대 rule delta"
                        value={String(standardCodeEffectObservation.housingMaxRuleDelta)}
                        description={`final delta ${standardCodeEffectObservation.housingMaxFinalDelta}`}
                      />
                      <MetricCard
                        title="복지 matrix 시나리오"
                        value={formatNumber(standardCodeEffectObservation.welfareScenarioCount)}
                        description={`positive rule ${formatNumber(standardCodeEffectObservation.welfarePositiveRuleScenarios)}`}
                        focusTarget
                        cardProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminCardKey, ADMIN_DASHBOARD_CARD_KEYS.welfareScenarioCount)}
                      />
                      <MetricCard
                        title="최대 rule delta"
                        value={String(standardCodeEffectObservation.welfareMaxRuleDelta)}
                        description={standardCodeEffectObservation.welfareMaxRuleDeltaScenario || "시나리오 없음"}
                      />
                    </Box>

                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      <Chip label={`precheck ${standardCodeEffectObservation.precheckStatus || "-"}`} size="small" variant="outlined" />
                      <Chip label={`decision ${standardCodeEffectObservation.decisionClass || "-"}`} size="small" variant="outlined" />
                      <Chip label={`matrix status ${standardCodeEffectObservation.welfareMatrixStatus || "-"}`} size="small" variant="outlined" />
                      <Chip label={`final delta ${standardCodeEffectObservation.welfareMaxFinalDelta}`} size="small" variant="outlined" />
                    </Stack>

                    <CompactListCard
                      title="관측 스냅샷"
                      description={standardCodeEffectObservation.sourceSummaryPath}
                      items={[
                        {
                          label: "주거 top delta",
                          value: standardCodeEffectObservation.housingTopPositiveRuleDeltaRows || "empty",
                        },
                        {
                          label: "복지 scenario snapshot",
                          value: standardCodeEffectObservation.welfareScenarioRuleDeltaSnapshot || "empty",
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
