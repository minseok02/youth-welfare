import {
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
  INFO_BG,
  INFO_BORDER,
  INFO_TEXT,
  INK,
  INK3,
  PANEL_BG,
  PANEL_LINE,
  WARNING_TEXT,
} from "./AdminDashboardUiTokens";
import {
  formatDateTime,
  formatNumber,
} from "../../lib/adminDashboardDisplay";

export default function AdminNotificationAttemptSummarySection({
  notificationAttemptSummaryQuery,
  notificationAttemptSummaryErrorMessage,
  notificationAttemptSummary,
}) {
  return (
    <Box id="admin-notification-attempt-summary" sx={{ mt: 2, scrollMarginTop: 96 }}>
      {notificationAttemptSummaryQuery.isLoading && (
        <SectionLoadingCard
          title="알림 attempt 로딩 중"
          description="채널별 발송 attempt outcome과 최근 실패 샘플을 불러오는 중입니다."
        />
      )}

      {notificationAttemptSummaryQuery.isError && (
        <SectionErrorCard
          title="알림 attempt 로드 실패"
          description="신규 notification_attempt_logs migration 적용 상태와 admin read 권한을 확인해야 합니다."
          message={notificationAttemptSummaryErrorMessage}
          onRetry={() => notificationAttemptSummaryQuery.refetch()}
        />
      )}

      {notificationAttemptSummary && (
        <Card sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 10px 28px rgba(15,23,42,0.04)" }}>
          <CardContent sx={{ p: 2.5 }}>
            <Stack spacing={2}>
              <Box>
                <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT, textTransform: "uppercase", letterSpacing: "0.08em" }}>
                  알림 attempt
                </Typography>
                <Typography sx={{ fontSize: 20, fontWeight: 900, color: INK, mt: 0.75, letterSpacing: "-0.02em" }}>
                  채널별 발송 상태
                </Typography>
              </Box>
              <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(4, 1fr)" } }}>
                <MetricCard
                  title={`${notificationAttemptSummary.windowDays}일 attempt`}
                  value={formatNumber(notificationAttemptSummary.totalAttempts)}
                  description={`최근 ${formatDateTime(notificationAttemptSummary.latestAttemptAt)}`}
                />
                <MetricCard
                  title="성공 / 실패"
                  value={`${formatNumber(notificationAttemptSummary.successAttempts)} / ${formatNumber(notificationAttemptSummary.failedAttempts)}`}
                  description="sent, fanout, gateway outcome 기준"
                  descriptionColor={Number(notificationAttemptSummary.failedAttempts) > 0 ? WARNING_TEXT : INK3}
                />
                <MetricCard
                  title="disabled"
                  value={formatNumber(notificationAttemptSummary.disabledAttempts)}
                  description="web push 구독 무효화/해제 신호"
                />
                <MetricCard
                  title="평균 지연"
                  value={`${formatNumber(notificationAttemptSummary.averageDurationMs)}ms`}
                  description="channel attempt 기준"
                />
              </Box>

              <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", xl: "1fr 1fr" } }}>
                <CompactListCard
                  title="attempt outcome 분포"
                  description="channel / kind / outcome 기준 상위 집계"
                  items={notificationAttemptSummary.breakdowns}
                  renderItem={(item) => (
                    <Box key={`${item.channel}-${item.kind}-${item.outcome}`} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
                      <Stack direction="row" justifyContent="space-between" spacing={2}>
                        <Box sx={{ minWidth: 0 }}>
                          <Typography sx={{ fontSize: 13, fontWeight: 800, color: INK }}>
                            {item.channel} · {item.kind}
                          </Typography>
                          <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25 }}>
                            {item.outcome} · 평균 {formatNumber(item.averageDurationMs)}ms
                          </Typography>
                        </Box>
                        <Chip
                          label={formatNumber(item.attemptCount)}
                          size="small"
                          sx={{ bgcolor: INFO_BG, color: INFO_TEXT, border: `1px solid ${INFO_BORDER}`, fontWeight: 700 }}
                        />
                      </Stack>
                    </Box>
                  )}
                />
                <CompactListCard
                  title="최근 실패/disabled"
                  description="실패 outcome과 disabled endpoint 샘플"
                  items={notificationAttemptSummary.recentFailures}
                  renderItem={(item) => (
                    <Box key={item.id} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
                      <Stack spacing={0.75}>
                        <Stack direction="row" justifyContent="space-between" spacing={2}>
                          <Typography sx={{ fontSize: 13, fontWeight: 800, color: INK }}>
                            {item.channel} · {item.kind}
                          </Typography>
                          <Typography sx={{ fontSize: 12, fontWeight: 800, color: WARNING_TEXT }}>
                            {item.outcome}
                          </Typography>
                        </Stack>
                        <Typography sx={{ fontSize: 12, color: INK3 }}>
                          {formatDateTime(item.createdAt)} · {item.endpointHost || "endpoint 없음"} · {item.errorType || "errorType 없음"}
                        </Typography>
                      </Stack>
                    </Box>
                  )}
                />
              </Box>
            </Stack>
          </CardContent>
        </Card>
      )}
    </Box>
  );
}
