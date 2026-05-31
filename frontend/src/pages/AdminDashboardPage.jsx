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
const E2E_FAILURE_STORAGE_KEY = "__ADMIN_DASHBOARD_E2E_FAIL__";

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
  DEFERRED_EMPTY_COHORT: { label: "추천 데이터 없음", bg: WARNING_BG, border: WARNING_BORDER, color: WARNING_TEXT },
  DEFERRED_NO_REAL_USER_TRAFFIC: { label: "실사용자 이용 데이터 없음", bg: WARNING_BG, border: WARNING_BORDER, color: WARNING_TEXT },
  DEFERRED_REAL_USER_SAMPLE_THIN: { label: "실사용자 표본 부족", bg: WARNING_BG, border: WARNING_BORDER, color: WARNING_TEXT },
  DEFERRED_REAL_USER_CLICK_SAMPLE_THIN: { label: "실사용자 클릭 표본 부족", bg: WARNING_BG, border: WARNING_BORDER, color: WARNING_TEXT },
  DEFERRED_NON_REAL_LEADER_SIGNAL: { label: "테스트 데이터 영향으로 보류", bg: WARNING_BG, border: WARNING_BORDER, color: WARNING_TEXT },
  DEFERRED_REAL_USER_LEADER_SIGNAL_THIN: { label: "1순위 실사용자 신호 부족", bg: WARNING_BG, border: WARNING_BORDER, color: WARNING_TEXT },
  READY_CONCENTRATED_TOP1_REVIEW: { label: "1순위 집중 검토 가능", bg: SUCCESS_BG, border: SUCCESS_BORDER, color: SUCCESS_TEXT },
  READY_NO_PRIORITY_DOMINANT_REVIEW: { label: "무우선순위 리뷰 가능", bg: SUCCESS_BG, border: SUCCESS_BORDER, color: SUCCESS_TEXT },
  READY_BALANCED_LOGIC_REVIEW: { label: "추천 로직 검토 가능", bg: SUCCESS_BG, border: SUCCESS_BORDER, color: SUCCESS_TEXT },
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
  ALL_TIME_LATEST_PER_USER: "전체 기간 사용자별 최신 추천 기준",
  READY_REAL_USER_TRAFFIC: "실사용자 이용 데이터 충분",
  READY_REAL_USER_COHORT: "실사용자 그룹 확보",
  BALANCED_ENOUGH_FOR_LOGIC_REVIEW: "추천 로직 검토 가능",
  CONCENTRATED_TOP1: "1순위 집중 상태",
  NO_PRIORITY_DOMINANT: "무우선순위 편중 상태",
  EMPTY_TOP1_LEADER: "1순위 선두 없음",
  EXAMPLE_SMOKE_ONLY_LEADER: "예제 스모크만 선두",
  BOUNDED_LOCAL_WITH_EXAMPLE_LEADER: "로컬 제한군과 예제가 선두",
  LOCAL_SEED_WITHOUT_REAL_USER_LEADER: "로컬 시드만 선두",
  REAL_USER_SIGNAL_THIN_LEADER: "1순위 실사용자 신호 부족",
  MIXED_REAL_USER_LEADER: "실사용자와 테스트 데이터 혼합 선두",
  REAL_USER_ONLY_LEADER: "실사용자만 선두",
  SYNTHETIC_ONLY_LATEST_BATCH: "합성 데이터 위주 배치",
  MIXED_WITH_NON_REAL_BATCH: "비실사용 혼합 배치",
  DEFERRED_EMPTY_COHORT: "추천 데이터 없음",
  DEFERRED_EMPTY_RECENT_WINDOW: "최근 추천 데이터 없음",
  DEFERRED_NO_REAL_USER_TRAFFIC: "실사용자 이용 데이터 없음",
  DEFERRED_REAL_USER_SAMPLE_THIN: "실사용자 표본 부족",
  DEFERRED_REAL_USER_CLICK_SAMPLE_THIN: "실사용자 클릭 표본 부족",
  DEFERRED_NON_REAL_LEADER_SIGNAL: "테스트 데이터 영향으로 검토 보류",
  DEFERRED_REAL_USER_LEADER_SIGNAL_THIN: "1순위 실사용자 신호 부족",
  DEFERRED_NO_REAL_USER_RECENT_WINDOW: "최근 실사용자 데이터 부족",
  RECENT_WINDOW_STILL_TARGET_DOMINANT: "최근에도 기존 대상 서비스 편중",
  RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE: "최근 데이터에서 기존 편중 해소",
  RECENT_WINDOW_INCONCLUSIVE: "최근 데이터 판단 보류",
  RECENT_WINDOW_POLICY_CANDIDATE: "최근 기준 전환 검토 대상",
  NOT_A_CANDIDATE_NO_HISTORICAL_EXAMPLE_DOMINANCE: "예제 지배 이력 없음",
  NOT_A_CANDIDATE_PRIMARY_GATE_NOT_NON_REAL_BLOCKED: "현재 상태 기준 후보 아님",
  NOT_A_CANDIDATE_PRIMARY_REFERENCE_NOT_ALL_TIME_LATEST: "전체 기간 기준이 아니어서 후보 아님",
  NOT_A_CANDIDATE_TARGET_STILL_PRESENT_IN_RECENT_EXAMPLE_WINDOW: "최근 예제 구간에 대상 서비스가 남아 있음",
  NOT_A_CANDIDATE_NO_REAL_USER_RECENT_LATEST_USERS: "최근 실사용자 최신 추천 없음",
  NOT_A_CANDIDATE_RECENT_WINDOW_NOT_CLEAR: "최근 데이터로 편중 해소 확인 안 됨",
  KEEP_PRIMARY_BASELINE: "기본 기준 유지",
  REQUIRES_EXPLICIT_POLICY_CHANGE_REVIEW: "명시적 정책 변경 검토 필요",
  PROMOTION_READY: "전환 준비됨",
  RUN_BOUNDED_PROMOTION_REVIEW: "제한 범위 검토 실행",
  AWAIT_EXPLICIT_POLICY_REVIEW_DECISION: "명시적 정책 검토 결정 대기",
  NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW: "제한 승격 검토 미준비",
  READY_FOR_BOUNDED_PROMOTION_REVIEW: "제한 승격 검토 준비됨",
  DO_NOT_RUN_BOUNDED_PROMOTION_REVIEW: "제한 승격 검토 실행 안 함",
  NOT_READY_FOR_EXPLICIT_PROMOTION_APPROVAL: "명시적 승격 승인 미준비",
  READY_FOR_EXPLICIT_PROMOTION_APPROVAL: "명시적 승격 승인 준비됨",
  PENDING_EXPLICIT_PROMOTION_APPROVAL: "명시적 승격 승인 대기",
  BOUNDED_PROMOTION_REVIEW_APPROVED: "제한 승격 검토 승인됨",
  PROMOTION_APPROVAL_NOT_APPLICABLE: "승격 승인 대상 아님",
  APPROVAL_DECISION_NOT_READY: "승인 결정 미준비",
  AWAIT_EXPLICIT_PROMOTION_APPROVAL_DECISION: "명시적 승인 결정 대기",
  APPROVED_FOR_BOUNDED_PROMOTION_REVIEW: "제한 승격 검토 승인",
  APPROVAL_DECISION_NOT_APPLICABLE: "승인 결정 대상 아님",
  APPROVAL_RECORD_NOT_READY: "승인 기록 미준비",
  PENDING_EXPLICIT_PROMOTION_APPROVAL_RECORD: "명시적 승인 기록 대기",
  EXPLICIT_PROMOTION_APPROVAL_RECORDED: "명시적 승인 기록 완료",
  APPROVAL_RECORD_NOT_APPLICABLE: "승인 기록 대상 아님",
  BOUNDED_PROMOTION_REVIEW_RUN_NOT_READY: "제한 검토 실행 미준비",
  PENDING_BOUNDED_PROMOTION_REVIEW_RUN: "제한 검토 실행 대기",
  AWAIT_BOUNDED_PROMOTION_REVIEW_RUN: "제한 검토 실행 승인 대기",
  BOUNDED_PROMOTION_REVIEW_RUN_NOT_APPLICABLE: "제한 검토 실행 대상 아님",
  NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN: "제한 검토 실행 기준 미충족",
  READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN: "제한 검토 실행 기준 충족",
  BOUNDED_PROMOTION_REVIEW_RUN_DECISION_NOT_READY: "제한 검토 실행 결정 미준비",
  AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_DECISION: "제한 검토 실행 결정 대기",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVED: "제한 검토 실행 승인됨",
  BOUNDED_PROMOTION_REVIEW_RUN_DECISION_NOT_APPLICABLE: "제한 검토 실행 결정 대상 아님",
  NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL: "제한 검토 실행 승인 미준비",
  READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL: "제한 검토 실행 승인 준비됨",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_DECISION_NOT_READY: "제한 검토 실행 승인 결정 미준비",
  AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_DECISION: "제한 검토 실행 승인 결정 대기",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_APPROVED: "제한 검토 실행 승인됨",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_DECISION_NOT_APPLICABLE: "제한 검토 실행 승인 결정 대상 아님",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_NOT_READY: "제한 검토 실행 승인 기록 미준비",
  PENDING_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL: "제한 검토 실행 승인 대기",
  APPROVED_FOR_BOUNDED_PROMOTION_REVIEW_RUN: "제한 검토 실행 승인",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_NOT_APPLICABLE: "제한 검토 실행 승인 대상 아님",
  NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD: "제한 검토 실행 승인 레코드 미준비",
  READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD: "제한 검토 실행 승인 기록 준비됨",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_NOT_READY: "제한 검토 실행 승인 레코드 없음",
  PENDING_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD: "제한 검토 실행 승인 기록 대기",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORDED: "제한 검토 실행 승인 기록 완료",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_NOT_APPLICABLE: "제한 검토 실행 승인 기록 대상 아님",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_TRANSITION_NOT_READY: "제한 검토 실행 전이 미준비",
  AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITE: "제한 검토 실행 승인 기록 쓰기 대기",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITTEN: "제한 검토 실행 승인 기록 작성됨",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_TRANSITION_NOT_APPLICABLE: "제한 검토 실행 전이 대상 아님",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITE_NOT_READY: "제한 검토 실행 기록 쓰기 미준비",
  PENDING_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITE: "제한 검토 실행 승인 기록 쓰기 대기",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITE_COMPLETED: "제한 검토 실행 승인 기록 쓰기 완료",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITE_NOT_APPLICABLE: "제한 검토 실행 기록 쓰기 대상 아님",
  SCHEDULED: "예약됨",
  RUNNING: "실행 중",
  SUCCESS: "성공",
  FAILED: "실패",
  PARTIAL_SUCCESS: "부분 성공",
  CANCELLED: "취소됨",
  OPEN: "열림",
  CLOSED: "닫힘",
};

const STATUS_TOKEN_LABELS = {
  ALL: "전체",
  TIME: "기간",
  LATEST: "최신",
  PER: "별",
  USER: "사용자",
  USERS: "사용자",
  READY: "준비됨",
  NOT: "아님",
  FOR: "대상",
  REAL: "실사용자",
  TRAFFIC: "이용 데이터",
  COHORT: "사용자 그룹",
  GATE: "상태",
  REVIEW: "검토",
  RUN: "실행",
  APPROVAL: "승인",
  APPROVED: "승인됨",
  RECORD: "기록",
  RECORDED: "기록됨",
  WRITE: "쓰기",
  WRITTEN: "작성됨",
  PENDING: "대기",
  AWAIT: "대기",
  DECISION: "결정",
  CRITERIA: "기준",
  APPLICABLE: "대상",
  PROMOTION: "승격",
  BOUNDED: "제한 범위",
  EXPLICIT: "명시적",
  POLICY: "정책",
  PRIMARY: "기본",
  BASELINE: "기준",
  RECENT: "최근",
  WINDOW: "구간",
  CANDIDATE: "후보",
  CLEAR: "해소",
  CLEARS: "해소",
  HISTORICAL: "과거",
  EXAMPLE: "예제",
  DOMINANCE: "편중",
  TARGET: "대상",
  PRESENT: "남아 있음",
  EMPTY: "없음",
  DEFERRED: "보류",
  SAMPLE: "표본",
  THIN: "부족",
  CLICK: "클릭",
  NON: "비",
  LEADER: "선두",
  SIGNAL: "신호",
  TOP1: "1순위",
  LOCAL: "로컬",
  SEED: "시드",
  MIXED: "혼합",
  ONLY: "만",
  BALANCED: "균형",
  ENOUGH: "충분",
  LOGIC: "로직",
  INCONCLUSIVE: "판단 보류",
  STALE: "오래된",
  REFERENCE: "기준",
  MODE: "방식",
  EXECUTION: "실행",
  LAYER: "단계",
  ALIGNED: "정렬됨",
  PREREQUISITES: "선행 조건",
  MET: "충족",
  SUPPORTS: "지원",
  SUPPORTED: "지원됨",
  TRANSITION: "전환",
  COMPLETED: "완료",
  IS: "임",
  ARE: "임",
  BY: "기준",
  WITH: "포함",
  WITHOUT: "없음",
  BUT: "단",
  AND: "및",
  FROM: "에서",
  STILL: "아직",
  HAS: "있음",
  HAVE: "있음",
  DETECTED: "감지됨",
  CONFIRMED: "확인됨",
  CONDITIONS: "조건",
  CHANGE: "변경",
  ACTIVE: "활성",
  EXECUTED: "실행됨",
  ALLOWS: "허용",
  CAN: "가능",
  BE: "됨",
  PROMOTED: "전환됨",
  REQUIRES: "필요",
  STATE: "상태",
  READING: "판단",
};

const COLLECT_JOB_LABELS = {
  YOUTH: "온통청년 목록 수집",
  BOKJIRO_CENTRAL: "복지로 중앙 목록 수집",
  BOKJIRO_LOCAL: "복지로 지자체 목록 수집",
  BOKJIRO_DETAIL: "복지로 상세 수집",
  GOV24: "정부24 목록 수집",
  GOV24_DETAIL: "정부24 상세 수집",
  GOV24_SUPPORT_CONDITIONS: "정부24 지원조건 수집",
  YOUTH_DETAILS: "온통청년 상세 보강",
  BOKJIRO_DETAIL_GAP_FILL: "복지로 상세 누락 보강",
  BOKJIRO_DETAIL_REFRESH: "복지로 상세 재수집",
};

const CONFIG_LABELS = {
  Scheduler: "자동 실행 일정",
  Budget: "실행 한도",
  "List pacing": "목록 요청 간격",
  "Detail pacing": "상세 요청 간격",
  Retry: "재시도",
  "429 guard": "요청 제한 보호",
  "429 abort": "요청 제한 중단 기준",
  "Open circuit": "회로 열림 유지 시간",
  "Lock guard": "중복 실행 방지",
  "Per-source cap": "출처별 한도",
};

const humanizeStatusKey = (value) => {
  if (!value) return "—";
  const tokens = String(value)
    .split("_")
    .filter(Boolean)
    .map((token) => STATUS_TOKEN_LABELS[token] ?? token);
  return tokens.join(" / ");
};

const formatStatusLabel = (value) => STATUS_LABELS[value] ?? humanizeStatusKey(value);
const formatSourceType = (value) => SOURCE_TYPE_LABELS[value] ?? (value ? formatStatusLabel(value) : "—");
const formatActorType = (value) => ACTOR_TYPE_LABELS[value] ?? formatStatusLabel(value);
const formatSearchStatusFilter = (value) => SEARCH_STATUS_FILTER_LABELS[value] ?? (value ? formatStatusLabel(value) : "전체");
const formatSortKey = (value) => SEARCH_SORT_KEY_LABELS[value] ?? (value ? formatStatusLabel(value) : "기본 정렬");
const formatBooleanLabel = (value, trueLabel, falseLabel) => (value ? trueLabel : falseLabel);
const formatCodeOrStatus = (value) => value ? formatStatusLabel(value) : "미분류";
const formatCollectJobName = (value) => COLLECT_JOB_LABELS[value] ?? formatStatusLabel(value);
const formatConfigLabel = (value) => CONFIG_LABELS[value] ?? value ?? "설정";
const formatConfigValue = (value) => String(value ?? "—")
  .replace(/^max /, "최대 ")
  .replace(/ items\/run/g, "건/회")
  .replace(/ calls\/run/g, "회 호출/회")
  .replace(/ attempts/g, "회 시도")
  .replace(/ backoff/g, " 대기")
  .replace(/cooldown /g, "재개 대기 ")
  .replace(/ consecutive hits/g, "회 연속")
  .replace(/missing detail rows only/g, "상세 정보가 없는 항목만")
  .replace(/operator supplied rounds\/maxCallsPerRound/g, "관리자가 지정한 회차/회차별 호출 수")
  .replace(/central /g, "중앙 ")
  .replace(/local /g, "지자체 ")
  .replace(/lease /g, "잠금 ")
  .replace(/heartbeat /g, "상태 확인 ");
const formatAdminMessage = (value) => {
  if (!value) return "에러 메시지 없음";
  return String(value)
    .replace(/notification gateway returned false/gi, "알림 발송 시스템이 실패를 반환했습니다")
    .replace(/rate limit/gi, "요청 제한")
    .replace(/timeout/gi, "응답 시간 초과")
    .replace(/connection reset/gi, "연결이 끊어짐");
};

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

const readE2EFailureMode = () => {
  if (!import.meta.env.DEV || typeof window === "undefined") return null;
  try {
    return window.localStorage.getItem(E2E_FAILURE_STORAGE_KEY);
  } catch {
    return null;
  }
};

const maybeThrowE2EFailure = (section) => {
  if (readE2EFailureMode() !== section) return;
  const message = `${section} forced failure`;
  const error = new Error(message);
  error.response = { data: { message } };
  throw error;
};

const fetchSummary = async (windowDays) => {
  maybeThrowE2EFailure("summary");
  const { data } = await api.get("/api/admin/dashboard/summary", {
    params: { summaryWindowDays: windowDays, trendWindowDays: [1, 7, 30] },
  });
  return data?.data;
};

const fetchBreakdowns = async (windowDays) => {
  maybeThrowE2EFailure("breakdown");
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
    { label: "예제", value: mix?.exampleUsers },
    { label: "로컬 제한군", value: mix?.boundedLocalUsers },
    { label: "로컬 시드", value: mix?.localRealNonExampleSeedUsers },
    { label: "실사용자", value: mix?.realUserUsers },
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
  const queryBaseOptions = {
    staleTime: 30_000,
    retry: false,
  };

  const summaryQuery = useQuery({
    queryKey: ["admin-dashboard-summary", windowDays],
    queryFn: () => fetchSummary(windowDays),
    ...queryBaseOptions,
  });

  const breakdownQuery = useQuery({
    queryKey: ["admin-dashboard-breakdowns", windowDays],
    queryFn: () => fetchBreakdowns(windowDays),
    ...queryBaseOptions,
  });

  const collectFailuresQuery = useQuery({
    queryKey: ["admin-dashboard-collect-failures", windowDays],
    queryFn: () => fetchCollectFailures(windowDays),
    ...queryBaseOptions,
  });

  const searchFailuresQuery = useQuery({
    queryKey: ["admin-dashboard-search-failures", windowDays],
    queryFn: () => fetchSearchFailures(windowDays),
    ...queryBaseOptions,
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
              추천 검토 상태, 1순위 선두 신호, 사용자 구성을 한 화면에서 확인합니다.
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
                    운영 스냅샷
                  </Typography>
                  <Typography sx={{ fontSize: 20, fontWeight: 900, color: INK, mt: 0.75, letterSpacing: "-0.02em" }}>
                    오늘 볼 운영 신호를 먼저 모았습니다
                  </Typography>
                  <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
                    실패 섹션, 열린 회로, 재시도 묶음, 마지막 갱신 시각을 먼저 확인하고 아래 상세로 내려가면 됩니다.
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
                  title="열린 회로"
                  value={formatNumber(openCircuitCount)}
                  description="외부 수집 안정성 저하 신호"
                />
                <MetricCard
                  title="재시도 묶음"
                  value={formatNumber(retryGroupCount)}
                  description="같은 조건 반복 검색"
                />
                <MetricCard
                  title="복구된 묶음"
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
              description="추천 검토 상태, 최근 추천 집중도, 수집/검색 개요를 불러오는 중입니다."
            />
          )}

          {summaryQuery.isError && (
            <SectionErrorCard
              title="운영 요약 로드 실패"
              description="요약 API가 실패해도 수집/검색 상세 진단은 아래에서 계속 확인할 수 있습니다."
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
                  />
                </Box>

                <Box sx={{ display: "grid", gap: 2, mt: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(4, 1fr)" } }}>
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
              </Box>
            </>
          )}

          {breakdownQuery.isLoading && (
            <SectionLoadingCard
              title="추천 상세 진단 로딩 중"
              description="반복 노출 서비스, 1순위 분포 선두, 세부 샘플을 불러오는 중입니다."
            />
          )}

          {breakdownQuery.isError && (
            <SectionErrorCard
              title="추천 상세 진단 로드 실패"
              description="요약 데이터가 살아 있으면 상단 추천 검토 상태와 최근 추천 집중도는 계속 볼 수 있습니다."
              message={breakdownErrorMessage}
              onRetry={() => breakdownQuery.refetch()}
            />
          )}

          {breakdowns && (
            <>
              <Box id="admin-recommendation-breakdowns" sx={{ scrollMarginTop: 96 }}>
                <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", xl: "1fr 1fr" } }}>
                  <ServiceListCard title="반복 노출 상위 서비스" items={breakdowns.topRepeatedServices} countLabel="rowCount" />
                  <ServiceListCard title="1순위 분포 선두 서비스" items={breakdowns.top1Services} countLabel="usersAsTop1" />
                </Box>

                <Box sx={{ display: "grid", gap: 2, mt: 2, gridTemplateColumns: { xs: "1fr", xl: "1fr 1fr" } }}>
                  <CompactListCard
                    title="온통청년 공식 속성 분포"
                    description="최근 추천 배치 기준 온통청년 공식 속성 분포"
                    items={breakdowns.youthOfficialFacetGroups}
                    renderItem={(group) => (
                      <Box key={group.facetKey} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
                        <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{group.label}</Typography>
                        <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap mt={1}>
                          {group.buckets?.map((bucket) => (
                            <Chip
                              key={`${group.facetKey}-${bucket.label}`}
                              label={`${bucket.label} · 행 ${formatNumber(bucket.rowCount)} / 서비스 ${formatNumber(bucket.distinctServices)}`}
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
                    title="정부24 토큰 속성 분포"
                    description="최근 추천 배치 기준 정부24 사용자구분/지원유형 토큰 분포"
                    items={breakdowns.gov24FacetGroups}
                    renderItem={(group) => (
                      <Box key={group.facetKey} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
                        <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{group.label}</Typography>
                        <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap mt={1}>
                          {group.buckets?.map((bucket) => (
                            <Chip
                              key={`${group.facetKey}-${bucket.label}`}
                              label={`${bucket.label} · 행 ${formatNumber(bucket.rowCount)} / 서비스 ${formatNumber(bucket.distinctServices)}`}
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
              eyebrow="수집 진단"
              title="수집 실패 상세"
              description="실패 작업, 회로 열림 상태, 최근 실패 샘플을 요약 카드 아래에서 바로 확인합니다."
            />
          </Box>

          {collectFailuresQuery.isLoading && (
            <SectionLoadingCard
              title="수집 실패 상세 로딩 중"
              description="수집 실패 API를 불러오는 중입니다."
            />
          )}

          {collectFailuresQuery.isError && (
            <SectionErrorCard
              title="수집 실패 상세 로드 실패"
              description="수집 진단만 실패한 경우 추천/검색 섹션은 그대로 확인할 수 있습니다."
              message={collectErrorMessage}
              onRetry={() => collectFailuresQuery.refetch()}
            />
          )}

          {collectFailures && (
            <>
              <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(4, 1fr)" } }}>
              <MetricCard
                title="실패 작업"
                value={formatNumber(collectFailures.failedJobsInWindow)}
                description={`최근 ${collectFailures.windowDays}일`}
              />
              <MetricCard
                title="부분 성공"
                value={formatNumber(collectFailures.partialSuccessJobsInWindow)}
                description="일부 저장 후 종료된 작업"
              />
              <MetricCard
                title="열린 회로"
                value={formatNumber(collectFailures.circuitStatuses?.filter((item) => item.open).length)}
                description={`추적 중 ${formatNumber(collectFailures.circuitStatuses?.length)}`}
              />
              <MetricCard
                title="최근 실패 샘플"
                value={formatNumber(collectFailures.recentSamples?.length)}
                description="상세 샘플 미리보기"
              />
              </Box>

              <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", xl: "1.1fr 0.9fr" } }}>
                <CompactListCard
                  title="수집 레인 구성"
                  description="야간 자동수집과 수동 레인 구성을 같은 화면에서 봅니다."
                  items={collectFailures.collectSourceLanes}
                  renderItem={(item) => (
                    <Box key={item.laneKey} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
                      <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" spacing={1.5}>
                        <Box sx={{ minWidth: 0 }}>
                          <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK, overflowWrap: "anywhere", wordBreak: "break-word" }}>
                            {item.label}
                          </Typography>
                          <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25, overflowWrap: "anywhere", wordBreak: "break-word" }}>
                            {formatCollectJobName(item.laneKey)} · 실행 경로 {item.triggerPath}
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
                                요청 {formatNumber(item.latestRun.requestedCount)} / 저장 {formatNumber(item.latestRun.savedCount)} / 건너뜀 {formatNumber(item.latestRun.skippedCount)} / 실패 {formatNumber(item.latestRun.failedCount)}
                              </Typography>
                            </Box>
                          ) : (
                            <Typography sx={{ fontSize: 12, color: INK3, mt: 0.75 }}>
                              최근 실행 기록 없음
                            </Typography>
                          )}
                          {item.configEntries?.length ? (
                            <Box mt={1}>
                              {item.configEntries.map((entry) => (
                                <Typography
                                  key={`${item.laneKey}-${entry.label}`}
                                  sx={{ fontSize: 12, color: INK3, mt: 0.25, overflowWrap: "anywhere", wordBreak: "break-word" }}
                                >
                                  {formatConfigLabel(entry.label)} · {formatConfigValue(entry.value)}
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
                  title="작업별 현황"
                  description="실패/부분 성공이 많은 수집 작업"
                  items={collectFailures.jobBreakdowns}
                  renderItem={(item) => (
                    <Box key={`${item.jobName}-${item.latestStartedAt}`} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
                      <Stack direction="row" justifyContent="space-between" spacing={2}>
                        <Box>
                          <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{formatCollectJobName(item.jobName)}</Typography>
                          <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25 }}>
                            최근 실행 {formatDateTime(item.latestStartedAt)}
                          </Typography>
                        </Box>
                        <Stack spacing={0.5} alignItems="flex-end">
                          <Typography sx={{ fontSize: 12, fontWeight: 800, color: WARNING_TEXT }}>
                            실패 {formatNumber(item.failedCount)}
                          </Typography>
                          <Typography sx={{ fontSize: 12, fontWeight: 700, color: INK2 }}>
                            부분 성공 {formatNumber(item.partialSuccessCount)}
                          </Typography>
                        </Stack>
                      </Stack>
                    </Box>
                  )}
                />
                <CompactListCard
                  title="회로 상태"
                  description="열린 회로는 외부 수집 안정성 저하를 뜻합니다."
                  items={collectFailures.circuitStatuses}
                  renderItem={(item) => (
                    <Box key={item.circuitKey} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: item.open ? "#fff7ed" : "#f8fafc" }}>
                      <Stack direction="row" justifyContent="space-between" spacing={2}>
                        <Box>
                          <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{formatCollectJobName(item.circuitKey)}</Typography>
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
                  title="오류 코드"
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
                  title="현재 연속 상태"
                  description="같은 상태가 연속되는 작업"
                  items={collectFailures.currentJobStreaks}
                  renderItem={(item) => (
                    <Box key={`${item.jobName}-${item.streakStatus}`} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
                      <Stack direction="row" justifyContent="space-between" spacing={2}>
                        <Box>
                          <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{formatCollectJobName(item.jobName)}</Typography>
                          <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25 }}>
                            {formatStatusLabel(item.streakStatus)} · 최근 {formatDateTime(item.latestStartedAt)}
                          </Typography>
                        </Box>
                        <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT }}>{formatNumber(item.streakCount)}</Typography>
                      </Stack>
                    </Box>
                  )}
                />
                <CompactListCard
                  title="최근 실패 샘플"
                  description="에러 메시지와 저장 실패 규모"
                  items={collectFailures.recentSamples}
                  renderItem={(item) => (
                    <Box key={`${item.jobName}-${item.startedAt}-${item.errorCode}`} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
                      <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{formatCollectJobName(item.jobName)}</Typography>
                      <Typography sx={{ fontSize: 12, color: INK3, mt: 0.35 }}>
                        {formatCodeOrStatus(item.errorCode || item.status)} · {formatDateTime(item.startedAt)}
                      </Typography>
                      <Typography sx={{ fontSize: 12, color: INK2, mt: 0.75 }}>
                        요청 {formatNumber(item.requestedCount)} / 저장 {formatNumber(item.savedCount)} / 실패 {formatNumber(item.failedCount)}
                      </Typography>
                      <Typography sx={{ fontSize: 12, color: INK3, mt: 0.75, lineHeight: 1.5 }}>
                        {formatAdminMessage(item.errorMessage)}
                      </Typography>
                    </Box>
                  )}
                />
              </Box>
            </>
          )}

          <Box id="admin-search-triage" sx={{ scrollMarginTop: 96 }}>
            <TriageSectionTitle
              eyebrow="검색 진단"
              title="검색 실패 상세"
              description="0건 검색 패턴, 재시도 묶음, recovery 여부를 같은 페이지에서 바로 확인합니다."
            />
          </Box>

          {searchFailuresQuery.isLoading && (
            <SectionLoadingCard
              title="검색 실패 상세 로딩 중"
              description="검색 실패 API를 불러오는 중입니다."
            />
          )}

          {searchFailuresQuery.isError && (
            <SectionErrorCard
              title="검색 실패 상세 로드 실패"
              description="검색 진단만 실패한 경우 추천/수집 섹션은 그대로 확인할 수 있습니다."
              message={searchErrorMessage}
              onRetry={() => searchFailuresQuery.refetch()}
            />
          )}

          {searchFailures && (
            <>
              <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(4, 1fr)" } }}>
              <MetricCard
                title="0건 검색"
                value={formatNumber(searchFailures.zeroResultSearchesInWindow)}
                description={`최근 ${searchFailures.windowDays}일`}
              />
              <MetricCard
                title="재시도 묶음"
                value={formatNumber(searchFailures.retryGroups?.length)}
                description="동일 조건 반복 검색"
              />
              <MetricCard
                title="복구된 묶음"
                value={formatNumber(searchFailures.recoveredSearchGroups?.length)}
                description="0건 후 결과 복구"
              />
              <MetricCard
                title="최근 검색 샘플"
                value={formatNumber(searchFailures.recentSamples?.length)}
                description="실패 샘플 미리보기"
              />
              </Box>

              <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", xl: "1fr 1fr" } }}>
                <CompactListCard
                  title="0건 검색 지역"
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
                  title="0건 검색 필터 패턴"
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
                  title="재시도 묶음"
                  description="같은 사용자 주체가 반복한 실패 검색"
                  items={searchFailures.retryGroups}
                  renderItem={(item) => (
                    <Box key={`${item.actorType}-${item.actorKey}-${item.keyword}-${item.latestSearchedAt}`} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
                      <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{item.keyword || "키워드 없음"}</Typography>
                      <Typography sx={{ fontSize: 12, color: INK3, mt: 0.35 }}>
                        {formatActorType(item.actorType)} · {item.actorKey || "익명"}
                      </Typography>
                      <Typography sx={{ fontSize: 12, color: INK2, mt: 0.75 }}>
                        재시도 {formatNumber(item.retryCount)} · {formatDateTime(item.firstSearchedAt)} ~ {formatDateTime(item.latestSearchedAt)}
                      </Typography>
                    </Box>
                  )}
                />
                <CompactListCard
                  title="복구된 묶음"
                  description="나중에 결과가 생긴 검색 묶음"
                  items={searchFailures.recoveredSearchGroups}
                  renderItem={(item) => (
                    <Box key={`${item.actorType}-${item.actorKey}-${item.keyword}-${item.latestRecoveredAt}`} sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}>
                      <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK }}>{item.keyword || "키워드 없음"}</Typography>
                      <Typography sx={{ fontSize: 12, color: INK3, mt: 0.35 }}>
                        0건 {formatNumber(item.zeroResultCount)} / 복구 {formatNumber(item.recoveredResultCount)}
                      </Typography>
                      <Typography sx={{ fontSize: 12, color: INK2, mt: 0.75 }}>
                        최근 복구 {formatDateTime(item.latestRecoveredAt)}
                      </Typography>
                    </Box>
                  )}
                />
                <CompactListCard
                  title="최근 0건 검색 샘플"
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
