import {
  Box,
  Card,
  CardContent,
  Divider,
  Stack,
  Typography,
} from "@mui/material";
import {
  CohortMix,
  CompactListCard,
  GateChip,
  MetricCard,
} from "./AdminDashboardUi";
import {
  ACCENT,
  INK,
  INK3,
  PANEL_BG,
  PANEL_LINE,
  WARNING_TEXT,
} from "./AdminDashboardUiTokens";
import {
  formatCodeOrStatus,
  formatCollectJobName,
  formatDateTime,
  formatNumber,
  formatPercent,
  formatSourceType,
  formatStatusLabel,
} from "../../lib/adminDashboardDisplay";
import {
  ADMIN_DASHBOARD_CARD_KEYS,
  ADMIN_DASHBOARD_TEST_ATTRS,
  buildDashboardDataAttr,
} from "../../lib/adminDashboardTestHooks";

export function RecommendationStatusHero({ recommendationSummary, concentration }) {
  return (
    <Box
      sx={{
        background: "linear-gradient(135deg, #0f172a 0%, #1d4ed8 55%, #2563eb 100%)",
        color: "white",
        borderRadius: 4,
        p: 3,
        position: "relative",
        overflow: "hidden",
      }}
    >
      <Box sx={{ position: "absolute", inset: 0, background: "radial-gradient(circle at top right, rgba(255,255,255,0.18), transparent 36%)" }} />
      <Stack direction={{ xs: "column", lg: "row" }} justifyContent="space-between" spacing={3} sx={{ position: "relative" }}>
        <Box>
          <Typography sx={{ fontSize: 12, fontWeight: 700, opacity: 0.8 }}>추천 검토 상태</Typography>
          <Typography sx={{ fontSize: 30, fontWeight: 900, mt: 1, letterSpacing: "-0.03em" }}>
            {formatStatusLabel(recommendationSummary.recommendationReviewGate)}
          </Typography>
          <Typography sx={{ fontSize: 14, opacity: 0.85, mt: 1.5, maxWidth: 720, lineHeight: 1.6 }}>
            현재 추천 검토 상태는 <strong>{formatStatusLabel(recommendationSummary.recommendationReviewGate)}</strong> 입니다.
            1순위 선두 신호는 <strong>{formatStatusLabel(concentration.top1LeaderSignalSummary)}</strong>, 실사용자 이용 데이터 상태는 <strong>{formatStatusLabel(recommendationSummary.realUserTrafficGateInWindow)}</strong> 입니다.
          </Typography>
        </Box>
        <GateChip value={recommendationSummary.recommendationReviewGate} />
      </Stack>
    </Box>
  );
}

export function RecommendationSignalMetrics({ recommendationSummary, concentration }) {
  return (
    <Box sx={{ display: "grid", gap: 2, mt: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(3, 1fr)" } }}>
      <MetricCard
        title="1순위 선두 서비스"
        value={concentration.top1LeaderTitle ?? "—"}
        description={`${formatSourceType(concentration.top1LeaderSource)} · ${concentration.top1LeaderCategory}`}
        chip={<Typography sx={{ fontSize: 22, fontWeight: 900, color: ACCENT }}>{formatPercent(concentration.top1LeaderSharePct)}</Typography>}
      />
      <MetricCard
        title="최근 추천 사용자"
        value={formatNumber(concentration.latestBatchUsers)}
        description={`행 ${formatNumber(concentration.latestBatchRows)} · 서비스 ${formatNumber(concentration.latestBatchDistinctServices)}`}
        chip={<GateChip value={concentration.realUserCohortGate} />}
      />
      <MetricCard
        title="1순위 선두 신호"
        value={formatStatusLabel(concentration.top1LeaderSignalSummary)}
        description={formatStatusLabel(concentration.signalQuality)}
        chip={<GateChip value={recommendationSummary.realUserTrafficGateInWindow} />}
        focusTarget
        cardProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminCardKey, ADMIN_DASHBOARD_CARD_KEYS.top1LeaderSignal)}
      />
    </Box>
  );
}

export function OpsSummaryMetrics({ summaryData }) {
  return (
    <Box id="admin-notification-summary" sx={{ display: "grid", gap: 2, mt: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(6, 1fr)" }, scrollMarginTop: 96 }}>
      <MetricCard
        title="수집 실패(24시간)"
        value={formatNumber(summaryData.collect.failedJobsLast24h)}
        description={`최근 ${summaryData.collect.windowDays}일 · 실행 중 ${formatNumber(summaryData.collect.runningJobs)}`}
      />
      <MetricCard
        title="알림 실패(기간)"
        value={formatNumber(summaryData.notification.failedInWindow)}
        description={`발송 ${formatNumber(summaryData.notification.sentInWindow)} / 최근 24시간 실패 ${formatNumber(summaryData.notification.failedLast24h)}`}
      />
      <MetricCard
        title="안 읽은 알림"
        value={formatNumber(summaryData.notification.unreadAlerts)}
        description={`재시도 대기 ${formatNumber(summaryData.notification.retryableFailedNotifications)} / 종결 실패 ${formatNumber(summaryData.notification.terminalFailedNotifications)}`}
      />
      <MetricCard
        title="stale unread 7일"
        value={formatNumber(summaryData.notification.staleUnread7d)}
        description={`미열람 ${formatNumber(summaryData.notification.unreadAlerts)} / 14일 초과 ${formatNumber(summaryData.notification.staleUnread14d)}`}
      />
      <MetricCard
        title="stale unread 14일"
        value={formatNumber(summaryData.notification.staleUnread14d)}
        description={`7일 초과 ${formatNumber(summaryData.notification.staleUnread7d)} / 숨김 후보`}
      />
      <MetricCard
        title="알림 재시도 대기"
        value={formatNumber(summaryData.notification.retryableFailedNotifications)}
        description={`안 읽은 알림 ${formatNumber(summaryData.notification.unreadAlerts)} / 종결 실패 ${formatNumber(summaryData.notification.terminalFailedNotifications)}`}
      />
      <MetricCard
        title="0건 검색(기간)"
        value={formatNumber(summaryData.search.zeroResultSearchesInWindow)}
        description={`검색 ${formatNumber(summaryData.search.searchesInWindow)} · 사용자 ${formatNumber(summaryData.search.uniqueFingerprintsInWindow)}`}
      />
      <MetricCard
        title="PII 동기화 실패"
        value={formatNumber(summaryData.userPiiSync.failedCount)}
        description={`대기 ${formatNumber(summaryData.userPiiSync.pendingCount)} · 최근 동기화 ${formatDateTime(summaryData.userPiiSync.latestSyncedAt)}`}
      />
    </Box>
  );
}

export function PolicyTriageSummary({ policyTriage }) {
  return (
    <Box id="admin-policy-triage-summary" sx={{ display: "grid", gap: 2, mt: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(4, 1fr)" }, scrollMarginTop: 96 }}>
      <MetricCard
        title="policy triage"
        value={formatStatusLabel(policyTriage?.decisionClass)}
        description={policyTriage?.operatorReading || "중복/link backlog 우선순위를 계산하지 못했습니다."}
        chip={
          <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT, maxWidth: 180, textAlign: "right" }}>
            {policyTriage?.nextAction || "nightly observation 확인"}
          </Typography>
        }
      />
      <MetricCard
        title="exact duplicate"
        value={formatNumber(policyTriage?.exactDuplicateGroups)}
        description={`mirror ${formatNumber(policyTriage?.mirrorVariantGroups)} · 열린 중복 ${formatNumber(policyTriage?.openDuplicateGroups)}`}
      />
      <MetricCard
        title="급부형 링크 review"
        value={formatNumber(policyTriage?.benefitSupportLinkReviews)}
        description={`모집형 ${formatNumber(policyTriage?.announcementRecruitmentLinkReviews)} · 열린 링크 ${formatNumber(policyTriage?.openLinkReviews)}`}
      />
      <MetricCard
        title="현재 처리 순서"
        value={policyTriage?.decisionClass === "DUPLICATE_THEN_LINK_PRIORITY" ? "exact -> mirror -> link" : policyTriage?.decisionClass === "LINK_REVIEW_PRIORITY" ? "benefit -> announcement" : "tail review"}
        description={policyTriage?.nextAction || "운영 backlog 우선순위 정보 없음"}
      />
    </Box>
  );
}

export function TopLeaderUserMixCard({ recommendationSummary, concentration }) {
  return (
    <Card sx={{ mt: 2, background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 8px 24px rgba(15,23,42,0.04)" }}>
      <CardContent sx={{ p: 2.5 }}>
        <Typography sx={{ fontSize: 15, fontWeight: 800, color: INK }}>1순위 선두 사용자 구성</Typography>
        <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
          현재 1순위 선두 서비스 ID는 {concentration.top1LeaderServiceId ?? "—"}이며, 아래 사용자 구성을 기준으로 추천 검토 가능 여부를 판단합니다.
        </Typography>
        <Box mt={2}>
          <CohortMix mix={concentration.top1LeaderUserMix} />
        </Box>
        <Divider sx={{ my: 2.5 }} />
        <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(4, 1fr)" } }}>
          <MetricCard title="실사용자 이용 데이터 상태" value={formatStatusLabel(recommendationSummary.realUserTrafficGateInWindow)} />
          <MetricCard title="추천 검토 상태" value={formatStatusLabel(recommendationSummary.recommendationReviewGate)} />
          <MetricCard title="집중도 준비 상태" value={formatStatusLabel(concentration.concentrationReadiness)} />
          <MetricCard title="선두 신호 요약" value={formatStatusLabel(concentration.top1LeaderSignalSummary)} />
        </Box>
      </CardContent>
    </Card>
  );
}

export function RecentOpsSamples({ summaryData }) {
  return (
    <Box sx={{ display: "grid", gap: 2, mt: 2, gridTemplateColumns: { xs: "1fr", xl: "1fr 1fr" } }}>
      <CompactListCard
        title="최근 수집 실패"
        description="최근 수집 실패 샘플"
        items={summaryData.collect.latestFailuresInWindow}
        renderItem={(item) => (
          <Box key={`${item.jobName}-${item.startedAt}`} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
            <Stack direction="row" justifyContent="space-between" spacing={2}>
              <Box>
                <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{formatCollectJobName(item.jobName)}</Typography>
                <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25 }}>
                  {formatCodeOrStatus(item.errorCode || item.status)} · {formatDateTime(item.startedAt)}
                </Typography>
              </Box>
              <Typography sx={{ fontSize: 12, fontWeight: 700, color: WARNING_TEXT }}>
                실패 {formatNumber(item.failedCount)}
              </Typography>
            </Stack>
          </Box>
        )}
      />
      <CompactListCard
        title="0건 검색 키워드"
        description="최근 0건 검색 키워드"
        items={summaryData.search.zeroResultKeywordsInWindow}
        renderItem={(item) => (
          <Box key={item.keyword} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
            <Stack direction="row" justifyContent="space-between" spacing={2}>
              <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{item.keyword}</Typography>
              <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT }}>
                {formatNumber(item.searchCount)}
              </Typography>
            </Stack>
          </Box>
        )}
      />
    </Box>
  );
}
