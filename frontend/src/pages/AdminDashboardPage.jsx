import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Alert,
  Box,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  MenuItem,
  Select,
  Stack,
  Typography,
} from "@mui/material";
import Header from "../components/Header";
import FloatingNav from "../components/FloatingNav";
import api from "../lib/axios";

const PAGE_BG = "#f7f8fc";
const PANEL_BG = "#ffffff";
const PANEL_LINE = "#e5e7eb";
const INK = "#11131a";
const INK2 = "#4a4f5c";
const INK3 = "#6b7280";
const ACCENT = "#2563eb";
const WARNING_BG = "#fff7ed";
const WARNING_BORDER = "#fdba74";
const WARNING_TEXT = "#9a3412";
const SUCCESS_BG = "#ecfdf5";
const SUCCESS_BORDER = "#86efac";
const SUCCESS_TEXT = "#166534";

const REVIEW_GATE_TONE = {
  DEFERRED_EMPTY_COHORT: { label: "비어 있음", bg: WARNING_BG, border: WARNING_BORDER, color: WARNING_TEXT },
  DEFERRED_NO_REAL_USER_TRAFFIC: { label: "실사용자 없음", bg: WARNING_BG, border: WARNING_BORDER, color: WARNING_TEXT },
  DEFERRED_REAL_USER_SAMPLE_THIN: { label: "실사용자 표본 부족", bg: WARNING_BG, border: WARNING_BORDER, color: WARNING_TEXT },
  DEFERRED_REAL_USER_CLICK_SAMPLE_THIN: { label: "실사용자 클릭 표본 부족", bg: WARNING_BG, border: WARNING_BORDER, color: WARNING_TEXT },
  DEFERRED_NON_REAL_LEADER_SIGNAL: { label: "비실사용 leader", bg: WARNING_BG, border: WARNING_BORDER, color: WARNING_TEXT },
  DEFERRED_REAL_USER_LEADER_SIGNAL_THIN: { label: "leader 실사용 신호 부족", bg: WARNING_BG, border: WARNING_BORDER, color: WARNING_TEXT },
  READY_CONCENTRATED_TOP1_REVIEW: { label: "top1 집중 리뷰 가능", bg: SUCCESS_BG, border: SUCCESS_BORDER, color: SUCCESS_TEXT },
  READY_NO_PRIORITY_DOMINANT_REVIEW: { label: "무우선순위 리뷰 가능", bg: SUCCESS_BG, border: SUCCESS_BORDER, color: SUCCESS_TEXT },
  READY_BALANCED_LOGIC_REVIEW: { label: "로직 리뷰 가능", bg: SUCCESS_BG, border: SUCCESS_BORDER, color: SUCCESS_TEXT },
};

const labelFor = (map, key) => map[key]?.label ?? key;

const formatPercent = (value) => {
  const num = Number(value);
  return Number.isFinite(num) ? `${num.toFixed(2)}%` : "—";
};

const formatNumber = (value) => {
  const num = Number(value);
  return Number.isFinite(num) ? num.toLocaleString("ko-KR") : "—";
};

const formatDateTime = (value) => {
  if (!value) return "—";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return "—";
  return new Intl.DateTimeFormat("ko-KR", {
    month: "numeric",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(parsed);
};

const fetchSummary = async (windowDays) => {
  const { data } = await api.get("/api/admin/dashboard/summary", {
    params: { summaryWindowDays: windowDays, trendWindowDays: [1, 7, 30] },
  });
  return data?.data;
};

const fetchBreakdowns = async (windowDays) => {
  const { data } = await api.get("/api/admin/dashboard/recommendation-breakdowns", {
    params: { summaryWindowDays: windowDays, limit: 3 },
  });
  return data?.data;
};

const fetchCollectFailures = async (windowDays) => {
  const { data } = await api.get("/api/admin/dashboard/collect-failures", {
    params: { summaryWindowDays: windowDays, limit: 5 },
  });
  return data?.data;
};

const fetchSearchFailures = async (windowDays) => {
  const { data } = await api.get("/api/admin/dashboard/search-failures", {
    params: { summaryWindowDays: windowDays, limit: 5 },
  });
  return data?.data;
};

function GateChip({ value }) {
  const tone = REVIEW_GATE_TONE[value] ?? { bg: "#eef2ff", border: "#c7d2fe", color: "#3730a3", label: value };
  return (
    <Chip
      label={tone.label}
      sx={{
        bgcolor: tone.bg,
        color: tone.color,
        border: `1px solid ${tone.border}`,
        fontWeight: 700,
      }}
    />
  );
}

function MetricCard({ title, value, description, chip }) {
  return (
    <Card sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 8px 24px rgba(15,23,42,0.04)" }}>
      <CardContent sx={{ p: 2.5 }}>
        <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={2}>
          <Box>
            <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK2 }}>{title}</Typography>
            <Typography sx={{ fontSize: 28, fontWeight: 800, color: INK, mt: 1 }}>{value}</Typography>
            {description && <Typography sx={{ fontSize: 12, color: INK3, mt: 0.75 }}>{description}</Typography>}
          </Box>
          {chip}
        </Stack>
      </CardContent>
    </Card>
  );
}

function CohortMix({ mix }) {
  const items = [
    { label: "Example", value: mix?.exampleUsers },
    { label: "Bounded Local", value: mix?.boundedLocalUsers },
    { label: "Local Seed", value: mix?.localRealNonExampleSeedUsers },
    { label: "Real User", value: mix?.realUserUsers },
  ];

  return (
    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
      {items.map((item) => (
        <Chip
          key={item.label}
          label={`${item.label} ${formatNumber(item.value)}`}
          size="small"
          sx={{ bgcolor: "#f8fafc", color: INK2, border: `1px solid ${PANEL_LINE}` }}
        />
      ))}
    </Stack>
  );
}

function ServiceListCard({ title, items, countLabel }) {
  return (
    <Card sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 8px 24px rgba(15,23,42,0.04)", height: "100%" }}>
      <CardContent sx={{ p: 2.5 }}>
        <Typography sx={{ fontSize: 15, fontWeight: 800, color: INK }}>{title}</Typography>
        <Stack spacing={1.5} mt={2}>
          {items?.length ? items.map((item) => (
            <Box key={`${title}-${item.serviceId}`} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
              <Stack direction="row" justifyContent="space-between" spacing={2}>
                <Box>
                  <Typography sx={{ fontSize: 14, fontWeight: 700, color: INK }}>{item.title}</Typography>
                  <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25 }}>
                    {item.sourceType} · {item.category}
                  </Typography>
                </Box>
                <Typography sx={{ fontSize: 14, fontWeight: 800, color: ACCENT, whiteSpace: "nowrap" }}>
                  {formatNumber(item[countLabel])}
                </Typography>
              </Stack>
              <Box mt={1.25}>
                <CohortMix mix={item.userMix} />
              </Box>
            </Box>
          )) : (
            <Typography sx={{ fontSize: 13, color: INK3 }}>표시할 데이터가 없습니다.</Typography>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}

function CompactListCard({ title, description, items, renderItem }) {
  return (
    <Card sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 8px 24px rgba(15,23,42,0.04)", height: "100%" }}>
      <CardContent sx={{ p: 2.5 }}>
        <Typography sx={{ fontSize: 15, fontWeight: 800, color: INK }}>{title}</Typography>
        {description && <Typography sx={{ fontSize: 12, color: INK3, mt: 0.5 }}>{description}</Typography>}
        <Stack spacing={1.25} mt={2}>
          {items?.length ? items.map(renderItem) : (
            <Typography sx={{ fontSize: 13, color: INK3 }}>표시할 데이터가 없습니다.</Typography>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}

function TriageSectionTitle({ eyebrow, title, description }) {
  return (
    <Box>
      <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT, textTransform: "uppercase", letterSpacing: "0.08em" }}>
        {eyebrow}
      </Typography>
      <Typography sx={{ fontSize: 22, fontWeight: 900, color: INK, mt: 0.75, letterSpacing: "-0.02em" }}>
        {title}
      </Typography>
      <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
        {description}
      </Typography>
    </Box>
  );
}

export default function AdminDashboardPage() {
  const [windowDays, setWindowDays] = useState(14);

  const summaryQuery = useQuery({
    queryKey: ["admin-dashboard-summary", windowDays],
    queryFn: () => fetchSummary(windowDays),
    staleTime: 30_000,
  });

  const breakdownQuery = useQuery({
    queryKey: ["admin-dashboard-breakdowns", windowDays],
    queryFn: () => fetchBreakdowns(windowDays),
    staleTime: 30_000,
  });

  const collectFailuresQuery = useQuery({
    queryKey: ["admin-dashboard-collect-failures", windowDays],
    queryFn: () => fetchCollectFailures(windowDays),
    staleTime: 30_000,
  });

  const searchFailuresQuery = useQuery({
    queryKey: ["admin-dashboard-search-failures", windowDays],
    queryFn: () => fetchSearchFailures(windowDays),
    staleTime: 30_000,
  });

  const summaryData = summaryQuery.data;
  const recommendationSummary = summaryData?.recommendation;
  const concentration = recommendationSummary?.latestBatchConcentration;
  const breakdowns = breakdownQuery.data;
  const collectFailures = collectFailuresQuery.data;
  const searchFailures = searchFailuresQuery.data;
  const isLoading =
    summaryQuery.isLoading
    || breakdownQuery.isLoading
    || collectFailuresQuery.isLoading
    || searchFailuresQuery.isLoading;
  const isError =
    summaryQuery.isError
    || breakdownQuery.isError
    || collectFailuresQuery.isError
    || searchFailuresQuery.isError;
  const errorMessage =
    summaryQuery.error?.response?.data?.message
    ?? breakdownQuery.error?.response?.data?.message
    ?? collectFailuresQuery.error?.response?.data?.message
    ?? searchFailuresQuery.error?.response?.data?.message
    ?? "운영 대시보드를 불러오지 못했습니다.";

  return (
    <div style={{ minHeight: "100vh", background: PAGE_BG }}>
      <Header />
      <FloatingNav />
      <Box sx={{ maxWidth: 1280, mx: "auto", px: { xs: 2, lg: 4 }, py: 4 }}>
        <Stack direction={{ xs: "column", lg: "row" }} justifyContent="space-between" alignItems={{ xs: "flex-start", lg: "center" }} spacing={2}>
          <Box>
            <Typography sx={{ fontSize: 32, fontWeight: 900, color: INK, letterSpacing: "-0.03em" }}>
              운영 추천 대시보드
            </Typography>
            <Typography sx={{ fontSize: 14, color: INK3, mt: 1 }}>
              recommendation review gate, top1 leader signal, cohort mix를 한 화면에서 확인합니다.
            </Typography>
          </Box>
          <Stack direction="row" spacing={1.5} alignItems="center">
            <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK2 }}>요약 기간</Typography>
            <Select
              size="small"
              value={windowDays}
              onChange={(event) => setWindowDays(Number(event.target.value))}
              sx={{ minWidth: 120, bgcolor: PANEL_BG }}
            >
              <MenuItem value={7}>최근 7일</MenuItem>
              <MenuItem value={14}>최근 14일</MenuItem>
              <MenuItem value={30}>최근 30일</MenuItem>
            </Select>
          </Stack>
        </Stack>

        {isLoading && (
          <Box sx={{ display: "flex", alignItems: "center", justifyContent: "center", py: 16 }}>
            <CircularProgress />
          </Box>
        )}

        {isError && (
          <Alert severity="error" sx={{ mt: 3 }}>
            {errorMessage}
          </Alert>
        )}

        {!isLoading && !isError && summaryData && recommendationSummary && concentration && breakdowns && collectFailures && searchFailures && (
          <Stack spacing={3} mt={3}>
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
                  <Typography sx={{ fontSize: 12, fontWeight: 700, opacity: 0.8 }}>Recommendation Review Gate</Typography>
                  <Typography sx={{ fontSize: 30, fontWeight: 900, mt: 1, letterSpacing: "-0.03em" }}>
                    {labelFor(REVIEW_GATE_TONE, recommendationSummary.recommendationReviewGate)}
                  </Typography>
                  <Typography sx={{ fontSize: 14, opacity: 0.85, mt: 1.5, maxWidth: 720, lineHeight: 1.6 }}>
                    현재 review gate는 <strong>{recommendationSummary.recommendationReviewGate}</strong> 입니다.
                    top1 leader signal은 <strong>{concentration.top1LeaderSignalSummary}</strong>, real user traffic gate는 <strong>{recommendationSummary.realUserTrafficGateInWindow}</strong> 입니다.
                  </Typography>
                </Box>
                <GateChip value={recommendationSummary.recommendationReviewGate} />
              </Stack>
            </Box>

            <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(3, 1fr)" } }}>
              <MetricCard
                title="Top1 Leader"
                value={concentration.top1LeaderTitle ?? "—"}
                description={`${concentration.top1LeaderSource} · ${concentration.top1LeaderCategory}`}
                chip={<Typography sx={{ fontSize: 22, fontWeight: 900, color: ACCENT }}>{formatPercent(concentration.top1LeaderSharePct)}</Typography>}
              />
              <MetricCard
                title="Latest Batch Users"
                value={formatNumber(concentration.latestBatchUsers)}
                description={`rows ${formatNumber(concentration.latestBatchRows)} · services ${formatNumber(concentration.latestBatchDistinctServices)}`}
                chip={<GateChip value={concentration.realUserCohortGate} />}
              />
              <MetricCard
                title="Leader Signal"
                value={labelFor(REVIEW_GATE_TONE, concentration.top1LeaderSignalSummary)}
                description={concentration.signalQuality}
                chip={<GateChip value={recommendationSummary.realUserTrafficGateInWindow} />}
              />
            </Box>

            <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(4, 1fr)" } }}>
              <MetricCard
                title="Collect 실패(24h)"
                value={formatNumber(summaryData.collect.failedJobsLast24h)}
                description={`window ${summaryData.collect.windowDays}일 · running ${formatNumber(summaryData.collect.runningJobs)}`}
              />
              <MetricCard
                title="알림 실패(window)"
                value={formatNumber(summaryData.notification.failedInWindow)}
                description={`sent ${formatNumber(summaryData.notification.sentInWindow)} / last24h failed ${formatNumber(summaryData.notification.failedLast24h)}`}
              />
              <MetricCard
                title="0건 검색(window)"
                value={formatNumber(summaryData.search.zeroResultSearchesInWindow)}
                description={`searches ${formatNumber(summaryData.search.searchesInWindow)} · users ${formatNumber(summaryData.search.uniqueFingerprintsInWindow)}`}
              />
              <MetricCard
                title="PII Sync 실패"
                value={formatNumber(summaryData.userPiiSync.failedCount)}
                description={`pending ${formatNumber(summaryData.userPiiSync.pendingCount)} · latest ${formatDateTime(summaryData.userPiiSync.latestSyncedAt)}`}
              />
            </Box>

            <Card sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 8px 24px rgba(15,23,42,0.04)" }}>
              <CardContent sx={{ p: 2.5 }}>
                <Typography sx={{ fontSize: 15, fontWeight: 800, color: INK }}>Top1 Leader Cohort Mix</Typography>
                <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
                  현재 top1 leader는 `{concentration.top1LeaderServiceId}` 이며, real user 기준으로는 아직 review reopen 근거가 아닙니다.
                </Typography>
                <Box mt={2}>
                  <CohortMix mix={concentration.top1LeaderUserMix} />
                </Box>
                <Divider sx={{ my: 2.5 }} />
                <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(4, 1fr)" } }}>
                  <MetricCard title="Real User Traffic Gate" value={recommendationSummary.realUserTrafficGateInWindow} />
                  <MetricCard title="Recommendation Review Gate" value={recommendationSummary.recommendationReviewGate} />
                  <MetricCard title="Concentration Readiness" value={concentration.concentrationReadiness} />
                  <MetricCard title="Leader Signal Summary" value={concentration.top1LeaderSignalSummary} />
                </Box>
              </CardContent>
            </Card>

            <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", xl: "1fr 1fr" } }}>
              <ServiceListCard title="Top Repeated Services" items={breakdowns.topRepeatedServices} countLabel="rowCount" />
              <ServiceListCard title="Top1 Distribution Leaders" items={breakdowns.top1Services} countLabel="usersAsTop1" />
            </Box>

            <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", xl: "1fr 1fr" } }}>
              <CompactListCard
                title="최근 수집 실패"
                description="collect latestFailuresInWindow"
                items={summaryData.collect.latestFailuresInWindow}
                renderItem={(item) => (
                  <Box key={`${item.jobName}-${item.startedAt}`} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
                    <Stack direction="row" justifyContent="space-between" spacing={2}>
                      <Box>
                        <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{item.jobName}</Typography>
                        <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25 }}>
                          {item.errorCode || item.status} · {formatDateTime(item.startedAt)}
                        </Typography>
                      </Box>
                      <Typography sx={{ fontSize: 12, fontWeight: 700, color: WARNING_TEXT }}>
                        fail {formatNumber(item.failedCount)}
                      </Typography>
                    </Stack>
                  </Box>
                )}
              />
              <CompactListCard
                title="0건 검색 키워드"
                description="search zeroResultKeywordsInWindow"
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

            <TriageSectionTitle
              eyebrow="Collect Triage"
              title="수집 실패 상세"
              description="실패 job, circuit open 상태, 최근 실패 샘플을 summary 카드 아래에서 바로 확인합니다."
            />

            <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(4, 1fr)" } }}>
              <MetricCard
                title="Failed Jobs"
                value={formatNumber(collectFailures.failedJobsInWindow)}
                description={`window ${collectFailures.windowDays}일`}
              />
              <MetricCard
                title="Partial Success"
                value={formatNumber(collectFailures.partialSuccessJobsInWindow)}
                description="일부 저장 후 종료된 job"
              />
              <MetricCard
                title="Open Circuits"
                value={formatNumber(collectFailures.circuitStatuses?.filter((item) => item.open).length)}
                description={`tracked ${formatNumber(collectFailures.circuitStatuses?.length)}`}
              />
              <MetricCard
                title="Recent Failure Samples"
                value={formatNumber(collectFailures.recentSamples?.length)}
                description="상세 샘플 미리보기"
              />
            </Box>

            <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", xl: "1.1fr 0.9fr" } }}>
              <CompactListCard
                title="Job Breakdown"
                description="실패/부분성공이 많은 수집 job"
                items={collectFailures.jobBreakdowns}
                renderItem={(item) => (
                  <Box key={`${item.jobName}-${item.latestStartedAt}`} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
                    <Stack direction="row" justifyContent="space-between" spacing={2}>
                      <Box>
                        <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{item.jobName}</Typography>
                        <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25 }}>
                          latest {formatDateTime(item.latestStartedAt)}
                        </Typography>
                      </Box>
                      <Stack spacing={0.5} alignItems="flex-end">
                        <Typography sx={{ fontSize: 12, fontWeight: 800, color: WARNING_TEXT }}>
                          fail {formatNumber(item.failedCount)}
                        </Typography>
                        <Typography sx={{ fontSize: 12, fontWeight: 700, color: INK2 }}>
                          partial {formatNumber(item.partialSuccessCount)}
                        </Typography>
                      </Stack>
                    </Stack>
                  </Box>
                )}
              />
              <CompactListCard
                title="Circuit Status"
                description="open circuit은 외부 수집 안정성 저하를 뜻합니다."
                items={collectFailures.circuitStatuses}
                renderItem={(item) => (
                  <Box key={item.circuitKey} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: item.open ? "#fff7ed" : "#f8fafc" }}>
                    <Stack direction="row" justifyContent="space-between" spacing={2}>
                      <Box>
                        <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{item.circuitKey}</Typography>
                        <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25 }}>
                          open until {formatDateTime(item.openUntil)}
                        </Typography>
                      </Box>
                      <Typography sx={{ fontSize: 12, fontWeight: 800, color: item.open ? WARNING_TEXT : SUCCESS_TEXT }}>
                        {item.open ? `OPEN ${formatNumber(item.remainingMs)}ms` : "CLOSED"}
                      </Typography>
                    </Stack>
                  </Box>
                )}
              />
            </Box>

            <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", xl: "1fr 1fr 1fr" } }}>
              <CompactListCard
                title="Error Codes"
                description="실패 원인 상위 집계"
                items={collectFailures.errorCodeBreakdowns}
                renderItem={(item) => (
                  <Box key={item.errorCode || "UNKNOWN"} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
                    <Stack direction="row" justifyContent="space-between" spacing={2}>
                      <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{item.errorCode || "UNKNOWN"}</Typography>
                      <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT }}>{formatNumber(item.failedCount)}</Typography>
                    </Stack>
                  </Box>
                )}
              />
              <CompactListCard
                title="Current Streaks"
                description="같은 상태가 연속되는 job"
                items={collectFailures.currentJobStreaks}
                renderItem={(item) => (
                  <Box key={`${item.jobName}-${item.streakStatus}`} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
                    <Stack direction="row" justifyContent="space-between" spacing={2}>
                      <Box>
                        <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{item.jobName}</Typography>
                        <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25 }}>
                          {item.streakStatus} · latest {formatDateTime(item.latestStartedAt)}
                        </Typography>
                      </Box>
                      <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT }}>{formatNumber(item.streakCount)}</Typography>
                    </Stack>
                  </Box>
                )}
              />
              <CompactListCard
                title="Recent Failure Samples"
                description="에러 메시지와 저장 실패 규모"
                items={collectFailures.recentSamples}
                renderItem={(item) => (
                  <Box key={`${item.jobName}-${item.startedAt}-${item.errorCode}`} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
                    <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{item.jobName}</Typography>
                    <Typography sx={{ fontSize: 12, color: INK3, mt: 0.35 }}>
                      {item.errorCode || item.status} · {formatDateTime(item.startedAt)}
                    </Typography>
                    <Typography sx={{ fontSize: 12, color: INK2, mt: 0.75 }}>
                      req {formatNumber(item.requestedCount)} / save {formatNumber(item.savedCount)} / fail {formatNumber(item.failedCount)}
                    </Typography>
                    <Typography sx={{ fontSize: 12, color: INK3, mt: 0.75, lineHeight: 1.5 }}>
                      {item.errorMessage || "에러 메시지 없음"}
                    </Typography>
                  </Box>
                )}
              />
            </Box>

            <TriageSectionTitle
              eyebrow="Search Triage"
              title="검색 실패 상세"
              description="0건 검색 패턴, 재시도 묶음, recovery 여부를 같은 페이지에서 바로 확인합니다."
            />

            <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(4, 1fr)" } }}>
              <MetricCard
                title="Zero Result Searches"
                value={formatNumber(searchFailures.zeroResultSearchesInWindow)}
                description={`window ${searchFailures.windowDays}일`}
              />
              <MetricCard
                title="Retry Groups"
                value={formatNumber(searchFailures.retryGroups?.length)}
                description="동일 조건 반복 검색"
              />
              <MetricCard
                title="Recovered Groups"
                value={formatNumber(searchFailures.recoveredSearchGroups?.length)}
                description="0건 후 결과 복구"
              />
              <MetricCard
                title="Recent Search Samples"
                value={formatNumber(searchFailures.recentSamples?.length)}
                description="실패 샘플 미리보기"
              />
            </Box>

            <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", xl: "1fr 1fr" } }}>
              <CompactListCard
                title="Zero Result Regions"
                description="지역 단위 0건 검색 상위"
                items={searchFailures.zeroResultRegions}
                renderItem={(item) => (
                  <Box key={`${item.sido}-${item.sgg}`} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
                    <Stack direction="row" justifyContent="space-between" spacing={2}>
                      <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{item.sido || "전국"} {item.sgg || ""}</Typography>
                      <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT }}>{formatNumber(item.searchCount)}</Typography>
                    </Stack>
                  </Box>
                )}
              />
              <CompactListCard
                title="Zero Result Filter Patterns"
                description="필터 조합별 0건 검색"
                items={searchFailures.zeroResultFilterPatterns}
                renderItem={(item) => (
                  <Box key={`${item.statusFilter}-${item.category}-${item.sourceType}-${item.onlineApply}-${item.includeClosed}-${item.sortKey}`} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
                    <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>
                      {item.category || "전체"} · {item.sourceType || "전체"}
                    </Typography>
                    <Typography sx={{ fontSize: 12, color: INK3, mt: 0.35 }}>
                      {item.statusFilter || "all"} / online {String(item.onlineApply)} / closed {String(item.includeClosed)} / {item.sortKey || "default"}
                    </Typography>
                    <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT, mt: 0.75 }}>
                      {formatNumber(item.searchCount)}
                    </Typography>
                  </Box>
                )}
              />
            </Box>

            <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", xl: "1fr 1fr 1fr" } }}>
              <CompactListCard
                title="Retry Groups"
                description="같은 actor가 반복한 실패 검색"
                items={searchFailures.retryGroups}
                renderItem={(item) => (
                  <Box key={`${item.actorType}-${item.actorKey}-${item.keyword}-${item.latestSearchedAt}`} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
                    <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{item.keyword || "키워드 없음"}</Typography>
                    <Typography sx={{ fontSize: 12, color: INK3, mt: 0.35 }}>
                      {item.actorType} · {item.actorKey || "anonymous"}
                    </Typography>
                    <Typography sx={{ fontSize: 12, color: INK2, mt: 0.75 }}>
                      retry {formatNumber(item.retryCount)} · {formatDateTime(item.firstSearchedAt)} ~ {formatDateTime(item.latestSearchedAt)}
                    </Typography>
                  </Box>
                )}
              />
              <CompactListCard
                title="Recovered Groups"
                description="나중에 결과가 생긴 검색 묶음"
                items={searchFailures.recoveredSearchGroups}
                renderItem={(item) => (
                  <Box key={`${item.actorType}-${item.actorKey}-${item.keyword}-${item.latestRecoveredAt}`} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
                    <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{item.keyword || "키워드 없음"}</Typography>
                    <Typography sx={{ fontSize: 12, color: INK3, mt: 0.35 }}>
                      zero {formatNumber(item.zeroResultCount)} / recovered {formatNumber(item.recoveredResultCount)}
                    </Typography>
                    <Typography sx={{ fontSize: 12, color: INK2, mt: 0.75 }}>
                      latest recovered {formatDateTime(item.latestRecoveredAt)}
                    </Typography>
                  </Box>
                )}
              />
              <CompactListCard
                title="Recent Zero Result Samples"
                description="실패 검색 샘플"
                items={searchFailures.recentSamples}
                renderItem={(item) => (
                  <Box key={`${item.keyword}-${item.searchedAt}-${item.sido}-${item.sgg}`} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
                    <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{item.keyword || "키워드 없음"}</Typography>
                    <Typography sx={{ fontSize: 12, color: INK3, mt: 0.35 }}>
                      {item.sido || "전국"} {item.sgg || ""} · {formatDateTime(item.searchedAt)}
                    </Typography>
                    <Typography sx={{ fontSize: 12, color: INK2, mt: 0.75 }}>
                      {item.category || "전체"} / {item.sourceType || "전체"} / {item.statusFilter || "all"}
                    </Typography>
                  </Box>
                )}
              />
            </Box>
          </Stack>
        )}
      </Box>
    </div>
  );
}
