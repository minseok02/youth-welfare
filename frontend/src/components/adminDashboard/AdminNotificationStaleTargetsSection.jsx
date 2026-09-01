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
                오래된 알림 정리
              </Typography>
              <Typography sx={{ fontSize: 20, fontWeight: 900, color: INK, mt: 0.75, letterSpacing: "-0.02em" }}>
                오래된 미열람 알림 대기열
              </Typography>
              <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
                {formatNumber(notificationStaleTargets?.olderThanDays ?? notificationStaleDays)}일 이상 읽지 않은 알림을 제목과 이동 경로 기준으로 묶어 보여줍니다.
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
                title="오래된 미열람 알림 로딩 중"
                description={`${formatNumber(notificationStaleDays)}일 이상 읽지 않은 알림 묶음을 읽는 중입니다.`}
              />
            )}

            {notificationStaleTargetsQuery.isError && (
              <SectionErrorCard
                title="오래된 미열람 알림 로드 실패"
                description="오래된 미열람 알림 묶음을 읽지 못했습니다."
                message={notificationStaleTargetsErrorMessage}
                onRetry={() => notificationStaleTargetsQuery.refetch()}
              />
            )}

            {notificationStaleTargets && !notificationStaleTargetsQuery.isLoading && !notificationStaleTargetsQuery.isError && (
              <Stack spacing={2}>
                <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(3, 1fr)" } }}>
                  <MetricCard
                    title="오래된 미열람"
                    value={formatNumber(notificationStaleTargets.staleRowCount)}
                    description={`${formatNumber(notificationStaleTargets.olderThanDays)}일 이상 읽지 않은 알림`}
                  />
                  <MetricCard
                    title="오래된 알림 묶음"
                    value={formatNumber(notificationStaleTargets.staleGroupCount)}
                    description="제목 / 이동 경로 기준 묶음 수"
                  />
                  <MetricCard
                    title="표시 묶음"
                    value={formatNumber(notificationStaleTargets.recentTargets?.length ?? 0)}
                    description="오래된 미열람 알림 샘플"
                  />
                </Box>

                {(notificationStaleTargets.recentTargets?.length ?? 0) === 0 ? (
                  <Alert severity="success">
                    현재 {formatNumber(notificationStaleTargets.olderThanDays)}일 이상 된 미열람 알림 묶음이 없습니다.
                  </Alert>
                ) : (
                  <CompactListCard
                    title="최근 오래된 미열람 알림"
                    description="알림 건수가 큰 오래된 미열람 묶음부터 표시합니다."
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
                                  {formatStatusLabel(item.kind)} · {formatAdminRoutePath(item.deeplinkUrl, "이동 경로 없음")} · 가장 오래된 알림 {formatDateTime(item.oldestCreatedAt)}
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
                              최근 알림 {formatDateTime(item.newestCreatedAt)}
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
