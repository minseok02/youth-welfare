import {
  Alert,
  Box,
  Button,
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
  INFO_BG,
  INFO_BORDER,
  INFO_TEXT,
  INK,
  INK2,
  INK3,
  NOTIFICATION_STALE_DAY_OPTIONS,
  PANEL_BG,
  PANEL_LINE,
  WARNING_BG,
  WARNING_BORDER,
  WARNING_TEXT,
} from "./AdminDashboardUiTokens";
import {
  formatAdminRoutePath,
  formatDateTime,
  formatNumber,
  formatStatusLabel,
} from "../../lib/adminDashboardDisplay";

export default function AdminNotificationStaleTargetsSection({
  notificationStaleTargetsQuery,
  notificationStaleTargetsErrorMessage,
  notificationStaleTargets,
  notificationStaleDays,
  onNotificationStaleDaysChange,
  reviewSubmittingKey,
  onHideNotificationStaleTarget,
}) {
  return (
    <Box id="admin-notification-stale-targets" sx={{ mt: 2, scrollMarginTop: 96 }}>
      <Card sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 10px 28px rgba(15,23,42,0.04)" }}>
        <CardContent sx={{ p: 2.5 }}>
          <Stack spacing={2}>
            <Box>
              <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT, textTransform: "uppercase", letterSpacing: "0.08em" }}>
                알림 backlog triage
              </Typography>
              <Typography sx={{ fontSize: 20, fontWeight: 900, color: INK, mt: 0.75, letterSpacing: "-0.02em" }}>
                stale notification target queue
              </Typography>
              <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
                {formatNumber(notificationStaleTargets?.olderThanDays ?? notificationStaleDays)}일 이상 unread 인 알림을 title/deeplink target 단위로 묶어 보여줍니다. broad unread 총량보다 실제 stale cluster를 먼저 정리할 때 쓰는 운영 경계입니다.
              </Typography>
            </Box>

            <Stack direction="row" spacing={1} flexWrap="wrap">
              {NOTIFICATION_STALE_DAY_OPTIONS.map((days) => {
                const active = notificationStaleDays === days;
                return (
                  <Chip
                    key={days}
                    label={`${days}일 초과`}
                    clickable
                    onClick={() => onNotificationStaleDaysChange(days)}
                    variant={active ? "filled" : "outlined"}
                    sx={{
                      fontWeight: 700,
                      bgcolor: active ? INFO_BG : "#fff",
                      color: active ? INFO_TEXT : INK2,
                      border: `1px solid ${active ? INFO_BORDER : PANEL_LINE}`,
                    }}
                  />
                );
              })}
            </Stack>

            {notificationStaleTargetsQuery.isLoading && (
              <SectionLoadingCard
                title="stale notification target 로딩 중"
                description={`${formatNumber(notificationStaleDays)}일 이상 unread target cluster를 읽는 중입니다.`}
              />
            )}

            {notificationStaleTargetsQuery.isError && (
              <SectionErrorCard
                title="stale notification target 로드 실패"
                description="오래된 unread 알림 target cluster를 읽지 못했습니다."
                message={notificationStaleTargetsErrorMessage}
                onRetry={() => notificationStaleTargetsQuery.refetch()}
              />
            )}

            {notificationStaleTargets && !notificationStaleTargetsQuery.isLoading && !notificationStaleTargetsQuery.isError && (
              <Stack spacing={2}>
                <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(3, 1fr)" } }}>
                  <MetricCard
                    title="stale unread"
                    value={formatNumber(notificationStaleTargets.staleRowCount)}
                    description={`${formatNumber(notificationStaleTargets.olderThanDays)}일 이상 unread row`}
                  />
                  <MetricCard
                    title="stale target 묶음"
                    value={formatNumber(notificationStaleTargets.staleGroupCount)}
                    description="title / deeplink 기준 cluster 수"
                  />
                  <MetricCard
                    title="표시 target"
                    value={formatNumber(notificationStaleTargets.recentTargets?.length ?? 0)}
                    description="최근 stale target 샘플"
                  />
                </Box>

                {(notificationStaleTargets.recentTargets?.length ?? 0) === 0 ? (
                  <Alert severity="success">
                    현재 {formatNumber(notificationStaleTargets.olderThanDays)}일 이상 stale notification target cluster가 없습니다.
                  </Alert>
                ) : (
                  <CompactListCard
                    title="최근 stale notification target"
                    description="rowCount가 큰 오래된 unread target부터 표시합니다."
                    items={notificationStaleTargets.recentTargets ?? []}
                    renderItem={(item) => {
                      const requestKey = `notification-stale-${item.kind}-${item.deeplinkUrl}`;
                      return (
                        <Box
                          key={requestKey}
                          sx={{ p: 1.75, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}
                        >
                          <Stack spacing={1}>
                            <Stack direction={{ xs: "column", md: "row" }} justifyContent="space-between" spacing={1.5}>
                              <Box sx={{ minWidth: 0 }}>
                                <Typography sx={{ fontSize: 14, fontWeight: 800, color: INK, overflowWrap: "anywhere", wordBreak: "break-word" }}>
                                  {item.title}
                                </Typography>
                                <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25, overflowWrap: "anywhere", wordBreak: "break-word" }}>
                                  {formatStatusLabel(item.kind)} · {formatAdminRoutePath(item.deeplinkUrl, "deeplink 없음")} · 가장 오래된 row {formatDateTime(item.oldestCreatedAt)}
                                </Typography>
                              </Box>
                              <Chip
                                label={`${formatNumber(item.rowCount)}건 / ${formatNumber(item.userCount)}명`}
                                size="small"
                                sx={{
                                  bgcolor: WARNING_BG,
                                  color: WARNING_TEXT,
                                  border: `1px solid ${WARNING_BORDER}`,
                                  fontWeight: 700,
                                  alignSelf: { xs: "flex-start", md: "center" },
                                }}
                              />
                            </Stack>
                            <Typography sx={{ fontSize: 13, color: INK2 }}>
                              최근 row {formatDateTime(item.newestCreatedAt)}
                            </Typography>
                            <Stack direction={{ xs: "column", md: "row" }} spacing={1} alignItems={{ xs: "stretch", md: "center" }}>
                              <Button
                                size="small"
                                variant="outlined"
                                disabled={reviewSubmittingKey === requestKey}
                                onClick={() => onHideNotificationStaleTarget(item)}
                                sx={{ textTransform: "none", borderRadius: 999, whiteSpace: "nowrap", alignSelf: { xs: "stretch", md: "flex-start" } }}
                              >
                                {reviewSubmittingKey === requestKey ? "처리 중..." : `${formatNumber(notificationStaleTargets.olderThanDays)}일 초과 숨기기`}
                              </Button>
                            </Stack>
                          </Stack>
                        </Box>
                      );
                    }}
                  />
                )}
              </Stack>
            )}
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
}
