import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Alert,
  Box,
  Button,
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
const INFO_BG = "#eff6ff";
const INFO_BORDER = "#93c5fd";
const INFO_TEXT = "#1d4ed8";

const SOURCE_TYPE_LABELS = {
  YOUTH: "온통청년",
  BOKJIRO_CENTRAL: "복지로 중앙",
  BOKJIRO_LOCAL: "복지로 지자체",
  GOV24: "정부24",
};

const ACTOR_TYPE_LABELS = {
  USER: "회원",
  ANONYMOUS: "비회원",
  GUEST: "게스트",
  SYSTEM: "시스템",
};

const SEARCH_STATUS_FILTER_LABELS = {
  ALL: "전체",
  ACTIVE_ONLY: "진행중만",
  ACTIVE_AND_UPCOMING: "진행중+예정",
  UPCOMING_ONLY: "예정만",
  CLOSED_ONLY: "마감만",
};

const SEARCH_SORT_KEY_LABELS = {
  DEFAULT: "기본 정렬",
  RELEVANCE: "관련도순",
  DEADLINE_ASC: "마감임박순",
  DEADLINE_DESC: "마감여유순",
  LATEST: "최신순",
  POPULAR: "인기순",
};

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

const COLLECT_EXECUTION_TONE = {
  SCHEDULED: { label: "야간 배치", bg: INFO_BG, border: INFO_BORDER, color: INFO_TEXT },
  MANUAL: { label: "수동 실행", bg: WARNING_BG, border: WARNING_BORDER, color: WARNING_TEXT },
};

const COLLECT_LANE_TONE = {
  SNAPSHOT: { label: "스냅샷", bg: "#f8fafc", border: PANEL_LINE, color: INK2 },
  DETAIL: { label: "상세 수집", bg: "#f8fafc", border: PANEL_LINE, color: INK2 },
  ENRICHMENT: { label: "보강 처리", bg: "#f8fafc", border: PANEL_LINE, color: INK2 },
  MAINTENANCE: { label: "유지보수", bg: "#f8fafc", border: PANEL_LINE, color: INK2 },
};

const COLLECT_RESOURCE_TONE = {
  HEAVY: { label: "고부하", bg: "#fee2e2", border: "#fca5a5", color: "#b91c1c" },
  STANDARD: { label: "표준", bg: SUCCESS_BG, border: SUCCESS_BORDER, color: SUCCESS_TEXT },
  BUDGETED: { label: "예산 제한", bg: WARNING_BG, border: WARNING_BORDER, color: WARNING_TEXT },
  ON_DEMAND: { label: "온디맨드", bg: INFO_BG, border: INFO_BORDER, color: INFO_TEXT },
};

const STATUS_LABELS = {
  READY_REAL_USER_TRAFFIC: "실사용자 트래픽 준비됨",
  READY_REAL_USER_COHORT: "실사용자 코호트 확보",
  CONCENTRATED_TOP1: "top1 집중 상태",
  NO_PRIORITY_DOMINANT: "무우선순위 편중 상태",
  EXAMPLE_SMOKE_ONLY_LEADER: "예제 스모크만 leader",
  LOCAL_SEED_WITHOUT_REAL_USER_LEADER: "로컬 seed만 leader",
  SYNTHETIC_ONLY_LATEST_BATCH: "합성 데이터 위주 배치",
  MIXED_WITH_NON_REAL_BATCH: "비실사용 혼합 배치",
  DEFERRED_NO_REAL_USER_RECENT_WINDOW: "최근 실사용자 데이터 부족",
  NOT_A_CANDIDATE_NO_HISTORICAL_EXAMPLE_DOMINANCE: "예제 지배 이력 없음",
  NOT_A_CANDIDATE_PRIMARY_GATE_NOT_NON_REAL_BLOCKED: "현재 gate 기준 후보 아님",
  KEEP_PRIMARY_BASELINE: "기본 gate 유지",
  NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW: "bounded promotion review 미준비",
  DO_NOT_RUN_BOUNDED_PROMOTION_REVIEW: "bounded promotion review 실행 안 함",
  NOT_READY_FOR_EXPLICIT_PROMOTION_APPROVAL: "명시적 승격 승인 미준비",
  PROMOTION_APPROVAL_NOT_APPLICABLE: "승격 승인 대상 아님",
  APPROVAL_DECISION_NOT_READY: "승인 결정 미준비",
  APPROVAL_RECORD_NOT_READY: "승인 기록 미준비",
  BOUNDED_PROMOTION_REVIEW_RUN_NOT_READY: "bounded review run 미준비",
  NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN: "bounded review run 기준 미충족",
  BOUNDED_PROMOTION_REVIEW_RUN_DECISION_NOT_READY: "bounded review run 결정 미준비",
  NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL: "bounded review run 승인 미준비",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_DECISION_NOT_READY: "bounded review run 승인 결정 미준비",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_NOT_READY: "bounded review run 승인 기록 미준비",
  NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD: "bounded review run 승인 레코드 미준비",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_NOT_READY: "bounded review run 승인 레코드 없음",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_TRANSITION_NOT_READY: "bounded review run 전이 미준비",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITE_NOT_READY: "bounded review run 기록 쓰기 미준비",
  SCHEDULED: "예약됨",
  RUNNING: "실행 중",
  SUCCESS: "성공",
  FAILED: "실패",
  PARTIAL_SUCCESS: "부분 성공",
  CANCELLED: "취소됨",
  OPEN: "열림",
  CLOSED: "닫힘",
};

const humanizeStatusKey = (value) => {
  if (!value) return "—";
  return String(value)
    .split("_")
    .filter(Boolean)
    .map((part, index) => {
      const lower = part.toLowerCase();
      return index === 0 ? lower.charAt(0).toUpperCase() + lower.slice(1) : lower;
    })
    .join(" ");
};

const formatStatusLabel = (value) => STATUS_LABELS[value] ?? humanizeStatusKey(value);
const formatSourceType = (value) => SOURCE_TYPE_LABELS[value] ?? value ?? "—";
const formatActorType = (value) => ACTOR_TYPE_LABELS[value] ?? formatStatusLabel(value);
const formatSearchStatusFilter = (value) => SEARCH_STATUS_FILTER_LABELS[value] ?? (value ? formatStatusLabel(value) : "전체");
const formatSortKey = (value) => SEARCH_SORT_KEY_LABELS[value] ?? (value ? formatStatusLabel(value) : "기본 정렬");
const formatBooleanLabel = (value, trueLabel, falseLabel) => (value ? trueLabel : falseLabel);
const formatCodeOrStatus = (value) => value ? formatStatusLabel(value) : "미분류";

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

const formatRelativeDateTime = (value) => {
  if (!value) return "업데이트 정보 없음";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return "업데이트 정보 없음";

  const diffMinutes = Math.max(0, Math.floor((Date.now() - parsed.getTime()) / 60000));
  if (diffMinutes < 1) return "방금 갱신";
  if (diffMinutes < 60) return `${diffMinutes}분 전 갱신`;

  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours}시간 전 갱신`;

  const diffDays = Math.floor(diffHours / 24);
  return `${diffDays}일 전 갱신`;
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
  const tone = REVIEW_GATE_TONE[value] ?? { bg: "#eef2ff", border: "#c7d2fe", color: "#3730a3", label: formatStatusLabel(value) };
  return (
    <Chip
      label={tone.label}
      sx={{
        bgcolor: tone.bg,
        color: tone.color,
        border: `1px solid ${tone.border}`,
        fontWeight: 700,
        height: "auto",
        maxWidth: "100%",
        "& .MuiChip-label": {
          display: "block",
          whiteSpace: "normal",
          overflowWrap: "anywhere",
          wordBreak: "break-word",
          lineHeight: 1.25,
          py: 0.75,
        },
      }}
    />
  );
}

function ToneChip({ toneMap, value }) {
  const tone = toneMap[value] ?? { label: formatStatusLabel(value), bg: "#f8fafc", border: PANEL_LINE, color: INK2 };
  return (
    <Chip
      label={tone.label}
      size="small"
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
        <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" alignItems="flex-start" spacing={2}>
          <Box sx={{ minWidth: 0, width: "100%" }}>
            <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK2 }}>{title}</Typography>
            <Typography
              sx={{
                fontSize: { xs: 22, sm: 28 },
                fontWeight: 800,
                color: INK,
                mt: 1,
                lineHeight: 1.15,
                overflowWrap: "anywhere",
                wordBreak: "break-word",
              }}
            >
              {value}
            </Typography>
            {description && (
              <Typography
                sx={{
                  fontSize: 12,
                  color: INK3,
                  mt: 0.75,
                  overflowWrap: "anywhere",
                  wordBreak: "break-word",
                }}
              >
                {description}
              </Typography>
            )}
          </Box>
          {chip && <Box sx={{ width: { xs: "100%", sm: "auto" } }}>{chip}</Box>}
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
          sx={{
            bgcolor: "#f8fafc",
            color: INK2,
            border: `1px solid ${PANEL_LINE}`,
            height: "auto",
            maxWidth: "100%",
            "& .MuiChip-label": {
              display: "block",
              whiteSpace: "normal",
              overflowWrap: "anywhere",
              wordBreak: "break-word",
              py: 0.5,
            },
          }}
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
              <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" spacing={2}>
                <Box sx={{ minWidth: 0 }}>
                  <Typography sx={{ fontSize: 14, fontWeight: 700, color: INK, overflowWrap: "anywhere", wordBreak: "break-word" }}>{item.title}</Typography>
                  <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25, overflowWrap: "anywhere", wordBreak: "break-word" }}>
                    {formatSourceType(item.sourceType)} · {item.category}
                  </Typography>
                </Box>
                <Typography sx={{ fontSize: 14, fontWeight: 800, color: ACCENT, whiteSpace: "nowrap", alignSelf: { xs: "flex-start", sm: "flex-start" } }}>
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

function QuickJumpButton({ label, targetId, count, subtle }) {
  return (
    <Button
      variant={subtle ? "outlined" : "contained"}
      onClick={() => document.getElementById(targetId)?.scrollIntoView({ behavior: "smooth", block: "start" })}
      sx={{
        borderRadius: 999,
        px: 2,
        py: 1,
        textTransform: "none",
        fontWeight: 800,
        bgcolor: subtle ? PANEL_BG : INK,
        color: subtle ? INK2 : "white",
        borderColor: subtle ? PANEL_LINE : INK,
        boxShadow: "none",
        "&:hover": {
          bgcolor: subtle ? "#f8fafc" : "#1f2937",
          borderColor: subtle ? "#cbd5e1" : "#1f2937",
          boxShadow: "none",
        },
      }}
    >
      {label}
      {typeof count === "number" ? ` · ${formatNumber(count)}` : ""}
    </Button>
  );
}

function SectionStateCard({ title, description, action, children }) {
  return (
    <Card sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 8px 24px rgba(15,23,42,0.04)" }}>
      <CardContent sx={{ p: 2.5 }}>
        <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" spacing={2}>
          <Box>
            <Typography sx={{ fontSize: 15, fontWeight: 800, color: INK }}>{title}</Typography>
            {description && <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>{description}</Typography>}
          </Box>
          {action}
        </Stack>
        <Box mt={2}>{children}</Box>
      </CardContent>
    </Card>
  );
}

function SectionLoadingCard({ title, description }) {
  return (
    <SectionStateCard title={title} description={description}>
      <Box sx={{ display: "flex", alignItems: "center", gap: 1.5, py: 1 }}>
        <CircularProgress size={20} />
        <Typography sx={{ fontSize: 13, color: INK3 }}>데이터를 불러오는 중입니다.</Typography>
      </Box>
    </SectionStateCard>
  );
}

function SectionErrorCard({ title, description, message, onRetry }) {
  return (
    <SectionStateCard
      title={title}
      description={description}
      action={(
        <Button variant="outlined" size="small" onClick={onRetry} sx={{ alignSelf: "flex-start" }}>
          다시 시도
        </Button>
      )}
    >
      <Alert severity="error">{message}</Alert>
    </SectionStateCard>
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
  const summaryErrorMessage = summaryQuery.error?.response?.data?.message ?? "요약 데이터를 불러오지 못했습니다.";
  const breakdownErrorMessage = breakdownQuery.error?.response?.data?.message ?? "추천 상세 triage를 불러오지 못했습니다.";
  const collectErrorMessage = collectFailuresQuery.error?.response?.data?.message ?? "수집 실패 상세를 불러오지 못했습니다.";
  const searchErrorMessage = searchFailuresQuery.error?.response?.data?.message ?? "검색 실패 상세를 불러오지 못했습니다.";
  const failedSectionCount = [
    summaryQuery.isError,
    breakdownQuery.isError,
    collectFailuresQuery.isError,
    searchFailuresQuery.isError,
  ].filter(Boolean).length;
  const isRefreshing = [
    summaryQuery.isFetching,
    breakdownQuery.isFetching,
    collectFailuresQuery.isFetching,
    searchFailuresQuery.isFetching,
  ].some(Boolean);
  const latestGeneratedAt = summaryData?.generatedAt ?? breakdowns?.generatedAt ?? null;
  const openCircuitCount = collectFailures?.circuitStatuses?.filter((item) => item.open).length ?? 0;
  const retryGroupCount = searchFailures?.retryGroups?.length ?? 0;
  const recoveredGroupCount = searchFailures?.recoveredSearchGroups?.length ?? 0;
  const refetchAll = () => {
    summaryQuery.refetch();
    breakdownQuery.refetch();
    collectFailuresQuery.refetch();
    searchFailuresQuery.refetch();
  };

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
          <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5} alignItems={{ xs: "stretch", sm: "center" }}>
            <Stack spacing={0.5} sx={{ minWidth: { xs: "100%", sm: 180 } }}>
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
            <Button
              variant="contained"
              onClick={refetchAll}
              disabled={isRefreshing}
              sx={{
                alignSelf: { xs: "stretch", sm: "flex-end" },
                borderRadius: 999,
                px: 2.25,
                py: 1.1,
                textTransform: "none",
                fontWeight: 800,
                bgcolor: ACCENT,
                boxShadow: "none",
                "&:hover": { bgcolor: "#1d4ed8", boxShadow: "none" },
              }}
            >
              {isRefreshing ? "갱신 중..." : "전체 새로고침"}
            </Button>
          </Stack>
        </Stack>

        <Card sx={{ mt: 3, background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 10px 28px rgba(15,23,42,0.04)" }}>
          <CardContent sx={{ p: 2.5 }}>
            <Stack spacing={2.5}>
              <Stack direction={{ xs: "column", lg: "row" }} justifyContent="space-between" spacing={2}>
                <Box>
                  <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT, textTransform: "uppercase", letterSpacing: "0.08em" }}>
                    Ops Snapshot
                  </Typography>
                  <Typography sx={{ fontSize: 20, fontWeight: 900, color: INK, mt: 0.75, letterSpacing: "-0.02em" }}>
                    오늘 볼 운영 신호를 먼저 모았습니다
                  </Typography>
                  <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
                    실패 섹션, 회로 오픈, retry group, 마지막 갱신 시각을 먼저 확인하고 아래 상세로 내려가면 됩니다.
                  </Typography>
                </Box>
                <Stack spacing={0.5} alignItems={{ xs: "flex-start", lg: "flex-end" }}>
                  <Typography sx={{ fontSize: 12, fontWeight: 700, color: INK2 }}>
                    {formatRelativeDateTime(latestGeneratedAt)}
                  </Typography>
                  <Typography sx={{ fontSize: 12, color: INK3 }}>
                    마지막 시각 {formatDateTime(latestGeneratedAt)}
                  </Typography>
                </Stack>
              </Stack>

              <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", sm: "repeat(2, 1fr)", xl: "repeat(4, 1fr)" } }}>
                <MetricCard
                  title="실패 섹션"
                  value={formatNumber(failedSectionCount)}
                  description={failedSectionCount > 0 ? "부분 실패 있음" : "전체 섹션 응답 정상"}
                />
                <MetricCard
                  title="Open Circuits"
                  value={formatNumber(openCircuitCount)}
                  description="외부 수집 안정성 저하 신호"
                />
                <MetricCard
                  title="Retry Groups"
                  value={formatNumber(retryGroupCount)}
                  description="같은 조건 반복 검색"
                />
                <MetricCard
                  title="Recovered Groups"
                  value={formatNumber(recoveredGroupCount)}
                  description="0건 후 결과가 생긴 검색"
                />
              </Box>

              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <QuickJumpButton label="추천 개요" targetId="admin-recommendation-overview" subtle={false} />
                <QuickJumpButton label="추천 상세" targetId="admin-recommendation-breakdowns" count={breakdowns?.topRepeatedServices?.length ?? 0} subtle />
                <QuickJumpButton label="수집 실패" targetId="admin-collect-triage" count={collectFailures?.failedJobsInWindow ?? 0} subtle />
                <QuickJumpButton label="검색 실패" targetId="admin-search-triage" count={searchFailures?.zeroResultSearchesInWindow ?? 0} subtle />
              </Stack>
            </Stack>
          </CardContent>
        </Card>

        {failedSectionCount > 0 && (
          <Alert severity="warning" sx={{ mt: 3 }}>
            일부 섹션만 불러오지 못했습니다. 성공한 데이터는 그대로 표시하고, 실패한 섹션만 다시 시도할 수 있습니다.
          </Alert>
        )}

        <Stack spacing={3} mt={3}>
          {summaryQuery.isLoading && (
            <SectionLoadingCard
              title="운영 요약 로딩 중"
              description="review gate, latest batch concentration, collect/search overview를 불러오는 중입니다."
            />
          )}

          {summaryQuery.isError && (
            <SectionErrorCard
              title="운영 요약 로드 실패"
              description="summary API가 실패해도 collect/search triage 상세는 아래에서 계속 확인할 수 있습니다."
              message={summaryErrorMessage}
              onRetry={() => summaryQuery.refetch()}
            />
          )}

          {summaryData && recommendationSummary && concentration && (
            <>
              <Box id="admin-recommendation-overview" sx={{ scrollMarginTop: 96 }}>
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
                        {formatStatusLabel(recommendationSummary.recommendationReviewGate)}
                      </Typography>
                      <Typography sx={{ fontSize: 14, opacity: 0.85, mt: 1.5, maxWidth: 720, lineHeight: 1.6 }}>
                        현재 review gate는 <strong>{formatStatusLabel(recommendationSummary.recommendationReviewGate)}</strong> 입니다.
                        top1 leader signal은 <strong>{formatStatusLabel(concentration.top1LeaderSignalSummary)}</strong>, real user traffic gate는 <strong>{formatStatusLabel(recommendationSummary.realUserTrafficGateInWindow)}</strong> 입니다.
                      </Typography>
                    </Box>
                    <GateChip value={recommendationSummary.recommendationReviewGate} />
                  </Stack>
                </Box>

                <Box sx={{ display: "grid", gap: 2, mt: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(3, 1fr)" } }}>
                  <MetricCard
                    title="Top1 Leader"
                    value={concentration.top1LeaderTitle ?? "—"}
                    description={`${formatSourceType(concentration.top1LeaderSource)} · ${concentration.top1LeaderCategory}`}
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
                    value={formatStatusLabel(concentration.top1LeaderSignalSummary)}
                    description={formatStatusLabel(concentration.signalQuality)}
                    chip={<GateChip value={recommendationSummary.realUserTrafficGateInWindow} />}
                  />
                </Box>

                <Box sx={{ display: "grid", gap: 2, mt: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(4, 1fr)" } }}>
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

                <Card sx={{ mt: 2, background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 8px 24px rgba(15,23,42,0.04)" }}>
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
                      <MetricCard title="Real User Traffic Gate" value={formatStatusLabel(recommendationSummary.realUserTrafficGateInWindow)} />
                      <MetricCard title="Recommendation Review Gate" value={formatStatusLabel(recommendationSummary.recommendationReviewGate)} />
                      <MetricCard title="Concentration Readiness" value={formatStatusLabel(concentration.concentrationReadiness)} />
                      <MetricCard title="Leader Signal Summary" value={formatStatusLabel(concentration.top1LeaderSignalSummary)} />
                    </Box>
                  </CardContent>
                </Card>

                <Box sx={{ display: "grid", gap: 2, mt: 2, gridTemplateColumns: { xs: "1fr", xl: "1fr 1fr" } }}>
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
                              {formatCodeOrStatus(item.errorCode || item.status)} · {formatDateTime(item.startedAt)}
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
              </Box>
            </>
          )}

          {breakdownQuery.isLoading && (
            <SectionLoadingCard
              title="추천 상세 triage 로딩 중"
              description="top repeated services, top1 distribution leaders, fallback/click sample을 불러오는 중입니다."
            />
          )}

          {breakdownQuery.isError && (
            <SectionErrorCard
              title="추천 상세 triage 로드 실패"
              description="summary API가 살아 있으면 상단 recommendation gate와 latest batch concentration은 계속 볼 수 있습니다."
              message={breakdownErrorMessage}
              onRetry={() => breakdownQuery.refetch()}
            />
          )}

          {breakdowns && (
            <>
              <Box id="admin-recommendation-breakdowns" sx={{ scrollMarginTop: 96 }}>
                <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", xl: "1fr 1fr" } }}>
                  <ServiceListCard title="Top Repeated Services" items={breakdowns.topRepeatedServices} countLabel="rowCount" />
                  <ServiceListCard title="Top1 Distribution Leaders" items={breakdowns.top1Services} countLabel="usersAsTop1" />
                </Box>

                <Box sx={{ display: "grid", gap: 2, mt: 2, gridTemplateColumns: { xs: "1fr", xl: "1fr 1fr" } }}>
                  <CompactListCard
                    title="YOUTH Official Facets"
                    description="latest recommendation batch 기준 온통청년 official fact 분포"
                    items={breakdowns.youthOfficialFacetGroups}
                    renderItem={(group) => (
                      <Box key={group.facetKey} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
                        <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{group.label}</Typography>
                        <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap mt={1}>
                          {group.buckets?.map((bucket) => (
                            <Chip
                              key={`${group.facetKey}-${bucket.label}`}
                              label={`${bucket.label} · rows ${formatNumber(bucket.rowCount)} / services ${formatNumber(bucket.distinctServices)}`}
                              size="small"
                              sx={{
                                bgcolor: "#f8fafc",
                                color: INK2,
                                border: `1px solid ${PANEL_LINE}`,
                                height: "auto",
                                maxWidth: "100%",
                                "& .MuiChip-label": {
                                  display: "block",
                                  whiteSpace: "normal",
                                  overflowWrap: "anywhere",
                                  wordBreak: "break-word",
                                  py: 0.5,
                                },
                              }}
                            />
                          ))}
                        </Stack>
                      </Box>
                    )}
                  />
                  <CompactListCard
                    title="Gov24 Token Facets"
                    description="latest recommendation batch 기준 Gov24 사용자구분/지원유형 token 분포"
                    items={breakdowns.gov24FacetGroups}
                    renderItem={(group) => (
                      <Box key={group.facetKey} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
                        <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{group.label}</Typography>
                        <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap mt={1}>
                          {group.buckets?.map((bucket) => (
                            <Chip
                              key={`${group.facetKey}-${bucket.label}`}
                              label={`${bucket.label} · rows ${formatNumber(bucket.rowCount)} / services ${formatNumber(bucket.distinctServices)}`}
                              size="small"
                              sx={{
                                bgcolor: "#f8fafc",
                                color: INK2,
                                border: `1px solid ${PANEL_LINE}`,
                                height: "auto",
                                maxWidth: "100%",
                                "& .MuiChip-label": {
                                  display: "block",
                                  whiteSpace: "normal",
                                  overflowWrap: "anywhere",
                                  wordBreak: "break-word",
                                  py: 0.5,
                                },
                              }}
                            />
                          ))}
                        </Stack>
                      </Box>
                    )}
                  />
                </Box>
              </Box>
            </>
          )}

          <Box id="admin-collect-triage" sx={{ scrollMarginTop: 96 }}>
            <TriageSectionTitle
              eyebrow="Collect Triage"
              title="수집 실패 상세"
              description="실패 job, circuit open 상태, 최근 실패 샘플을 summary 카드 아래에서 바로 확인합니다."
            />
          </Box>

          {collectFailuresQuery.isLoading && (
            <SectionLoadingCard
              title="수집 실패 상세 로딩 중"
              description="collect-failures API를 불러오는 중입니다."
            />
          )}

          {collectFailuresQuery.isError && (
            <SectionErrorCard
              title="수집 실패 상세 로드 실패"
              description="수집 triage만 실패한 경우 recommendation/search 섹션은 그대로 확인할 수 있습니다."
              message={collectErrorMessage}
              onRetry={() => collectFailuresQuery.refetch()}
            />
          )}

          {collectFailures && (
            <>
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
                  title="Collect Lane Inventory"
                  description="nightly 자동수집과 manual lane 경계를 같은 화면에서 본다."
                  items={collectFailures.collectSourceLanes}
                  renderItem={(item) => (
                    <Box key={item.laneKey} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
                      <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" spacing={1.5}>
                        <Box sx={{ minWidth: 0 }}>
                          <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK, overflowWrap: "anywhere", wordBreak: "break-word" }}>
                            {item.label}
                          </Typography>
                          <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25, overflowWrap: "anywhere", wordBreak: "break-word" }}>
                            {formatStatusLabel(item.laneKey)} · {item.triggerPath}
                          </Typography>
                          <Typography sx={{ fontSize: 12, color: INK2, mt: 0.75, lineHeight: 1.5, overflowWrap: "anywhere", wordBreak: "break-word" }}>
                            {item.governanceReason}
                          </Typography>
                          {item.latestRun ? (
                            <Box mt={1}>
                              <Typography sx={{ fontSize: 12, fontWeight: 700, color: INK2 }}>
                                최근 실행 {formatStatusLabel(item.latestRun.status)} · {formatDateTime(item.latestRun.startedAt)}
                              </Typography>
                              <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25 }}>
                                req {formatNumber(item.latestRun.requestedCount)} / save {formatNumber(item.latestRun.savedCount)} / skip {formatNumber(item.latestRun.skippedCount)} / fail {formatNumber(item.latestRun.failedCount)}
                              </Typography>
                            </Box>
                          ) : (
                            <Typography sx={{ fontSize: 12, color: INK3, mt: 0.75 }}>
                              last run 기록 없음
                            </Typography>
                          )}
                          {item.configEntries?.length ? (
                            <Box mt={1}>
                              {item.configEntries.map((entry) => (
                                <Typography
                                  key={`${item.laneKey}-${entry.label}`}
                                  sx={{ fontSize: 12, color: INK3, mt: 0.25, overflowWrap: "anywhere", wordBreak: "break-word" }}
                                >
                                  {entry.label} · {entry.value}
                                </Typography>
                              ))}
                            </Box>
                          ) : null}
                          {item.scheduleLabel && (
                            <Typography sx={{ fontSize: 12, color: INK3, mt: 0.75 }}>
                              {item.scheduleLabel}
                            </Typography>
                          )}
                        </Box>
                        <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap alignSelf="flex-start">
                          <ToneChip toneMap={COLLECT_EXECUTION_TONE} value={item.executionMode} />
                          <ToneChip toneMap={COLLECT_LANE_TONE} value={item.laneType} />
                          <ToneChip toneMap={COLLECT_RESOURCE_TONE} value={item.resourceProfile} />
                        </Stack>
                      </Stack>
                    </Box>
                  )}
                />
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
                            {item.open ? "해제 예정" : "마지막 확인"} {formatDateTime(item.openUntil)}
                          </Typography>
                        </Box>
                        <Typography sx={{ fontSize: 12, fontWeight: 800, color: item.open ? WARNING_TEXT : SUCCESS_TEXT }}>
                          {item.open ? `열림 · ${formatNumber(item.remainingMs)}ms 남음` : "닫힘"}
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
                        <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{formatCodeOrStatus(item.errorCode) || "미분류"}</Typography>
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
                            {formatStatusLabel(item.streakStatus)} · latest {formatDateTime(item.latestStartedAt)}
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
            </>
          )}

          <Box id="admin-search-triage" sx={{ scrollMarginTop: 96 }}>
            <TriageSectionTitle
              eyebrow="Search Triage"
              title="검색 실패 상세"
              description="0건 검색 패턴, 재시도 묶음, recovery 여부를 같은 페이지에서 바로 확인합니다."
            />
          </Box>

          {searchFailuresQuery.isLoading && (
            <SectionLoadingCard
              title="검색 실패 상세 로딩 중"
              description="search-failures API를 불러오는 중입니다."
            />
          )}

          {searchFailuresQuery.isError && (
            <SectionErrorCard
              title="검색 실패 상세 로드 실패"
              description="검색 triage만 실패한 경우 recommendation/collect 섹션은 그대로 확인할 수 있습니다."
              message={searchErrorMessage}
              onRetry={() => searchFailuresQuery.refetch()}
            />
          )}

          {searchFailures && (
            <>
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
                        {item.category || "전체"} · {item.sourceType ? formatSourceType(item.sourceType) : "전체"}
                      </Typography>
                      <Typography sx={{ fontSize: 12, color: INK3, mt: 0.35 }}>
                        {formatSearchStatusFilter(item.statusFilter)} / {formatBooleanLabel(item.onlineApply, "온라인만", "온라인 포함")} / {formatBooleanLabel(item.includeClosed, "마감 포함", "마감 제외")} / {formatSortKey(item.sortKey)}
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
                        {formatActorType(item.actorType)} · {item.actorKey || "익명"}
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
                        {item.category || "전체"} / {item.sourceType ? formatSourceType(item.sourceType) : "전체"} / {formatSearchStatusFilter(item.statusFilter)}
                      </Typography>
                    </Box>
                  )}
                />
              </Box>
            </>
          )}
        </Stack>
      </Box>
    </div>
  );
}
