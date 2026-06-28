import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Stack,
  Typography,
} from "@mui/material";
import {
  AttentionNextAction,
  CompactListCard,
  SectionErrorCard,
  SectionLoadingCard,
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
} from "./AdminDashboardUiTokens";
import {
  ADMIN_DASHBOARD_TEST_ATTRS,
  buildDashboardDataAttr,
} from "../../lib/adminDashboardTestHooks";

export default function AdminAttentionQueueSection({
  attentionFeedQuery,
  localAttentionQueueItems,
  attentionQueueItems,
  attentionFeedErrorMessage,
  onJumpToSection,
}) {
  return (
    <Box id="admin-attention-queue" sx={{ scrollMarginTop: 96 }}>
      <Card sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 10px 28px rgba(15,23,42,0.04)" }}>
        <CardContent sx={{ p: 2.5 }}>
          <Stack spacing={2}>
            <Box>
              <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT, textTransform: "uppercase", letterSpacing: "0.08em" }}>
                운영 큐
              </Typography>
              <Typography sx={{ fontSize: 20, fontWeight: 900, color: INK, mt: 0.75, letterSpacing: "-0.02em" }}>
                지금 먼저 볼 주의 항목
              </Typography>
              <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
                collect drift, 표준코드 backlog, wrapper 경고처럼 바로 조치가 필요한 신호만 우선순위 큐로 묶었습니다.
              </Typography>
            </Box>

            {attentionFeedQuery.isLoading && localAttentionQueueItems.length === 0 ? (
              <SectionLoadingCard
                title="운영 알림 로딩 중"
                description="재사용 가능한 attention feed를 읽는 중입니다."
              />
            ) : null}

            {attentionFeedQuery.isError ? (
              <SectionErrorCard
                title="운영 알림 로드 실패"
                description="backend attention feed를 읽지 못했습니다."
                message={attentionFeedErrorMessage}
                onRetry={() => attentionFeedQuery.refetch()}
              />
            ) : null}

            {attentionQueueItems.length === 0 && !attentionFeedQuery.isLoading ? (
              <Alert severity="success">현재 우선 조치가 필요한 운영 큐가 없습니다.</Alert>
            ) : (
              <CompactListCard
                title="주의 항목 큐"
                description="상단 운영 스냅샷과 하위 섹션을 잇는 우선순위 큐"
                items={attentionQueueItems}
                renderItem={(item, index) => (
                  <Box
                    key={item.key}
                    {...buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.attentionKey, item.key)}
                    {...(index === 0 ? buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTarget, "true") : {})}
                    sx={{
                      p: 1.75,
                      borderRadius: 2,
                      border: `1px solid ${ATTENTION_SEVERITY_TONE[item.severity]?.border ?? PANEL_LINE}`,
                      bgcolor: ATTENTION_SEVERITY_TONE[item.severity]?.bg ?? "#fafbff",
                    }}
                  >
                    <Stack direction={{ xs: "column", md: "row" }} spacing={1.5} justifyContent="space-between" alignItems={{ xs: "flex-start", md: "center" }}>
                      <Stack spacing={0.75}>
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
                      </Stack>
                      {item.targetId ? (
                        <Button
                          size="small"
                          variant="outlined"
                          {...buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.attentionActionKey, item.key)}
                          sx={{ textTransform: "none", borderRadius: 999 }}
                          onClick={() => onJumpToSection(item.targetId, { tone: item.severity, preset: "attention" })}
                        >
                          해당 섹션 보기
                        </Button>
                      ) : null}
                    </Stack>
                  </Box>
                )}
              />
            )}
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
}
