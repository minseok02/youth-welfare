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
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import Header from "../components/Header";
import FloatingNav from "../components/FloatingNav";
import api from "../lib/axios";
import {
  ADMIN_DASHBOARD_ACTION_KEYS,
  ADMIN_DASHBOARD_CARD_KEYS,
  ADMIN_DASHBOARD_DEFAULT_JUMP_PRESET,
  ADMIN_DASHBOARD_JUMP_PRESETS,
  ADMIN_DASHBOARD_LIST_KEYS,
  ADMIN_DASHBOARD_TEST_ATTRS,
  ADMIN_DASHBOARD_FOCUS_KEYS,
  buildDashboardDataAttr,
} from "../lib/adminDashboardTestHooks";

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

const POLICY_LINK_REVIEW_BUCKET_LABELS = {
  announcement_recruitment: "공고/모집형",
  benefit_support: "지원금/급부형",
  program_event: "프로그램형",
  event_culture: "행사/문화형",
  other: "기타",
};

const POLICY_DUPLICATE_REVIEW_CLASS_LABELS = {
  exact_duplicate_candidate: "exact 후보",
  mirror_or_channel_variant_candidate: "mirror 후보",
  date_or_contract_drift_candidate: "drift tail",
  title_only_false_positive_risk: "title-only 주의",
};

const NOTIFICATION_STALE_DAY_OPTIONS = [7, 14];

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

const WRAPPER_STATUS_TONE = {
  passed: { label: "정상", bg: SUCCESS_BG, border: SUCCESS_BORDER, color: SUCCESS_TEXT },
  ok: { label: "정상", bg: SUCCESS_BG, border: SUCCESS_BORDER, color: SUCCESS_TEXT },
  skipped: { label: "건너뜀", bg: WARNING_BG, border: WARNING_BORDER, color: WARNING_TEXT },
  failed: { label: "실패", bg: "#fee2e2", border: "#fca5a5", color: "#b91c1c" },
  missing: { label: "없음", bg: "#f8fafc", border: PANEL_LINE, color: INK2 },
};

const ATTENTION_SEVERITY_TONE = {
  warning: { label: "주의", bg: WARNING_BG, border: WARNING_BORDER, color: WARNING_TEXT },
  info: { label: "확인", bg: INFO_BG, border: INFO_BORDER, color: INFO_TEXT },
  success: { label: "정상", bg: SUCCESS_BG, border: SUCCESS_BORDER, color: SUCCESS_TEXT },
};

const ATTENTION_SOURCE_LABELS = {
  collect: "collect",
  "user-profile-standard-codes": "standard-codes",
  "wrapper-observation": "wrapper",
  "policy-duplicate-groups": "duplicates",
  "policy-link-reviews": "links",
};

const ADMIN_QUEUE_STATUS_OPTIONS = [
  { value: "OPEN", label: "열린 건" },
  { value: "REVIEWED", label: "처리완료" },
  { value: "ALL", label: "전체" },
];

function formatAttentionSource(sourceOrKey) {
  if (!sourceOrKey) {
    return "attention";
  }
  if (ATTENTION_SOURCE_LABELS[sourceOrKey]) {
    return ATTENTION_SOURCE_LABELS[sourceOrKey];
  }
  if (sourceOrKey.includes("standard-code")) {
    return "standard-codes";
  }
  if (sourceOrKey.includes("collect")) {
    return "collect";
  }
  if (sourceOrKey.includes("wrapper")) {
    return "wrapper";
  }
  return sourceOrKey;
}

function formatAttentionActionLabel(source) {
  switch (source) {
    case "collect":
      return "수집 실패 보기";
    case "standard-codes":
      return "표준코드 입력률 보기";
    case "wrapper":
      return "상위 wrapper 보기";
    case "notification":
      return "stale 알림 보기";
    case "duplicates":
      return "중복 리뷰 보기";
    case "links":
      return "링크 review 보기";
    default:
      return "관련 섹션 보기";
  }
}

const SECTION_FLASH_TONE = {
  warning: {
    shadow: "rgba(249,115,22,0.28)",
    background: "rgba(249,115,22,0.10)",
  },
  info: {
    shadow: "rgba(37,99,235,0.20)",
    background: "rgba(37,99,235,0.08)",
  },
  success: {
    shadow: "rgba(22,163,74,0.20)",
    background: "rgba(22,163,74,0.08)",
  },
};

const ATTENTION_PROMOTION_RANK = {
  warning: 0,
  info: 1,
  success: 2,
};

const ATTENTION_PROMOTION_KEY_RANK = {
  "section-failures": 0,
  "collect-drift": 1,
  "wrapper-warning": 2,
  "standard-code-backlog": 3,
  "notification-backlog": 4,
  "notification-stale-backlog": 5,
  "policy-duplicate-backlog": 6,
  "policy-error-report-backlog": 7,
  "policy-link-review-backlog": 8,
  "support-inquiry-backlog": 9,
};

const ACTIVE_CARD_SX = {
  transition: "border-color 180ms ease, box-shadow 180ms ease, transform 180ms ease",
  [`&[${ADMIN_DASHBOARD_TEST_ATTRS.jumpActive}="true"]`]: {
    transform: "translateY(-1px)",
  },
  [`&[${ADMIN_DASHBOARD_TEST_ATTRS.jumpActive}="true"][${ADMIN_DASHBOARD_TEST_ATTRS.jumpActiveTone}="info"]`]: {
    borderColor: INFO_BORDER,
    boxShadow: "0 16px 36px rgba(37,99,235,0.18)",
  },
  [`&[${ADMIN_DASHBOARD_TEST_ATTRS.jumpActive}="true"][${ADMIN_DASHBOARD_TEST_ATTRS.jumpActiveTone}="warning"]`]: {
    borderColor: WARNING_BORDER,
    boxShadow: "0 16px 36px rgba(249,115,22,0.22)",
  },
  [`&[${ADMIN_DASHBOARD_TEST_ATTRS.jumpActive}="true"][${ADMIN_DASHBOARD_TEST_ATTRS.jumpActiveTone}="success"]`]: {
    borderColor: SUCCESS_BORDER,
    boxShadow: "0 16px 36px rgba(22,163,74,0.18)",
  },
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
  DUPLICATE_THEN_LINK_PRIORITY: "중복 우선",
  LINK_REVIEW_PRIORITY: "링크 우선",
  DRIFT_TAIL_PRIORITY: "drift tail",
  LOW_BACKLOG_STEADY_STATE: "안정 상태",
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
const formatPolicyLinkReviewBucket = (value) => POLICY_LINK_REVIEW_BUCKET_LABELS[value] ?? (value ? formatStatusLabel(value) : "기타");
const formatPolicyDuplicateReviewClass = (value) =>
  POLICY_DUPLICATE_REVIEW_CLASS_LABELS[value] ?? (value ? formatStatusLabel(value) : "분류 없음");
const formatBooleanLabel = (value, trueLabel, falseLabel) => (value ? trueLabel : falseLabel);
const formatCodeOrStatus = (value) => value ? formatStatusLabel(value) : "미분류";
const parseRegionCorrectionCodes = (value) => (
  (value || "")
    .split(/[\s,]+/)
    .map((code) => code.trim())
    .filter(Boolean)
);
const parseSuggestedRegionCodes = (value) => (
  Array.from(new Set(Array.from(String(value || "").matchAll(/\((\d{5})\)/g)).map((match) => match[1])))
);
const resolveFieldCorrectionType = (reasonCode) => ({
  PERIOD_MISMATCH: "APPLICATION_PERIOD",
  BROKEN_LINK: "DETAIL_URL",
  ELIGIBILITY_MISMATCH: "ELIGIBILITY",
  DUPLICATE_POLICY: "DUPLICATE_POLICY",
}[reasonCode] ?? null);
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

const formatDate = (value) => {
  if (!value) return "—";
  const parsed = new Date(`${value}T00:00:00`);
  if (Number.isNaN(parsed.getTime())) return "—";
  return new Intl.DateTimeFormat("ko-KR", {
    month: "numeric",
    day: "numeric",
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

const fetchUserProfileStandardCodeCoverage = async () => {
  const { data } = await api.get("/api/admin/dashboard/user-profile-standard-code-coverage");
  return data?.data;
};

const fetchAttentionFeed = async () => {
  const { data } = await api.get("/api/admin/dashboard/attention-feed");
  return data?.data;
};

const fetchStandardCodeEffectObservation = async () => {
  const { data } = await api.get("/api/admin/dashboard/standard-code-effect-observation");
  return data?.data;
};

const fetchWrapperObservation = async () => {
  const { data } = await api.get("/api/admin/dashboard/wrapper-observation");
  return data?.data;
};

const fetchPolicyErrorReports = async (status = "OPEN") => {
  const { data } = await api.get("/api/admin/dashboard/policy-error-reports", {
    params: { limit: 5, status },
  });
  return data?.data;
};

const fetchRegionOptions = async () => {
  const { data } = await api.get("/api/admin/dashboard/region-options");
  return data?.data;
};

const fetchPolicyRegionCorrections = async () => {
  const { data } = await api.get("/api/admin/dashboard/policy-region-corrections", {
    params: { limit: 20, activeOnly: false },
  });
  return data?.data;
};

const fetchPolicyFieldCorrections = async () => {
  const { data } = await api.get("/api/admin/dashboard/policy-field-corrections", {
    params: { limit: 20 },
  });
  return data?.data;
};

const fetchSupportInquiries = async (status = "OPEN") => {
  const { data } = await api.get("/api/admin/dashboard/support-inquiries", {
    params: { limit: 5, status },
  });
  return data?.data;
};

const fetchPolicyDuplicateGroups = async (status = "OPEN") => {
  const { data } = await api.get("/api/admin/dashboard/policy-duplicate-groups", {
    params: { limit: 5, status },
  });
  return data?.data;
};

const fetchPolicyLinkReviews = async (status = "OPEN") => {
  const { data } = await api.get("/api/admin/dashboard/policy-link-reviews", {
    params: { limit: 5, status },
  });
  return data?.data;
};

const fetchNotificationStaleTargets = async (olderThanDays = 14) => {
  const { data } = await api.get("/api/admin/dashboard/notification-stale-targets", {
    params: { limit: 5, olderThanDays },
  });
  return data?.data;
};

const fetchOfficialCodebooks = async () => {
  const { data } = await api.get("/api/reference/official-codes");
  return data?.data ?? [];
};

const fetchOfficialCodebookDetail = async (codeSetKey, queryText) => {
  const { data } = await api.get(`/api/reference/official-codes/${codeSetKey}`, {
    params: {
      q: queryText?.trim() || undefined,
      limit: 20,
    },
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

function MetricCard({
  title,
  value,
  description,
  descriptionColor = INK3,
  chip,
  actionLabel,
  onAction,
  focusTarget = false,
  cardProps,
  actionProps,
}) {
  return (
    <Card
      {...(focusTarget ? buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTarget, "true") : {})}
      {...cardProps}
      sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 8px 24px rgba(15,23,42,0.04)", ...ACTIVE_CARD_SX }}
    >
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
                  color: descriptionColor,
                  mt: 0.75,
                  overflowWrap: "anywhere",
                  wordBreak: "break-word",
                }}
              >
                {description}
              </Typography>
            )}
            {actionLabel && onAction && (
              <Button
                size="small"
                variant="text"
                {...actionProps}
                sx={{ mt: 1, px: 0, textTransform: "none", fontWeight: 700 }}
                onClick={onAction}
              >
                {actionLabel}
              </Button>
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

function ServiceListCard({ title, items, countLabel, focusTarget = false, cardProps }) {
  return (
    <Card
      {...(focusTarget ? buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTarget, "true") : {})}
      {...cardProps}
      sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 8px 24px rgba(15,23,42,0.04)", height: "100%", ...ACTIVE_CARD_SX }}
    >
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

function CompactListCard({ title, description, items, renderItem, cardProps }) {
  return (
    <Card
      {...cardProps}
      sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 8px 24px rgba(15,23,42,0.04)", height: "100%", ...ACTIVE_CARD_SX }}
    >
      <CardContent sx={{ p: 2.5 }}>
        <Typography sx={{ fontSize: 15, fontWeight: 800, color: INK }}>{title}</Typography>
        {description && <Typography sx={{ fontSize: 12, color: INK3, mt: 0.5 }}>{description}</Typography>}
        <Stack spacing={1.25} mt={2}>
          {items?.length ? items.map((item, index) => renderItem(item, index)) : (
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

function QuickJumpButton({ label, targetId, count, subtle, onJump, tone = "info", focusKey = "default", preset = "quickJump", actionProps }) {
  return (
    <Button
      variant={subtle ? "outlined" : "contained"}
      {...actionProps}
      onClick={() => onJump?.(targetId, { tone, focusKey, preset })}
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
  const [selectedCodeSetKey, setSelectedCodeSetKey] = useState("");
  const [codebookQueryText, setCodebookQueryText] = useState("");
  const [policyErrorReviewNotes, setPolicyErrorReviewNotes] = useState({});
  const [policyRegionCorrectionInputs, setPolicyRegionCorrectionInputs] = useState({});
  const [policyFieldCorrectionInputs, setPolicyFieldCorrectionInputs] = useState({});
  const [latestRegionAuditResult, setLatestRegionAuditResult] = useState(null);
  const [supportInquiryReviewNotes, setSupportInquiryReviewNotes] = useState({});
  const [policyDuplicateReviewNotes, setPolicyDuplicateReviewNotes] = useState({});
  const [policyLinkReviewNotes, setPolicyLinkReviewNotes] = useState({});
  const [policyErrorStatusFilter, setPolicyErrorStatusFilter] = useState("OPEN");
  const [supportInquiryStatusFilter, setSupportInquiryStatusFilter] = useState("OPEN");
  const [policyDuplicateStatusFilter, setPolicyDuplicateStatusFilter] = useState("OPEN");
  const [policyLinkStatusFilter, setPolicyLinkStatusFilter] = useState("OPEN");
  const [notificationStaleDays, setNotificationStaleDays] = useState(14);
  const [reviewSubmittingKey, setReviewSubmittingKey] = useState(null);
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

  const standardCodeCoverageQuery = useQuery({
    queryKey: ["admin-dashboard-standard-code-coverage"],
    queryFn: fetchUserProfileStandardCodeCoverage,
    ...queryBaseOptions,
  });

  const attentionFeedQuery = useQuery({
    queryKey: ["admin-dashboard-attention-feed"],
    queryFn: fetchAttentionFeed,
    ...queryBaseOptions,
  });

  const standardCodeEffectObservationQuery = useQuery({
    queryKey: ["admin-dashboard-standard-code-effect-observation"],
    queryFn: fetchStandardCodeEffectObservation,
    ...queryBaseOptions,
  });

  const wrapperObservationQuery = useQuery({
    queryKey: ["admin-dashboard-wrapper-observation"],
    queryFn: fetchWrapperObservation,
    ...queryBaseOptions,
  });

  const policyErrorReportsQuery = useQuery({
    queryKey: ["admin-dashboard-policy-error-reports", policyErrorStatusFilter],
    queryFn: () => fetchPolicyErrorReports(policyErrorStatusFilter),
    ...queryBaseOptions,
  });

  const regionOptionsQuery = useQuery({
    queryKey: ["admin-dashboard-region-options"],
    queryFn: fetchRegionOptions,
    ...queryBaseOptions,
  });

  const policyRegionCorrectionsQuery = useQuery({
    queryKey: ["admin-dashboard-policy-region-corrections"],
    queryFn: fetchPolicyRegionCorrections,
    ...queryBaseOptions,
  });

  const policyFieldCorrectionsQuery = useQuery({
    queryKey: ["admin-dashboard-policy-field-corrections"],
    queryFn: fetchPolicyFieldCorrections,
    ...queryBaseOptions,
  });

  const supportInquiriesQuery = useQuery({
    queryKey: ["admin-dashboard-support-inquiries", supportInquiryStatusFilter],
    queryFn: () => fetchSupportInquiries(supportInquiryStatusFilter),
    ...queryBaseOptions,
  });

  const policyDuplicateGroupsQuery = useQuery({
    queryKey: ["admin-dashboard-policy-duplicate-groups", policyDuplicateStatusFilter],
    queryFn: () => fetchPolicyDuplicateGroups(policyDuplicateStatusFilter),
    ...queryBaseOptions,
  });

  const policyLinkReviewsQuery = useQuery({
    queryKey: ["admin-dashboard-policy-link-reviews", policyLinkStatusFilter],
    queryFn: () => fetchPolicyLinkReviews(policyLinkStatusFilter),
    ...queryBaseOptions,
  });

  const notificationStaleTargetsQuery = useQuery({
    queryKey: ["admin-dashboard-notification-stale-targets", notificationStaleDays],
    queryFn: () => fetchNotificationStaleTargets(notificationStaleDays),
    ...queryBaseOptions,
  });

  const officialCodebooksQuery = useQuery({
    queryKey: ["official-codebooks"],
    queryFn: fetchOfficialCodebooks,
    ...queryBaseOptions,
  });

  const officialCodebooks = officialCodebooksQuery.data ?? [];
  const effectiveSelectedCodeSetKey = selectedCodeSetKey || officialCodebooks[0]?.codeSetKey || "";

  const officialCodebookDetailQuery = useQuery({
    queryKey: ["official-codebook-detail", effectiveSelectedCodeSetKey, codebookQueryText],
    queryFn: () => fetchOfficialCodebookDetail(effectiveSelectedCodeSetKey, codebookQueryText),
    enabled: Boolean(effectiveSelectedCodeSetKey),
    ...queryBaseOptions,
  });

  const summaryData = summaryQuery.data;
  const recommendationSummary = summaryData?.recommendation;
  const concentration = recommendationSummary?.latestBatchConcentration;
  const breakdowns = breakdownQuery.data;
  const collectFailures = collectFailuresQuery.data;
  const searchFailures = searchFailuresQuery.data;
  const standardCodeCoverage = standardCodeCoverageQuery.data;
  const attentionFeed = attentionFeedQuery.data;
  const standardCodeEffectObservation = standardCodeEffectObservationQuery.data;
  const wrapperObservation = wrapperObservationQuery.data;
  const policyErrorReports = policyErrorReportsQuery.data;
  const regionOptions = regionOptionsQuery.data?.regions ?? [];
  const policyRegionCorrections = policyRegionCorrectionsQuery.data;
  const policyFieldCorrections = policyFieldCorrectionsQuery.data;
  const supportInquiries = supportInquiriesQuery.data;
  const policyDuplicateGroups = policyDuplicateGroupsQuery.data;
  const policyLinkReviews = policyLinkReviewsQuery.data;
  const notificationStaleTargets = notificationStaleTargetsQuery.data;
  const selectedCodebookSummary = officialCodebooks.find((item) => item.codeSetKey === effectiveSelectedCodeSetKey) ?? null;
  const officialCodebookDetail = officialCodebookDetailQuery.data;
  const detailRows = officialCodebookDetail?.rows ?? officialCodebookDetail?.metadata?.sampleRows ?? [];
  const summaryErrorMessage = summaryQuery.error?.response?.data?.message ?? "요약 데이터를 불러오지 못했습니다.";
  const breakdownErrorMessage = breakdownQuery.error?.response?.data?.message ?? "추천 상세 triage를 불러오지 못했습니다.";
  const collectErrorMessage = collectFailuresQuery.error?.response?.data?.message ?? "수집 실패 상세를 불러오지 못했습니다.";
  const searchErrorMessage = searchFailuresQuery.error?.response?.data?.message ?? "검색 실패 상세를 불러오지 못했습니다.";
  const standardCodeCoverageErrorMessage =
    standardCodeCoverageQuery.error?.response?.data?.message ?? "표준코드 입력 현황을 불러오지 못했습니다.";
  const attentionFeedErrorMessage =
    attentionFeedQuery.error?.response?.data?.message ?? "운영 알림 항목을 불러오지 못했습니다.";
  const standardCodeEffectObservationErrorMessage =
    standardCodeEffectObservationQuery.error?.response?.data?.message ?? "표준코드 추천 효과 관측값을 불러오지 못했습니다.";
  const wrapperObservationErrorMessage =
    wrapperObservationQuery.error?.response?.data?.message ?? "상위 wrapper 관측값을 불러오지 못했습니다.";
  const policyErrorReportsErrorMessage =
    policyErrorReportsQuery.error?.response?.data?.message ?? "정책 오류 제보 목록을 불러오지 못했습니다.";
  const regionOptionsErrorMessage =
    regionOptionsQuery.error?.response?.data?.message ?? "지역 옵션을 불러오지 못했습니다.";
  const policyRegionCorrectionsErrorMessage =
    policyRegionCorrectionsQuery.error?.response?.data?.message ?? "지역 보정 목록을 불러오지 못했습니다.";
  const policyFieldCorrectionsErrorMessage =
    policyFieldCorrectionsQuery.error?.response?.data?.message ?? "필드 보정 목록을 불러오지 못했습니다.";
  const supportInquiriesErrorMessage =
    supportInquiriesQuery.error?.response?.data?.message ?? "서비스 문의 목록을 불러오지 못했습니다.";
  const policyDuplicateGroupsErrorMessage =
    policyDuplicateGroupsQuery.error?.response?.data?.message ?? "정책 중복 review 목록을 불러오지 못했습니다.";
  const policyLinkReviewsErrorMessage =
    policyLinkReviewsQuery.error?.response?.data?.message ?? "정책 링크 review 목록을 불러오지 못했습니다.";
  const notificationStaleTargetsErrorMessage =
    notificationStaleTargetsQuery.error?.response?.data?.message ?? "stale notification target 목록을 불러오지 못했습니다.";
  const officialCodebooksErrorMessage = officialCodebooksQuery.error?.response?.data?.message ?? "공식 코드북 목록을 불러오지 못했습니다.";
  const officialCodebookDetailErrorMessage = officialCodebookDetailQuery.error?.response?.data?.message ?? "선택한 코드북 상세를 불러오지 못했습니다.";
  const animateJumpTarget = (target, tone, duration = 1400) => {
    if (!target || typeof target.animate !== "function") {
      return;
    }
    const flashTone = SECTION_FLASH_TONE[tone] ?? SECTION_FLASH_TONE.info;
    target.animate(
      [
        { boxShadow: "0 0 0 0 rgba(0,0,0,0)", backgroundColor: "rgba(0,0,0,0)" },
        { boxShadow: `0 0 0 4px ${flashTone.shadow}`, backgroundColor: flashTone.background },
        { boxShadow: "0 0 0 0 rgba(0,0,0,0)", backgroundColor: "rgba(0,0,0,0)" },
      ],
      { duration, easing: "ease-out" },
    );
  };
  const markJumpActive = (target, tone = "info", duration) => {
    if (!target) {
      return;
    }
    if (target.__jumpActiveTimeoutId) {
      window.clearTimeout(target.__jumpActiveTimeoutId);
    }
    target.setAttribute(ADMIN_DASHBOARD_TEST_ATTRS.jumpActive, "true");
    target.setAttribute(ADMIN_DASHBOARD_TEST_ATTRS.jumpActiveTone, tone);
    target.__jumpActiveTimeoutId = window.setTimeout(() => {
      target.removeAttribute(ADMIN_DASHBOARD_TEST_ATTRS.jumpActive);
      target.removeAttribute(ADMIN_DASHBOARD_TEST_ATTRS.jumpActiveTone);
      delete target.__jumpActiveTimeoutId;
    }, duration);
  };
  const jumpToSection = (targetId, options = {}) => {
    const tone = options.tone ?? "info";
    const focusKey = options.focusKey ?? ADMIN_DASHBOARD_FOCUS_KEYS.default;
    const preset = ADMIN_DASHBOARD_JUMP_PRESETS[options.preset] ?? ADMIN_DASHBOARD_JUMP_PRESETS[ADMIN_DASHBOARD_DEFAULT_JUMP_PRESET];
    const section = document.getElementById(targetId);
    if (!section) {
      return;
    }
    section.scrollIntoView({ behavior: "smooth", block: "start" });
    markJumpActive(section, tone, preset.sectionDuration);
    animateJumpTarget(section, tone, preset.sectionDuration);
    const focusTargetSelector = focusKey === "default"
      ? `[${ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTarget}="true"], [${ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTarget}="${ADMIN_DASHBOARD_FOCUS_KEYS.default}"]`
      : `[${ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTarget}="${focusKey}"]`;
    const focusTarget = section.querySelector(focusTargetSelector);
    if (focusTarget && focusTarget !== section) {
      const focusTone = focusTarget.getAttribute(ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTone) || tone;
      const focusContainer = focusTarget.closest(
        `[${ADMIN_DASHBOARD_TEST_ATTRS.adminCardKey}], [${ADMIN_DASHBOARD_TEST_ATTRS.adminListKey}], [${ADMIN_DASHBOARD_TEST_ATTRS.attentionKey}]`,
      );
      if (focusContainer && focusContainer !== section && focusContainer !== focusTarget) {
        markJumpActive(focusContainer, focusTone, preset.containerDuration);
        animateJumpTarget(focusContainer, focusTone, preset.containerDuration);
      }
      markJumpActive(focusTarget, focusTone, preset.focusDuration);
      animateJumpTarget(focusTarget, focusTone, preset.focusDuration);
    }
  };
  const failedSectionCount = [
    summaryQuery.isError,
    breakdownQuery.isError,
    collectFailuresQuery.isError,
    searchFailuresQuery.isError,
    standardCodeCoverageQuery.isError,
    attentionFeedQuery.isError,
    standardCodeEffectObservationQuery.isError,
    wrapperObservationQuery.isError,
    policyErrorReportsQuery.isError,
    regionOptionsQuery.isError,
    policyRegionCorrectionsQuery.isError,
    policyFieldCorrectionsQuery.isError,
    supportInquiriesQuery.isError,
    policyDuplicateGroupsQuery.isError,
    policyLinkReviewsQuery.isError,
    notificationStaleTargetsQuery.isError,
    officialCodebooksQuery.isError,
    officialCodebookDetailQuery.isError,
  ].filter(Boolean).length;
  const isRefreshing = [
    summaryQuery.isFetching,
    breakdownQuery.isFetching,
    collectFailuresQuery.isFetching,
    searchFailuresQuery.isFetching,
    standardCodeCoverageQuery.isFetching,
    attentionFeedQuery.isFetching,
    standardCodeEffectObservationQuery.isFetching,
    wrapperObservationQuery.isFetching,
    policyErrorReportsQuery.isFetching,
    regionOptionsQuery.isFetching,
    policyRegionCorrectionsQuery.isFetching,
    policyFieldCorrectionsQuery.isFetching,
    supportInquiriesQuery.isFetching,
    policyDuplicateGroupsQuery.isFetching,
    policyLinkReviewsQuery.isFetching,
    notificationStaleTargetsQuery.isFetching,
    officialCodebooksQuery.isFetching,
    officialCodebookDetailQuery.isFetching,
  ].some(Boolean);
  const latestGeneratedAt = summaryData?.generatedAt ?? breakdowns?.generatedAt ?? null;
  const openCircuitCount = collectFailures?.circuitStatuses?.filter((item) => item.open).length ?? 0;
  const firstCollectCircuitKey =
    collectFailures?.circuitStatuses?.find((item) => item.open)?.circuitKey
    ?? collectFailures?.circuitStatuses?.[0]?.circuitKey
    ?? null;
  const retryGroupCount = searchFailures?.retryGroups?.length ?? 0;
  const recoveredGroupCount = searchFailures?.recoveredSearchGroups?.length ?? 0;
  const wrapperMissingAllStandardCodes =
    wrapperObservation?.currentPriorityUsersMissingAllStandardCodes
    ?? wrapperObservation?.activeBaselineUsersMissingAllStandardCodes
    ?? standardCodeCoverage?.usersMissingAllStandardCodes
    ?? 0;
  const wrapperRecommendationObservationStatus =
    wrapperObservation?.currentPriorityRecommendationObservationStatus
    || wrapperObservation?.activeBaselineRecommendationObservationStatus
    || "—";
  const wrapperActiveBaselineReuseLabel = wrapperObservation?.currentPriorityAvailable
    ? (wrapperObservation.currentPriorityActiveBaselineReused ? "재사용" : "직접 실행")
    : "—";
  const wrapperAttentionFeedStatus =
    wrapperObservation?.currentPriorityAttentionFeedStatus
    || wrapperObservation?.activeBaselineAttentionFeedStatus
    || "—";
  const wrapperAttentionFeedItemCount =
    wrapperObservation?.currentPriorityAttentionFeedItemCount
    ?? wrapperObservation?.activeBaselineAttentionFeedItemCount
    ?? attentionFeed?.itemCount
    ?? 0;
  const wrapperAttentionFeedPrimaryTitle =
    wrapperObservation?.currentPriorityAttentionFeedItemTitles
    || wrapperObservation?.activeBaselineAttentionFeedItemTitles
    || attentionFeed?.items?.[0]?.title
    || "대표 항목 없음";
  const wrapperAttentionFeedPrimaryKey =
    wrapperObservation?.currentPriorityAttentionFeedItemKeys
    || wrapperObservation?.activeBaselineAttentionFeedItemKeys
    || attentionFeed?.items?.[0]?.key
    || "";
  const wrapperAttentionFeedPrimarySeverity =
    (wrapperObservation?.currentPriorityAttentionFeedWarningItemCount ?? wrapperObservation?.activeBaselineAttentionFeedWarningItemCount ?? 0) > 0
      ? "warning"
      : (attentionFeed?.items?.[0]?.severity || (wrapperAttentionFeedItemCount > 0 ? "warning" : "success"));
  const wrapperAttentionFeedPrimarySource = formatAttentionSource(
    wrapperAttentionFeedPrimaryKey || attentionFeed?.items?.[0]?.source
  );
  const wrapperAttentionFeedPrimaryTargetId = (
    attentionFeed?.items?.find((item) => item.key === wrapperAttentionFeedPrimaryKey)?.targetId
    || attentionFeed?.items?.find((item) => item.title === wrapperAttentionFeedPrimaryTitle)?.targetId
    || "admin-attention-queue"
  );
  const wrapperAttentionFeedPrimaryActionLabel = formatAttentionActionLabel(wrapperAttentionFeedPrimarySource);
  const wrapperAttentionFeedTone = wrapperAttentionFeedItemCount > 0
    ? { label: "주시", bg: WARNING_BG, border: WARNING_BORDER, color: WARNING_TEXT }
    : { label: "양호", bg: SUCCESS_BG, border: SUCCESS_BORDER, color: SUCCESS_TEXT };
  const wrapperMissingStandardCodeTone = wrapperMissingAllStandardCodes > 0
    ? { label: "정리 필요", bg: WARNING_BG, border: WARNING_BORDER, color: WARNING_TEXT }
    : { label: "양호", bg: SUCCESS_BG, border: SUCCESS_BORDER, color: SUCCESS_TEXT };
  const wrapperMissingDelta = wrapperObservation?.currentPriorityUsersMissingAllStandardCodesDelta ?? 0;
  const wrapperMissingDeltaLabel = wrapperObservation?.currentPriorityUsersMissingAllStandardCodesDeltaLabel
    || (
      wrapperObservation?.currentPriorityPreviousAvailable
        ? (
            wrapperMissingDelta > 0
              ? `${formatNumber(wrapperMissingDelta)} 증가`
              : wrapperMissingDelta < 0
                ? `${formatNumber(Math.abs(wrapperMissingDelta))} 감소`
                : "변화 없음"
          )
        : "이전값 없음"
    );
  const wrapperMissingDeltaColor = wrapperObservation?.currentPriorityPreviousAvailable
    ? (
        wrapperMissingDelta > 0
          ? WARNING_TEXT
          : wrapperMissingDelta < 0
            ? SUCCESS_TEXT
            : INK3
      )
    : INK3;
  const wrapperObservationChangeLabel = wrapperObservation?.currentPriorityRecommendationObservationStatusTransitionLabel
    || (
      wrapperObservation?.currentPriorityPreviousAvailable
        ? (
            wrapperObservation?.currentPriorityRecommendationObservationStatusChanged
              ? `${wrapperObservation.currentPriorityPreviousRecommendationObservationStatus || "—"} -> ${wrapperRecommendationObservationStatus}`
              : "변화 없음"
          )
        : "이전값 없음"
    );
  const wrapperObservationChangeColor = wrapperObservation?.currentPriorityPreviousAvailable
    ? (
        wrapperObservation?.currentPriorityRecommendationObservationStatusChanged
          ? INFO_TEXT
          : INK3
      )
    : INK3;
  const wrapperSnapshotAlert = wrapperObservation?.promotedAlert
    ? {
        severity: wrapperObservation.promotedAlert.severity,
        title: wrapperObservation.promotedAlert.title,
        message: wrapperObservation.promotedAlert.message,
      }
    : null;
  const localAttentionQueueItems = [
    failedSectionCount > 0 ? {
      key: "section-failures",
      severity: "warning",
      title: "대시보드 섹션 재시도 필요",
      message: `${formatNumber(failedSectionCount)}개 섹션이 실패했습니다. 실패 카드부터 다시 불러오세요.`,
    } : null,
  ].filter(Boolean);
  const attentionQueueItems = [
    ...localAttentionQueueItems,
    ...(attentionFeed?.items ?? []),
  ];
  const promotedAttentionItems = [...attentionQueueItems]
    .sort((left, right) => {
      const leftRank = ATTENTION_PROMOTION_RANK[left.severity] ?? 99;
      const rightRank = ATTENTION_PROMOTION_RANK[right.severity] ?? 99;
      if (leftRank !== rightRank) {
        return leftRank - rightRank;
      }
      const leftKeyRank = ATTENTION_PROMOTION_KEY_RANK[left.key] ?? 99;
      const rightKeyRank = ATTENTION_PROMOTION_KEY_RANK[right.key] ?? 99;
      return leftKeyRank - rightKeyRank;
    })
    .slice(0, 3);
  const refetchAll = () => {
    summaryQuery.refetch();
    breakdownQuery.refetch();
    collectFailuresQuery.refetch();
    searchFailuresQuery.refetch();
    standardCodeCoverageQuery.refetch();
    attentionFeedQuery.refetch();
    standardCodeEffectObservationQuery.refetch();
    wrapperObservationQuery.refetch();
    policyErrorReportsQuery.refetch();
    regionOptionsQuery.refetch();
    policyRegionCorrectionsQuery.refetch();
    policyFieldCorrectionsQuery.refetch();
    supportInquiriesQuery.refetch();
    policyDuplicateGroupsQuery.refetch();
    policyLinkReviewsQuery.refetch();
    notificationStaleTargetsQuery.refetch();
    officialCodebooksQuery.refetch();
    if (effectiveSelectedCodeSetKey) {
      officialCodebookDetailQuery.refetch();
    }
  };

  const handleReviewPolicyErrorReport = async (reportId) => {
    setReviewSubmittingKey(`policy-${reportId}`);
    try {
      await api.post(`/api/admin/dashboard/policy-error-reports/${reportId}/review`, {
        reviewNote: policyErrorReviewNotes[reportId]?.trim() || null,
      });
      setPolicyErrorReviewNotes((prev) => {
        const next = { ...prev };
        delete next[reportId];
        return next;
      });
      policyErrorReportsQuery.refetch();
      policyRegionCorrectionsQuery.refetch();
      attentionFeedQuery.refetch();
    } finally {
      setReviewSubmittingKey(null);
    }
  };

  const updatePolicyRegionCorrectionInput = (reportId, patch) => {
    setPolicyRegionCorrectionInputs((prev) => ({
      ...prev,
      [reportId]: {
        ...(prev[reportId] ?? {}),
        ...patch,
      },
    }));
  };

  const updatePolicyFieldCorrectionInput = (reportId, patch) => {
    setPolicyFieldCorrectionInputs((prev) => ({
      ...prev,
      [reportId]: {
        ...(prev[reportId] ?? {}),
        ...patch,
      },
    }));
  };

  const clearPolicyErrorInputs = (reportId) => {
    setPolicyErrorReviewNotes((prev) => {
      const next = { ...prev };
      delete next[reportId];
      return next;
    });
    setPolicyRegionCorrectionInputs((prev) => {
      const next = { ...prev };
      delete next[reportId];
      return next;
    });
    setPolicyFieldCorrectionInputs((prev) => {
      const next = { ...prev };
      delete next[reportId];
      return next;
    });
  };

  const handleApplyPolicyRegionCorrection = async (item, nationwide = false) => {
    const input = policyRegionCorrectionInputs[item.reportId] ?? {};
    const selectedRegionCodes = Array.isArray(input.selectedRegionCodes) ? input.selectedRegionCodes : [];
    const regionCodes = nationwide ? [] : (
      selectedRegionCodes.length > 0 ? selectedRegionCodes : parseRegionCorrectionCodes(input.regionCodes)
    );
    if (!nationwide && regionCodes.length === 0) {
      window.alert("지역코드를 입력하세요.");
      return;
    }
    const requestKey = `policy-region-${item.reportId}-${nationwide ? "nationwide" : "regions"}`;
    setReviewSubmittingKey(requestKey);
    try {
      await api.post("/api/admin/dashboard/policy-region-corrections", {
        policyId: item.policyId,
        nationwide,
        regionCodes,
        reportId: item.reportId,
        correctionNote: input.correctionNote?.trim()
          || policyErrorReviewNotes[item.reportId]?.trim()
          || null,
      });
      clearPolicyErrorInputs(item.reportId);
      policyErrorReportsQuery.refetch();
      policyRegionCorrectionsQuery.refetch();
      attentionFeedQuery.refetch();
    } finally {
      setReviewSubmittingKey(null);
    }
  };

  const handleRunPolicyRegionAudit = async () => {
    setReviewSubmittingKey("policy-region-audit");
    try {
      const { data } = await api.post("/api/admin/dashboard/policy-region-audit/run", null, {
        params: { limit: 50000 },
      });
      setLatestRegionAuditResult(data?.data ?? null);
      policyErrorReportsQuery.refetch();
      attentionFeedQuery.refetch();
    } finally {
      setReviewSubmittingKey(null);
    }
  };

  const handleApplySuggestedRegionCorrection = async (item, regionCodes) => {
    if (!regionCodes || regionCodes.length === 0) {
      return;
    }
    const requestKey = `policy-region-${item.reportId}-suggested`;
    setReviewSubmittingKey(requestKey);
    try {
      await api.post("/api/admin/dashboard/policy-region-corrections", {
        policyId: item.policyId,
        nationwide: false,
        regionCodes,
        reportId: item.reportId,
        correctionNote: policyErrorReviewNotes[item.reportId]?.trim() || "자동 지역감사 후보 적용",
      });
      clearPolicyErrorInputs(item.reportId);
      policyErrorReportsQuery.refetch();
      policyRegionCorrectionsQuery.refetch();
      attentionFeedQuery.refetch();
    } finally {
      setReviewSubmittingKey(null);
    }
  };

  const handleApplyPolicyFieldCorrection = async (item) => {
    const correctionType = resolveFieldCorrectionType(item.reasonCode);
    if (!correctionType) {
      return;
    }
    const input = policyFieldCorrectionInputs[item.reportId] ?? {};
    const requestKey = `policy-field-${item.reportId}`;
    setReviewSubmittingKey(requestKey);
    try {
      await api.post("/api/admin/dashboard/policy-field-corrections", {
        policyId: item.policyId,
        reportId: item.reportId,
        correctionType,
        applyStartDate: input.applyStartDate || null,
        applyEndDate: input.applyEndDate || null,
        detailUrl: input.detailUrl?.trim() || null,
        eligibilityText: input.eligibilityText?.trim() || null,
        duplicateOfPolicyId: input.duplicateOfPolicyId ? Number(input.duplicateOfPolicyId) : null,
        correctionNote: input.correctionNote?.trim()
          || policyErrorReviewNotes[item.reportId]?.trim()
          || null,
      });
      clearPolicyErrorInputs(item.reportId);
      policyErrorReportsQuery.refetch();
      policyFieldCorrectionsQuery.refetch();
      attentionFeedQuery.refetch();
    } finally {
      setReviewSubmittingKey(null);
    }
  };

  const handleRevertPolicyRegionCorrection = async (correctionId) => {
    setReviewSubmittingKey(`policy-region-revert-${correctionId}`);
    try {
      await api.post(`/api/admin/dashboard/policy-region-corrections/${correctionId}/revert`, {
        reviewNote: "관리자 대시보드에서 수동 보정 되돌림",
      });
      policyRegionCorrectionsQuery.refetch();
      policyErrorReportsQuery.refetch();
      attentionFeedQuery.refetch();
    } finally {
      setReviewSubmittingKey(null);
    }
  };

  const handleReviewSupportInquiry = async (inquiryId) => {
    setReviewSubmittingKey(`support-${inquiryId}`);
    try {
      await api.post(`/api/admin/dashboard/support-inquiries/${inquiryId}/review`, {
        reviewNote: supportInquiryReviewNotes[inquiryId]?.trim() || null,
      });
      setSupportInquiryReviewNotes((prev) => {
        const next = { ...prev };
        delete next[inquiryId];
        return next;
      });
      supportInquiriesQuery.refetch();
      attentionFeedQuery.refetch();
    } finally {
      setReviewSubmittingKey(null);
    }
  };

  const handleReviewPolicyDuplicateGroup = async (item) => {
    const requestKey = `duplicate-${item.sourceType}-${item.title}-${item.hostOrgKey ?? ""}`;
    setReviewSubmittingKey(requestKey);
    try {
      await api.post("/api/admin/dashboard/policy-duplicate-groups/review", {
        sourceType: item.sourceType,
        title: item.title,
        hostOrgKey: item.hostOrgKey ?? "",
        hostOrgLabel: item.hostOrgLabel ?? null,
        reviewNote: policyDuplicateReviewNotes[requestKey]?.trim() || null,
      });
      setPolicyDuplicateReviewNotes((prev) => {
        const next = { ...prev };
        delete next[requestKey];
        return next;
      });
      policyDuplicateGroupsQuery.refetch();
      attentionFeedQuery.refetch();
    } finally {
      setReviewSubmittingKey(null);
    }
  };

  const handleReviewPolicyLink = async (serviceId) => {
    setReviewSubmittingKey(`policy-link-${serviceId}`);
    try {
      await api.post(`/api/admin/dashboard/policy-link-reviews/${serviceId}/review`, {
        reviewNote: policyLinkReviewNotes[serviceId]?.trim() || null,
      });
      setPolicyLinkReviewNotes((prev) => {
        const next = { ...prev };
        delete next[serviceId];
        return next;
      });
      policyLinkReviewsQuery.refetch();
      attentionFeedQuery.refetch();
    } finally {
      setReviewSubmittingKey(null);
    }
  };

  const handleHideNotificationStaleTarget = async (item) => {
    const requestKey = `notification-stale-${item.kind}-${item.deeplinkUrl}`;
    setReviewSubmittingKey(requestKey);
    try {
      await api.post("/api/admin/dashboard/notification-backlog/hide-stale", {
        kind: item.kind,
        title: item.title,
        deeplinkUrl: item.deeplinkUrl,
        olderThanDays: notificationStaleTargets?.olderThanDays ?? notificationStaleDays,
      });
      notificationStaleTargetsQuery.refetch();
      summaryQuery.refetch();
      attentionFeedQuery.refetch();
    } finally {
      setReviewSubmittingKey(null);
    }
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

        {promotedAttentionItems.length > 0 && (
          <Card
            id="admin-ops-alerts"
            sx={{
              mt: 3,
              background: "#fffaf0",
              border: `1px solid ${WARNING_BORDER}`,
              boxShadow: "0 10px 28px rgba(15,23,42,0.04)",
            }}
          >
            <CardContent sx={{ p: 2.5 }}>
              <Stack spacing={2}>
                <Stack direction={{ xs: "column", md: "row" }} justifyContent="space-between" spacing={1.5}>
                  <Box>
                    <Typography sx={{ fontSize: 12, fontWeight: 800, color: WARNING_TEXT, textTransform: "uppercase", letterSpacing: "0.08em" }}>
                      운영 알림
                    </Typography>
                    <Typography sx={{ fontSize: 20, fontWeight: 900, color: INK, mt: 0.75, letterSpacing: "-0.02em" }}>
                      지금 바로 볼 우선 신호 {formatNumber(attentionQueueItems.length)}건
                    </Typography>
                    <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
                      상위 경고와 표준코드 backlog, collect drift를 먼저 읽고 아래 상세 섹션으로 내려가면 됩니다.
                    </Typography>
                  </Box>
                  <Button
                    size="small"
                    variant="outlined"
                    sx={{ alignSelf: { xs: "flex-start", md: "center" }, textTransform: "none", borderRadius: 999 }}
                    onClick={() => jumpToSection("admin-attention-queue", { tone: "warning", preset: "attention" })}
                  >
                    주의 항목 큐 보기
                  </Button>
                </Stack>

                <Box sx={{ display: "grid", gap: 1.25, gridTemplateColumns: { xs: "1fr", xl: "repeat(3, 1fr)" } }}>
                  {promotedAttentionItems.map((item) => (
                    <Box
                      key={item.key}
                      {...buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.attentionKey, item.key)}
                      sx={{
                        p: 1.75,
                        borderRadius: 2,
                        border: `1px solid ${ATTENTION_SEVERITY_TONE[item.severity]?.border ?? PANEL_LINE}`,
                        bgcolor: "#ffffff",
                      }}
                    >
                      <Stack spacing={1}>
                        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                          <ToneChip toneMap={ATTENTION_SEVERITY_TONE} value={item.severity} />
                          <Typography sx={{ fontSize: 14, fontWeight: 800, color: INK }}>
                            {item.title}
                          </Typography>
                        </Stack>
                        <Typography sx={{ fontSize: 13, color: INK2 }}>
                          {item.message}
                        </Typography>
                        {item.targetId ? (
                          <Button
                            size="small"
                            variant="text"
                            {...buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.attentionActionKey, item.key)}
                            sx={{ alignSelf: "flex-start", px: 0, textTransform: "none", fontWeight: 700 }}
                            onClick={() => jumpToSection(item.targetId, { tone: item.severity, preset: "attention" })}
                          >
                            해당 섹션 보기
                          </Button>
                        ) : null}
                      </Stack>
                    </Box>
                  ))}
                </Box>
              </Stack>
            </CardContent>
          </Card>
        )}

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
                    실패 섹션, 열린 회로, 표준코드 미입력 규모, current priority 관측 상태를 먼저 확인하고 아래 상세로 내려가면 됩니다.
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

              <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", sm: "repeat(2, 1fr)", xl: "repeat(7, 1fr)" } }}>
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
                <MetricCard
                  title="표준코드 미입력"
                  value={formatNumber(wrapperMissingAllStandardCodes)}
                  description={`current priority 승격 기준 · ${wrapperMissingDeltaLabel}`}
                  descriptionColor={wrapperMissingDeltaColor}
                  chip={<ToneChip toneMap={{ active: wrapperMissingStandardCodeTone }} value="active" />}
                />
                <MetricCard
                  title="attention 대표"
                  value={wrapperAttentionFeedPrimaryTitle}
                  description={`current priority 승격 기준 · ${formatNumber(wrapperAttentionFeedItemCount)}건 · ${wrapperAttentionFeedPrimarySource} / ${wrapperAttentionFeedPrimarySeverity}`}
                  chip={<ToneChip toneMap={{ active: wrapperAttentionFeedTone }} value="active" />}
                  actionLabel={wrapperAttentionFeedPrimaryActionLabel}
                  cardProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.attentionPrimaryKey, wrapperAttentionFeedPrimaryKey || "unknown")}
                  actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.attentionPrimaryActionKey, wrapperAttentionFeedPrimaryKey || "unknown")}
                  onAction={() => jumpToSection(wrapperAttentionFeedPrimaryTargetId, { tone: wrapperAttentionFeedPrimarySeverity, preset: "attention" })}
                />
                <MetricCard
                  title="priority 관측"
                  value={wrapperRecommendationObservationStatus}
                  description={`active baseline ${wrapperActiveBaselineReuseLabel} · ${wrapperObservationChangeLabel} · attention ${wrapperAttentionFeedStatus}`}
                  descriptionColor={wrapperObservationChangeColor}
                  chip={<ToneChip toneMap={WRAPPER_STATUS_TONE} value={wrapperRecommendationObservationStatus} />}
                />
              </Box>

              {wrapperSnapshotAlert && (
                <Alert severity={wrapperSnapshotAlert.severity}>
                  <strong>{wrapperSnapshotAlert.title}</strong>
                  {` · ${wrapperSnapshotAlert.message}`}
                </Alert>
              )}

              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <QuickJumpButton
                  label="추천 개요"
                  targetId="admin-recommendation-overview"
                  subtle={false}
                  onJump={jumpToSection}
                  tone="info"
                  actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminActionKey, ADMIN_DASHBOARD_ACTION_KEYS.quickJumpRecommendationOverview)}
                />
                <QuickJumpButton
                  label="주의 항목"
                  targetId="admin-attention-queue"
                  count={attentionQueueItems.length}
                  subtle
                  onJump={jumpToSection}
                  tone="warning"
                  actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminActionKey, ADMIN_DASHBOARD_ACTION_KEYS.quickJumpAttentionQueue)}
                />
                <QuickJumpButton
                  label="표준코드 입력률"
                  targetId="admin-standard-code-coverage"
                  count={standardCodeCoverage?.usersMissingAllStandardCodes ?? 0}
                  subtle
                  onJump={jumpToSection}
                  tone={standardCodeCoverage?.usersMissingAllStandardCodes > 0 ? "warning" : "success"}
                  actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminActionKey, ADMIN_DASHBOARD_ACTION_KEYS.quickJumpStandardCodeCoverage)}
                />
                <QuickJumpButton
                  label="표준코드 효과"
                  targetId="admin-standard-code-effect"
                  count={standardCodeEffectObservation?.welfareScenarioCount ?? 0}
                  subtle
                  onJump={jumpToSection}
                  tone="info"
                  actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminActionKey, ADMIN_DASHBOARD_ACTION_KEYS.quickJumpStandardCodeEffect)}
                />
                <QuickJumpButton
                  label="상위 wrapper"
                  targetId="admin-wrapper-observation"
                  count={wrapperObservation?.currentPriorityUsersMissingAllStandardCodes ?? 0}
                  subtle
                  onJump={jumpToSection}
                  tone={wrapperSnapshotAlert?.severity ?? "info"}
                  actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminActionKey, ADMIN_DASHBOARD_ACTION_KEYS.quickJumpWrapperObservation)}
                />
                <QuickJumpButton
                  label="공식 코드북"
                  targetId="admin-reference-codebooks"
                  count={officialCodebooks.length}
                  subtle
                  onJump={jumpToSection}
                  tone="info"
                  actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminActionKey, ADMIN_DASHBOARD_ACTION_KEYS.quickJumpReferenceCodebooks)}
                />
                <QuickJumpButton
                  label="추천 상세"
                  targetId="admin-recommendation-breakdowns"
                  count={breakdowns?.topRepeatedServices?.length ?? 0}
                  subtle
                  onJump={jumpToSection}
                  tone="info"
                  actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminActionKey, ADMIN_DASHBOARD_ACTION_KEYS.quickJumpRecommendationBreakdowns)}
                />
                <QuickJumpButton
                  label="수집 실패"
                  targetId="admin-collect-triage"
                  count={collectFailures?.failedJobsInWindow ?? 0}
                  subtle
                  onJump={jumpToSection}
                  tone={collectFailures?.failedJobsInWindow > 0 ? "warning" : "success"}
                  focusKey={ADMIN_DASHBOARD_FOCUS_KEYS.default}
                  actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminActionKey, ADMIN_DASHBOARD_ACTION_KEYS.quickJumpCollectTriage)}
                />
                <QuickJumpButton
                  label="검색 실패"
                  targetId="admin-search-triage"
                  count={searchFailures?.zeroResultSearchesInWindow ?? 0}
                  subtle
                  onJump={jumpToSection}
                  tone={searchFailures?.zeroResultSearchesInWindow > 0 ? "warning" : "success"}
                  focusKey={ADMIN_DASHBOARD_FOCUS_KEYS.searchWarning}
                  actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminActionKey, ADMIN_DASHBOARD_ACTION_KEYS.quickJumpSearchTriage)}
                />
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
                            </Stack>
                            {item.targetId ? (
                              <Button
                                size="small"
                                variant="outlined"
                                {...buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.attentionActionKey, item.key)}
                                sx={{ textTransform: "none", borderRadius: 999 }}
                                onClick={() => jumpToSection(item.targetId, { tone: item.severity, preset: "attention" })}
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

          <Box id="admin-standard-code-coverage" sx={{ scrollMarginTop: 96 }}>
            <Card sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 10px 28px rgba(15,23,42,0.04)" }}>
              <CardContent sx={{ p: 2.5 }}>
                <Stack spacing={2}>
                  <Box>
                    <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT, textTransform: "uppercase", letterSpacing: "0.08em" }}>
                      프로필 정합성
                    </Typography>
                    <Typography sx={{ fontSize: 20, fontWeight: 900, color: INK, mt: 0.75, letterSpacing: "-0.02em" }}>
                      주거·복지 표준코드 입력률
                    </Typography>
                    <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
                      추천 정확도에 직접 쓰는 표준코드 4개가 실제 사용자 프로필에 얼마나 채워졌는지, `users`와 `user_profiles` 간 안전한 보정 후보가 있는지 같이 봅니다.
                    </Typography>
                  </Box>

                  {standardCodeCoverageQuery.isLoading && (
                    <SectionLoadingCard
                      title="표준코드 coverage 로딩 중"
                      description="사용자 프로필의 표준코드 입력 현황을 계산하는 중입니다."
                    />
                  )}

                  {standardCodeCoverageQuery.isError && (
                    <SectionErrorCard
                      title="표준코드 coverage 로드 실패"
                      description="운영용 coverage 집계를 불러오지 못했습니다."
                      message={standardCodeCoverageErrorMessage}
                      onRetry={() => standardCodeCoverageQuery.refetch()}
                    />
                  )}

                  {standardCodeCoverage && !standardCodeCoverageQuery.isLoading && !standardCodeCoverageQuery.isError && (
                    <Stack spacing={2}>
                      <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", sm: "repeat(2, 1fr)", xl: "repeat(4, 1fr)" } }}>
                        <MetricCard
                          title="전체 사용자"
                          value={formatNumber(standardCodeCoverage.totalUsers)}
                          description={formatRelativeDateTime(standardCodeCoverage.generatedAt)}
                        />
                        <MetricCard
                          title="1개 이상 입력"
                          value={formatNumber(standardCodeCoverage.usersWithAnyStandardCode)}
                          description="표준코드가 하나라도 채워진 사용자"
                        />
                        <MetricCard
                          title="전부 미입력"
                          value={formatNumber(standardCodeCoverage.usersMissingAllStandardCodes)}
                          description="4개 표준코드가 모두 비어 있는 사용자"
                          focusTarget
                        />
                        <MetricCard
                          title="안전 보정 후보"
                          value={formatNumber(standardCodeCoverage.safeReconcileCandidateRows)}
                          description="충돌 없이 복사 가능한 user/profile gap"
                        />
                      </Box>

                      <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(2, 1fr)", xl: "repeat(4, 1fr)" } }}>
                        <MetricCard title="주거형태 입력" value={formatNumber(standardCodeCoverage.houseTenureFilled)} description="`house_tenure_code` 채움 수" />
                        <MetricCard title="주택유형 입력" value={formatNumber(standardCodeCoverage.housingTypeFilled)} description="`housing_type_code` 채움 수" />
                        <MetricCard title="기초생활수급권자 입력" value={formatNumber(standardCodeCoverage.basicLivingRecipientTypeFilled)} description="`basic_living_recipient_type_code` 채움 수" />
                        <MetricCard title="장애등급 입력" value={formatNumber(standardCodeCoverage.disabilityGradeFilled)} description="`disability_grade_code` 채움 수" />
                      </Box>

                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        <Chip label={`profile row 보유 ${formatNumber(standardCodeCoverage.usersWithProfileRow)}`} size="small" variant="outlined" />
                        <Chip label={`profile row 없음 ${formatNumber(standardCodeCoverage.usersWithoutProfileRow)}`} size="small" variant="outlined" />
                        <Chip label={`users/profile 충돌 ${formatNumber(standardCodeCoverage.conflictingValueGapRows)}`} size="small" variant="outlined" />
                        <Chip label={`profile only gap ${formatNumber(standardCodeCoverage.profileOnlyGapRows)}`} size="small" variant="outlined" />
                        <Chip label={`user only gap ${formatNumber(standardCodeCoverage.userOnlyGapRows)}`} size="small" variant="outlined" />
                      </Stack>
                    </Stack>
                  )}
                </Stack>
              </CardContent>
            </Card>
          </Box>

          <Box id="admin-policy-error-reports" sx={{ scrollMarginTop: 96 }}>
            <Card sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 10px 28px rgba(15,23,42,0.04)" }}>
              <CardContent sx={{ p: 2.5 }}>
                <Stack spacing={2}>
                  <Box>
                    <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT, textTransform: "uppercase", letterSpacing: "0.08em" }}>
                      사용자 제보
                    </Typography>
                    <Typography sx={{ fontSize: 20, fontWeight: 900, color: INK, mt: 0.75, letterSpacing: "-0.02em" }}>
                      정책 오류 제보 recent queue
                    </Typography>
                    <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
                      정책 상세에서 사용자가 보낸 오류 제보를 최근 열린 순서대로 봅니다. 지역, 기간, 자격조건, 링크 같은 데이터 품질 문제를 운영에서 빠르게 triage하는 용도입니다.
                    </Typography>
                  </Box>

                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    {ADMIN_QUEUE_STATUS_OPTIONS.map((option) => (
                      <Chip
                        key={option.value}
                        label={option.label}
                        clickable
                        color={policyErrorStatusFilter === option.value ? "primary" : "default"}
                        variant={policyErrorStatusFilter === option.value ? "filled" : "outlined"}
                        onClick={() => setPolicyErrorStatusFilter(option.value)}
                      />
                    ))}
                    <Button
                      size="small"
                      variant="outlined"
                      disabled={reviewSubmittingKey === "policy-region-audit"}
                      onClick={handleRunPolicyRegionAudit}
                      sx={{ textTransform: "none", borderRadius: 999, whiteSpace: "nowrap" }}
                    >
                      {reviewSubmittingKey === "policy-region-audit" ? "감사 중..." : "지역 감사 실행"}
                    </Button>
                  </Stack>

                  {latestRegionAuditResult && (
                    <Alert severity={latestRegionAuditResult.createdReportCount > 0 ? "warning" : "info"}>
                      {`지역 감사 완료 · 스캔 ${formatNumber(latestRegionAuditResult.scannedCount)}건 · 후보 ${formatNumber(latestRegionAuditResult.candidateCount)}건 · 신규 제보 ${formatNumber(latestRegionAuditResult.createdReportCount)}건`}
                    </Alert>
                  )}

                  {regionOptionsQuery.isError && (
                    <Alert severity="warning">{regionOptionsErrorMessage}</Alert>
                  )}

                  {policyRegionCorrectionsQuery.isError && (
                    <Alert severity="warning">{policyRegionCorrectionsErrorMessage}</Alert>
                  )}

                  {policyFieldCorrectionsQuery.isError && (
                    <Alert severity="warning">{policyFieldCorrectionsErrorMessage}</Alert>
                  )}

                  {policyErrorReportsQuery.isLoading && (
                    <SectionLoadingCard
                      title="정책 오류 제보 로딩 중"
                      description="최근 열린 오류 제보를 불러오는 중입니다."
                    />
                  )}

                  {policyErrorReportsQuery.isError && (
                    <SectionErrorCard
                      title="정책 오류 제보 로드 실패"
                      description="정책 상세에서 접수된 제보 queue를 읽지 못했습니다."
                      message={policyErrorReportsErrorMessage}
                      onRetry={() => policyErrorReportsQuery.refetch()}
                    />
                  )}

                  {policyErrorReports && !policyErrorReportsQuery.isLoading && !policyErrorReportsQuery.isError && (
                    <Stack spacing={2}>
                      <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(2, 1fr)" } }}>
                        <MetricCard
                          title="열린 제보"
                          value={formatNumber(policyErrorReports.openCount)}
                          description="아직 운영 확인이 필요한 오류 제보"
                        />
                        <MetricCard
                          title="최근 24시간 신규"
                          value={formatNumber(policyErrorReports.recentOpenCount24h)}
                          description="지난 24시간 동안 새로 열린 오류 제보"
                        />
                        <MetricCard
                          title="표시 제보"
                          value={formatNumber(policyErrorReports.recentReports?.length ?? 0)}
                          description="최근 열린 제보 샘플"
                        />
                      </Box>

                      {(policyErrorReports.recentReports?.length ?? 0) === 0 ? (
                        <Alert severity="success">
                          {policyErrorStatusFilter === "OPEN"
                            ? "현재 열린 정책 오류 제보가 없습니다."
                            : policyErrorStatusFilter === "REVIEWED"
                              ? "표시할 처리완료 정책 오류 제보가 없습니다."
                              : "표시할 정책 오류 제보가 없습니다."}
                        </Alert>
                      ) : (
                        <CompactListCard
                          title="최근 정책 오류 제보"
                          description="가장 최근 열린 제보부터 표시합니다."
                          items={policyErrorReports.recentReports ?? []}
                          renderItem={(item) => (
                            <Box
                              key={item.reportId}
                              sx={{ p: 1.75, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}
                            >
                              <Stack spacing={1}>
                                <Stack direction={{ xs: "column", md: "row" }} justifyContent="space-between" spacing={1.5}>
                                  <Box sx={{ minWidth: 0 }}>
                                    <Typography sx={{ fontSize: 14, fontWeight: 800, color: INK, overflowWrap: "anywhere", wordBreak: "break-word" }}>
                                      {item.policyTitle}
                                    </Typography>
                                    <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25, overflowWrap: "anywhere", wordBreak: "break-word" }}>
                                      {formatSourceType(item.sourceType)} · {item.sourceId || "sourceId 없음"} · {formatDateTime(item.createdAt)}
                                    </Typography>
                                  </Box>
                                  <Chip
                                    label={item.reasonLabel}
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
                                  제보자 {item.userKey || "익명"}{item.note ? ` · ${item.note}` : " · 추가 메모 없음"}
                                </Typography>
                                {item.status === "REVIEWED" && (
                                  <Alert severity="success" sx={{ py: 0 }}>
                                    {`처리완료 · ${item.reviewedByUserKey || "운영자"} · ${formatDateTime(item.reviewedAt)}`}
                                    {item.reviewNote ? ` · ${item.reviewNote}` : ""}
                                  </Alert>
                                )}
                                {item.reasonCode === "REGION_MISMATCH" && item.status !== "REVIEWED" && (
                                  <Box sx={{ p: 1.5, borderRadius: 1.5, border: `1px solid ${INFO_BORDER}`, bgcolor: INFO_BG }}>
                                    <Stack spacing={1}>
                                      <Typography sx={{ fontSize: 12, fontWeight: 800, color: INFO_TEXT }}>
                                        지역 보정
                                      </Typography>
                                      {parseSuggestedRegionCodes(item.note).length > 0 && (
                                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                          <Button
                                            size="small"
                                            variant="outlined"
                                            disabled={reviewSubmittingKey === `policy-region-${item.reportId}-suggested`}
                                            onClick={() => handleApplySuggestedRegionCorrection(item, parseSuggestedRegionCodes(item.note))}
                                            sx={{ textTransform: "none", borderRadius: 999, whiteSpace: "nowrap" }}
                                          >
                                            {reviewSubmittingKey === `policy-region-${item.reportId}-suggested` ? "적용 중..." : "감사 후보 적용"}
                                          </Button>
                                          {parseSuggestedRegionCodes(item.note).map((code) => (
                                            <Chip
                                              key={`${item.reportId}-${code}`}
                                              label={regionOptions.find((region) => region.regionCode === code)?.label ?? code}
                                              size="small"
                                              variant="outlined"
                                            />
                                          ))}
                                        </Stack>
                                      )}
                                      <Stack direction={{ xs: "column", md: "row" }} spacing={1} alignItems={{ xs: "stretch", md: "center" }}>
                                        <TextField
                                          size="small"
                                          placeholder="행정구역코드"
                                          value={policyRegionCorrectionInputs[item.reportId]?.regionCodes ?? ""}
                                          onChange={(event) => updatePolicyRegionCorrectionInput(item.reportId, {
                                            regionCodes: event.target.value,
                                            selectedRegionCodes: [],
                                          })}
                                          helperText="예: 28110 또는 28110, 28200"
                                          sx={{ flex: 1.1 }}
                                          disabled={reviewSubmittingKey?.startsWith(`policy-region-${item.reportId}-`)}
                                        />
                                        <Select
                                          multiple
                                          size="small"
                                          displayEmpty
                                          value={policyRegionCorrectionInputs[item.reportId]?.selectedRegionCodes ?? []}
                                          onChange={(event) => updatePolicyRegionCorrectionInput(item.reportId, {
                                            selectedRegionCodes: Array.isArray(event.target.value)
                                              ? event.target.value
                                              : parseRegionCorrectionCodes(event.target.value),
                                            regionCodes: "",
                                          })}
                                          renderValue={(selected) => {
                                            if (!selected || selected.length === 0) {
                                              return "지역 선택";
                                            }
                                            return selected
                                              .map((code) => regionOptions.find((region) => region.regionCode === code)?.label ?? code)
                                              .join(", ");
                                          }}
                                          disabled={
                                            regionOptionsQuery.isLoading
                                            || reviewSubmittingKey?.startsWith(`policy-region-${item.reportId}-`)
                                          }
                                          sx={{ flex: 1.4, minWidth: 220 }}
                                        >
                                          {regionOptions.map((region) => (
                                            <MenuItem key={region.regionCode} value={region.regionCode}>
                                              {region.label}
                                            </MenuItem>
                                          ))}
                                        </Select>
                                        <TextField
                                          size="small"
                                          placeholder="보정 메모"
                                          value={policyRegionCorrectionInputs[item.reportId]?.correctionNote ?? ""}
                                          onChange={(event) => updatePolicyRegionCorrectionInput(item.reportId, {
                                            correctionNote: event.target.value,
                                          })}
                                          sx={{ flex: 1.2 }}
                                          disabled={reviewSubmittingKey?.startsWith(`policy-region-${item.reportId}-`)}
                                        />
                                        <Button
                                          size="small"
                                          variant="contained"
                                          disabled={reviewSubmittingKey?.startsWith(`policy-region-${item.reportId}-`)}
                                          onClick={() => handleApplyPolicyRegionCorrection(item, false)}
                                          sx={{ textTransform: "none", borderRadius: 999, whiteSpace: "nowrap" }}
                                        >
                                          {reviewSubmittingKey === `policy-region-${item.reportId}-regions` ? "보정 중..." : "지역 보정"}
                                        </Button>
                                        <Button
                                          size="small"
                                          variant="outlined"
                                          disabled={reviewSubmittingKey?.startsWith(`policy-region-${item.reportId}-`)}
                                          onClick={() => handleApplyPolicyRegionCorrection(item, true)}
                                          sx={{ textTransform: "none", borderRadius: 999, whiteSpace: "nowrap" }}
                                        >
                                          {reviewSubmittingKey === `policy-region-${item.reportId}-nationwide` ? "처리 중..." : "전국 처리"}
                                        </Button>
                                      </Stack>
                                    </Stack>
                                  </Box>
                                )}
                                {resolveFieldCorrectionType(item.reasonCode) && item.status !== "REVIEWED" && (
                                  <Box sx={{ p: 1.5, borderRadius: 1.5, border: `1px solid ${PANEL_LINE}`, bgcolor: "#f8fafc" }}>
                                    <Stack spacing={1}>
                                      <Typography sx={{ fontSize: 12, fontWeight: 800, color: INK2 }}>
                                        필드 보정
                                      </Typography>
                                      {item.reasonCode === "PERIOD_MISMATCH" && (
                                        <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                                          <TextField
                                            size="small"
                                            type="date"
                                            label="신청 시작일"
                                            InputLabelProps={{ shrink: true }}
                                            value={policyFieldCorrectionInputs[item.reportId]?.applyStartDate ?? ""}
                                            onChange={(event) => updatePolicyFieldCorrectionInput(item.reportId, { applyStartDate: event.target.value })}
                                            sx={{ flex: 1 }}
                                          />
                                          <TextField
                                            size="small"
                                            type="date"
                                            label="신청 종료일"
                                            InputLabelProps={{ shrink: true }}
                                            value={policyFieldCorrectionInputs[item.reportId]?.applyEndDate ?? ""}
                                            onChange={(event) => updatePolicyFieldCorrectionInput(item.reportId, { applyEndDate: event.target.value })}
                                            sx={{ flex: 1 }}
                                          />
                                        </Stack>
                                      )}
                                      {item.reasonCode === "BROKEN_LINK" && (
                                        <TextField
                                          size="small"
                                          placeholder="새 원문 URL"
                                          value={policyFieldCorrectionInputs[item.reportId]?.detailUrl ?? ""}
                                          onChange={(event) => updatePolicyFieldCorrectionInput(item.reportId, { detailUrl: event.target.value })}
                                        />
                                      )}
                                      {item.reasonCode === "ELIGIBILITY_MISMATCH" && (
                                        <TextField
                                          size="small"
                                          multiline
                                          minRows={2}
                                          placeholder="보정할 자격조건/설명"
                                          value={policyFieldCorrectionInputs[item.reportId]?.eligibilityText ?? ""}
                                          onChange={(event) => updatePolicyFieldCorrectionInput(item.reportId, { eligibilityText: event.target.value })}
                                        />
                                      )}
                                      {item.reasonCode === "DUPLICATE_POLICY" && (
                                        <TextField
                                          size="small"
                                          type="number"
                                          placeholder="중복 기준 정책 ID"
                                          value={policyFieldCorrectionInputs[item.reportId]?.duplicateOfPolicyId ?? ""}
                                          onChange={(event) => updatePolicyFieldCorrectionInput(item.reportId, { duplicateOfPolicyId: event.target.value })}
                                        />
                                      )}
                                      <Stack direction={{ xs: "column", md: "row" }} spacing={1}>
                                        <TextField
                                          size="small"
                                          placeholder="보정 메모"
                                          value={policyFieldCorrectionInputs[item.reportId]?.correctionNote ?? ""}
                                          onChange={(event) => updatePolicyFieldCorrectionInput(item.reportId, { correctionNote: event.target.value })}
                                          sx={{ flex: 1 }}
                                        />
                                        <Button
                                          size="small"
                                          variant="contained"
                                          disabled={reviewSubmittingKey === `policy-field-${item.reportId}`}
                                          onClick={() => handleApplyPolicyFieldCorrection(item)}
                                          sx={{ textTransform: "none", borderRadius: 999, whiteSpace: "nowrap" }}
                                        >
                                          {reviewSubmittingKey === `policy-field-${item.reportId}` ? "보정 중..." : "필드 보정"}
                                        </Button>
                                      </Stack>
                                    </Stack>
                                  </Box>
                                )}
                                <Stack direction={{ xs: "column", md: "row" }} spacing={1} alignItems={{ xs: "stretch", md: "center" }}>
                                  <TextField
                                    size="small"
                                    placeholder="운영 메모 (선택)"
                                    value={policyErrorReviewNotes[item.reportId] ?? ""}
                                    onChange={(event) => setPolicyErrorReviewNotes((prev) => ({
                                      ...prev,
                                      [item.reportId]: event.target.value,
                                    }))}
                                    sx={{ flex: 1 }}
                                    disabled={item.status === "REVIEWED"}
                                  />
                                  <Button
                                    size="small"
                                    variant="outlined"
                                    disabled={item.status === "REVIEWED" || reviewSubmittingKey === `policy-${item.reportId}`}
                                    onClick={() => handleReviewPolicyErrorReport(item.reportId)}
                                    sx={{ textTransform: "none", borderRadius: 999, whiteSpace: "nowrap" }}
                                  >
                                    {item.status === "REVIEWED"
                                      ? "처리완료됨"
                                      : reviewSubmittingKey === `policy-${item.reportId}` ? "처리 중..." : "처리완료"}
                                  </Button>
                                </Stack>
                              </Stack>
                            </Box>
                          )}
                        />
                      )}

                      {policyRegionCorrections?.corrections?.length > 0 && (
                        <CompactListCard
                          title="지역 보정 이력"
                          description="활성 보정은 수집/백필에서 보호되고, 비활성 이력은 감사 추적용으로 남습니다."
                          items={policyRegionCorrections.corrections}
                          renderItem={(item) => (
                            <Box
                              key={item.correctionId}
                              sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#f8fafc" }}
                            >
                              <Stack direction={{ xs: "column", md: "row" }} justifyContent="space-between" spacing={1.5}>
                                <Box sx={{ minWidth: 0 }}>
                                  <Typography sx={{ fontSize: 13, fontWeight: 800, color: INK, overflowWrap: "anywhere" }}>
                                    {item.policyTitle}
                                  </Typography>
                                  <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25 }}>
                                    {formatSourceType(item.sourceType)} · {item.sourceId || "sourceId 없음"} · {item.correctionScope} · {item.active ? "활성" : "비활성"}
                                  </Typography>
                                  <Typography sx={{ fontSize: 12, color: INK2, mt: 0.5, overflowWrap: "anywhere" }}>
                                    {(item.regions ?? []).length > 0
                                      ? item.regions.map((region) => `${region.sidoName} ${region.sggName} (${region.regionCode})`).join(", ")
                                      : "전국 처리"}
                                  </Typography>
                                </Box>
                                <Button
                                  size="small"
                                  variant="outlined"
                                  color="warning"
                                  disabled={!item.active || reviewSubmittingKey === `policy-region-revert-${item.correctionId}`}
                                  onClick={() => handleRevertPolicyRegionCorrection(item.correctionId)}
                                  sx={{ textTransform: "none", borderRadius: 999, whiteSpace: "nowrap", alignSelf: { xs: "stretch", md: "center" } }}
                                >
                                  {!item.active ? "되돌림 완료" : reviewSubmittingKey === `policy-region-revert-${item.correctionId}` ? "되돌리는 중..." : "되돌리기"}
                                </Button>
                              </Stack>
                            </Box>
                          )}
                        />
                      )}
                      {policyFieldCorrections?.corrections?.length > 0 && (
                        <CompactListCard
                          title="필드 보정 이력"
                          description="기간, 링크, 자격조건, 중복 정책 보정 기록입니다."
                          items={policyFieldCorrections.corrections}
                          renderItem={(item) => (
                            <Box
                              key={item.correctionId}
                              sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#f8fafc" }}
                            >
                              <Typography sx={{ fontSize: 13, fontWeight: 800, color: INK, overflowWrap: "anywhere" }}>
                                {item.policyTitle}
                              </Typography>
                              <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25 }}>
                                {formatSourceType(item.sourceType)} · {item.sourceId || "sourceId 없음"} · {item.correctionType}
                              </Typography>
                              <Typography sx={{ fontSize: 12, color: INK2, mt: 0.5, overflowWrap: "anywhere" }}>
                                {item.correctionJson}
                              </Typography>
                            </Box>
                          )}
                        />
                      )}
                    </Stack>
                  )}
                </Stack>
              </CardContent>
            </Card>
          </Box>

          <Box id="admin-support-inquiries" sx={{ scrollMarginTop: 96 }}>
            <Card sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 10px 28px rgba(15,23,42,0.04)" }}>
              <CardContent sx={{ p: 2.5 }}>
                <Stack spacing={2}>
                  <Box>
                    <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT, textTransform: "uppercase", letterSpacing: "0.08em" }}>
                      사용자 문의
                    </Typography>
                    <Typography sx={{ fontSize: 20, fontWeight: 900, color: INK, mt: 0.75, letterSpacing: "-0.02em" }}>
                      서비스 문의 recent queue
                    </Typography>
                    <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
                      로그인, 추천, 챗봇, 검색, 알림처럼 서비스를 쓰다가 막힌 내용을 최근 열린 순서대로 봅니다. 정책 데이터 오류 제보와는 별도 queue입니다.
                    </Typography>
                  </Box>

                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    {ADMIN_QUEUE_STATUS_OPTIONS.map((option) => (
                      <Chip
                        key={option.value}
                        label={option.label}
                        clickable
                        color={supportInquiryStatusFilter === option.value ? "primary" : "default"}
                        variant={supportInquiryStatusFilter === option.value ? "filled" : "outlined"}
                        onClick={() => setSupportInquiryStatusFilter(option.value)}
                      />
                    ))}
                  </Stack>

                  {supportInquiriesQuery.isLoading && (
                    <SectionLoadingCard
                      title="서비스 문의 로딩 중"
                      description="최근 열린 서비스 문의를 불러오는 중입니다."
                    />
                  )}

                  {supportInquiriesQuery.isError && (
                    <SectionErrorCard
                      title="서비스 문의 로드 실패"
                      description="서비스 사용 문의 queue를 읽지 못했습니다."
                      message={supportInquiriesErrorMessage}
                      onRetry={() => supportInquiriesQuery.refetch()}
                    />
                  )}

                  {supportInquiries && !supportInquiriesQuery.isLoading && !supportInquiriesQuery.isError && (
                    <Stack spacing={2}>
                      <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(2, 1fr)" } }}>
                        <MetricCard
                          title="열린 문의"
                          value={formatNumber(supportInquiries.openCount)}
                          description="아직 운영 확인이 필요한 서비스 문의"
                        />
                        <MetricCard
                          title="최근 24시간 신규"
                          value={formatNumber(supportInquiries.recentOpenCount24h)}
                          description="지난 24시간 동안 새로 열린 서비스 문의"
                        />
                        <MetricCard
                          title="표시 문의"
                          value={formatNumber(supportInquiries.recentInquiries?.length ?? 0)}
                          description="최근 열린 문의 샘플"
                        />
                      </Box>

                      {(supportInquiries.recentInquiries?.length ?? 0) === 0 ? (
                        <Alert severity="success">
                          {supportInquiryStatusFilter === "OPEN"
                            ? "현재 열린 서비스 문의가 없습니다."
                            : supportInquiryStatusFilter === "REVIEWED"
                              ? "표시할 처리완료 서비스 문의가 없습니다."
                              : "표시할 서비스 문의가 없습니다."}
                        </Alert>
                      ) : (
                        <CompactListCard
                          title="최근 서비스 문의"
                          description="가장 최근 열린 문의부터 표시합니다."
                          items={supportInquiries.recentInquiries ?? []}
                          renderItem={(item) => (
                            <Box
                              key={item.inquiryId}
                              sx={{ p: 1.75, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}
                            >
                              <Stack spacing={1}>
                                <Stack direction={{ xs: "column", md: "row" }} justifyContent="space-between" spacing={1.5}>
                                  <Box sx={{ minWidth: 0 }}>
                                    <Typography sx={{ fontSize: 14, fontWeight: 800, color: INK, overflowWrap: "anywhere", wordBreak: "break-word" }}>
                                      {item.categoryLabel}
                                    </Typography>
                                    <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25, overflowWrap: "anywhere", wordBreak: "break-word" }}>
                                      {item.contactEmail} · {item.routePath || "경로 정보 없음"} · {formatDateTime(item.createdAt)}
                                    </Typography>
                                  </Box>
                                  <Chip
                                    label={item.categoryLabel}
                                    size="small"
                                    sx={{
                                      bgcolor: INFO_BG,
                                      color: INFO_TEXT,
                                      border: `1px solid ${INFO_BORDER}`,
                                      fontWeight: 700,
                                      alignSelf: { xs: "flex-start", md: "center" },
                                    }}
                                  />
                                </Stack>
                                <Typography sx={{ fontSize: 13, color: INK2 }}>
                                  {item.message}
                                </Typography>
                                <Typography sx={{ fontSize: 12, color: INK3 }}>
                                  문의자 {item.userKey || "비로그인/미연결"}
                                </Typography>
                                {item.status === "REVIEWED" && (
                                  <Alert severity="success" sx={{ py: 0 }}>
                                    {`처리완료 · ${item.reviewedByUserKey || "운영자"} · ${formatDateTime(item.reviewedAt)}`}
                                    {item.reviewNote ? ` · ${item.reviewNote}` : ""}
                                  </Alert>
                                )}
                                <Stack direction={{ xs: "column", md: "row" }} spacing={1} alignItems={{ xs: "stretch", md: "center" }}>
                                  <TextField
                                    size="small"
                                    placeholder="운영 메모 (선택)"
                                    value={supportInquiryReviewNotes[item.inquiryId] ?? ""}
                                    onChange={(event) => setSupportInquiryReviewNotes((prev) => ({
                                      ...prev,
                                      [item.inquiryId]: event.target.value,
                                    }))}
                                    sx={{ flex: 1 }}
                                    disabled={item.status === "REVIEWED"}
                                  />
                                  <Button
                                    size="small"
                                    variant="outlined"
                                    disabled={item.status === "REVIEWED" || reviewSubmittingKey === `support-${item.inquiryId}`}
                                    onClick={() => handleReviewSupportInquiry(item.inquiryId)}
                                    sx={{ textTransform: "none", borderRadius: 999, whiteSpace: "nowrap" }}
                                  >
                                    {item.status === "REVIEWED"
                                      ? "처리완료됨"
                                      : reviewSubmittingKey === `support-${item.inquiryId}` ? "처리 중..." : "처리완료"}
                                  </Button>
                                </Stack>
                              </Stack>
                            </Box>
                          )}
                        />
                      )}
                    </Stack>
                  )}
                </Stack>
              </CardContent>
            </Card>
          </Box>

          <Box id="admin-policy-duplicate-groups" sx={{ scrollMarginTop: 96 }}>
            <Card sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 10px 28px rgba(15,23,42,0.04)" }}>
              <CardContent sx={{ p: 2.5 }}>
                <Stack spacing={2}>
                  <Box>
                    <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT, textTransform: "uppercase", letterSpacing: "0.08em" }}>
                      데이터 품질 리뷰
                    </Typography>
                    <Typography sx={{ fontSize: 20, fontWeight: 900, color: INK, mt: 0.75, letterSpacing: "-0.02em" }}>
                      정책 중복 review queue
                    </Typography>
                    <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
                      `YOUTH`와 `BOKJIRO_LOCAL`에서 title/host 기준으로 반복되는 정책 묶음을 최근 review queue로 보여줍니다. broad parser 변경 전, 실제 운영자가 중복 검토 우선순위를 잡는 용도입니다.
                    </Typography>
                  </Box>

                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    {ADMIN_QUEUE_STATUS_OPTIONS.map((option) => (
                      <Chip
                        key={option.value}
                        label={option.label}
                        clickable
                        color={policyDuplicateStatusFilter === option.value ? "primary" : "default"}
                        variant={policyDuplicateStatusFilter === option.value ? "filled" : "outlined"}
                        onClick={() => setPolicyDuplicateStatusFilter(option.value)}
                      />
                    ))}
                  </Stack>

                  {policyDuplicateGroupsQuery.isLoading && (
                    <SectionLoadingCard
                      title="정책 중복 review 로딩 중"
                      description="최근 duplicate title/host 묶음을 불러오는 중입니다."
                    />
                  )}

                  {policyDuplicateGroupsQuery.isError && (
                    <SectionErrorCard
                      title="정책 중복 review 로드 실패"
                      description="운영 duplicate review queue를 읽지 못했습니다."
                      message={policyDuplicateGroupsErrorMessage}
                      onRetry={() => policyDuplicateGroupsQuery.refetch()}
                    />
                  )}

                  {policyDuplicateGroups && !policyDuplicateGroupsQuery.isLoading && !policyDuplicateGroupsQuery.isError && (
                    <Stack spacing={2}>
                      <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(2, 1fr)" } }}>
                        <MetricCard
                          title="열린 중복 묶음"
                          value={formatNumber(policyDuplicateGroups.openGroupCount)}
                          description="아직 운영 검토가 필요한 duplicate title/host 묶음"
                        />
                        <MetricCard
                          title="최근 24시간 신규"
                          value={formatNumber(policyDuplicateGroups.recentOpenGroupCount24h)}
                          description="지난 24시간 안에 새로 생긴 duplicate 묶음"
                        />
                        <MetricCard
                          title="열린 관련 row"
                          value={formatNumber(policyDuplicateGroups.openDuplicateRowCount)}
                          description="현재 열린 duplicate 묶음에 포함된 row 수"
                        />
                        <MetricCard
                          title="표시 묶음"
                          value={formatNumber(policyDuplicateGroups.recentGroups?.length ?? 0)}
                          description="최근 duplicate review 샘플"
                        />
                      </Box>

                      {(policyDuplicateGroups.recentGroups?.length ?? 0) === 0 ? (
                        <Alert severity="success">
                          {policyDuplicateStatusFilter === "OPEN"
                            ? "현재 열린 정책 중복 review 묶음이 없습니다."
                            : policyDuplicateStatusFilter === "REVIEWED"
                              ? "표시할 처리완료 중복 review 묶음이 없습니다."
                              : "표시할 정책 중복 review 묶음이 없습니다."}
                        </Alert>
                      ) : (
                        <CompactListCard
                          title="최근 정책 중복 묶음"
                          description="duplicate count가 큰 묶음부터 표시합니다."
                          items={policyDuplicateGroups.recentGroups ?? []}
                          renderItem={(item) => {
                            const requestKey = `duplicate-${item.sourceType}-${item.title}-${item.hostOrgKey ?? ""}`;
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
                                        {formatSourceType(item.sourceType)} · {item.hostOrgLabel || "기관명 없음"} · 최신 row {formatDateTime(item.latestCreatedAt)}
                                      </Typography>
                                    </Box>
                                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap justifyContent={{ xs: "flex-start", md: "flex-end" }}>
                                      {item.reviewClass && (
                                        <Chip
                                          label={formatPolicyDuplicateReviewClass(item.reviewClass)}
                                          size="small"
                                          sx={{
                                            bgcolor: INFO_BG,
                                            color: INFO_TEXT,
                                            border: `1px solid ${INFO_BORDER}`,
                                            fontWeight: 700,
                                            alignSelf: { xs: "flex-start", md: "center" },
                                          }}
                                        />
                                      )}
                                      <Chip
                                        label={`${formatNumber(item.duplicateCount)}건 중복`}
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
                                  </Stack>
                                  <Typography sx={{ fontSize: 13, color: INK2, overflowWrap: "anywhere", wordBreak: "break-word" }}>
                                    sourceIds {item.sourceIds}
                                  </Typography>
                                  {item.status === "REVIEWED" && (
                                    <Alert severity="success" sx={{ py: 0 }}>
                                      {`처리완료 · ${item.reviewedByUserKey || "운영자"} · ${formatDateTime(item.reviewedAt)}`}
                                      {item.reviewNote ? ` · ${item.reviewNote}` : ""}
                                    </Alert>
                                  )}
                                  <Stack direction={{ xs: "column", md: "row" }} spacing={1} alignItems={{ xs: "stretch", md: "center" }}>
                                    <TextField
                                      size="small"
                                      placeholder="운영 메모 (선택)"
                                      value={policyDuplicateReviewNotes[requestKey] ?? ""}
                                      onChange={(event) => setPolicyDuplicateReviewNotes((prev) => ({
                                        ...prev,
                                        [requestKey]: event.target.value,
                                      }))}
                                      sx={{ flex: 1 }}
                                      disabled={item.status === "REVIEWED"}
                                    />
                                    <Button
                                      size="small"
                                      variant="outlined"
                                      disabled={item.status === "REVIEWED" || reviewSubmittingKey === requestKey}
                                      onClick={() => handleReviewPolicyDuplicateGroup(item)}
                                      sx={{ textTransform: "none", borderRadius: 999, whiteSpace: "nowrap" }}
                                    >
                                      {item.status === "REVIEWED"
                                        ? "처리완료됨"
                                        : reviewSubmittingKey === requestKey ? "처리 중..." : "처리완료"}
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

          <Box id="admin-policy-link-reviews" sx={{ scrollMarginTop: 96 }}>
            <Card sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 10px 28px rgba(15,23,42,0.04)" }}>
              <CardContent sx={{ p: 2.5 }}>
                <Stack spacing={2}>
                  <Box>
                    <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT, textTransform: "uppercase", letterSpacing: "0.08em" }}>
                      링크 품질 리뷰
                    </Typography>
                    <Typography sx={{ fontSize: 20, fontWeight: 900, color: INK, mt: 0.75, letterSpacing: "-0.02em" }}>
                      정책 링크 review queue
                    </Typography>
                    <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
                      현재 노출될 수 있는 정책 중 `detail URL`과 대표 참고 링크가 모두 비어 있는 항목만 recent queue로 보여줍니다. source contract와 실제 사용자 체감을 분리해 운영자가 review할 수 있게 둔 경계입니다.
                    </Typography>
                  </Box>

                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    {ADMIN_QUEUE_STATUS_OPTIONS.map((option) => (
                      <Chip
                        key={option.value}
                        label={option.label}
                        clickable
                        color={policyLinkStatusFilter === option.value ? "primary" : "default"}
                        variant={policyLinkStatusFilter === option.value ? "filled" : "outlined"}
                        onClick={() => setPolicyLinkStatusFilter(option.value)}
                      />
                    ))}
                  </Stack>

                  {policyLinkReviewsQuery.isLoading && (
                    <SectionLoadingCard
                      title="정책 링크 review 로딩 중"
                      description="최근 열린 링크 review 후보를 불러오는 중입니다."
                    />
                  )}

                  {policyLinkReviewsQuery.isError && (
                    <SectionErrorCard
                      title="정책 링크 review 로드 실패"
                      description="현재 사용자에게 노출될 수 있는 링크 공백 정책 queue를 읽지 못했습니다."
                      message={policyLinkReviewsErrorMessage}
                      onRetry={() => policyLinkReviewsQuery.refetch()}
                    />
                  )}

                  {policyLinkReviews && !policyLinkReviewsQuery.isLoading && !policyLinkReviewsQuery.isError && (
                    <Stack spacing={2}>
                      <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(2, 1fr)" } }}>
                        <MetricCard
                          title="열린 링크 검토"
                          value={formatNumber(policyLinkReviews.openCount)}
                          description="대표 링크가 비어 있는 현재 노출 후보"
                        />
                        <MetricCard
                          title="최근 24시간 신규"
                          value={formatNumber(policyLinkReviews.recentOpenCount24h)}
                          description="지난 24시간에 새로 열린 링크 review 후보"
                        />
                        <MetricCard
                          title="표시 항목"
                          value={formatNumber(policyLinkReviews.recentReviews?.length ?? 0)}
                          description="최근 링크 review 샘플"
                        />
                      </Box>

                      {(policyLinkReviews.recentReviews?.length ?? 0) === 0 ? (
                        <Alert severity="success">
                          {policyLinkStatusFilter === "OPEN"
                            ? "현재 열린 정책 링크 review 후보가 없습니다."
                            : policyLinkStatusFilter === "REVIEWED"
                              ? "표시할 처리완료 링크 review가 없습니다."
                              : "표시할 정책 링크 review 항목이 없습니다."}
                        </Alert>
                      ) : (
                        <CompactListCard
                          title="최근 정책 링크 review"
                          description="링크 공백이 있는 최신 정책부터 표시합니다."
                          items={policyLinkReviews.recentReviews ?? []}
                          renderItem={(item) => (
                            <Box
                              key={item.serviceId}
                              sx={{ p: 1.75, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}
                            >
                              <Stack spacing={1}>
                                <Stack direction={{ xs: "column", md: "row" }} justifyContent="space-between" spacing={1.5}>
                                  <Box sx={{ minWidth: 0 }}>
                                    <Typography sx={{ fontSize: 14, fontWeight: 800, color: INK, overflowWrap: "anywhere", wordBreak: "break-word" }}>
                                      {item.policyTitle}
                                    </Typography>
                                    <Typography sx={{ fontSize: 12, color: INK3, mt: 0.25, overflowWrap: "anywhere", wordBreak: "break-word" }}>
                                      {formatSourceType(item.sourceType)} · {item.sourceId || "sourceId 없음"} · {formatDateTime(item.createdAt)}
                                    </Typography>
                                  </Box>
                                  <Chip
                                    label="대표 링크 비어있음"
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
                                  {[
                                    item.hostOrgLabel || item.operatingOrgLabel || "기관명 없음",
                                    item.categoryMain || item.categorySub
                                      ? [item.categoryMain, item.categorySub].filter(Boolean).join(" / ")
                                      : "카테고리 없음",
                                  ].join(" · ")}
                                </Typography>
                                <Typography sx={{ fontSize: 12, color: INK3 }}>
                                  신청 종료 {formatDate(item.applyEndDate)} · 사업 종료 {formatDate(item.endDate)}
                                </Typography>
                                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                  <Chip
                                    label={formatPolicyLinkReviewBucket(item.reviewBucket)}
                                    size="small"
                                    variant="outlined"
                                  />
                                </Stack>
                                {item.status === "REVIEWED" && (
                                  <Alert severity="success" sx={{ py: 0 }}>
                                    {`처리완료 · ${item.reviewedByUserKey || "운영자"} · ${formatDateTime(item.reviewedAt)}`}
                                    {item.reviewNote ? ` · ${item.reviewNote}` : ""}
                                  </Alert>
                                )}
                                <Stack direction={{ xs: "column", md: "row" }} spacing={1} alignItems={{ xs: "stretch", md: "center" }}>
                                  <TextField
                                    size="small"
                                    placeholder="운영 메모 (선택)"
                                    value={policyLinkReviewNotes[item.serviceId] ?? ""}
                                    onChange={(event) => setPolicyLinkReviewNotes((prev) => ({
                                      ...prev,
                                      [item.serviceId]: event.target.value,
                                    }))}
                                    sx={{ flex: 1 }}
                                    disabled={item.status === "REVIEWED"}
                                  />
                                  <Button
                                    size="small"
                                    variant="outlined"
                                    disabled={item.status === "REVIEWED" || reviewSubmittingKey === `policy-link-${item.serviceId}`}
                                    onClick={() => handleReviewPolicyLink(item.serviceId)}
                                    sx={{ textTransform: "none", borderRadius: 999, whiteSpace: "nowrap" }}
                                  >
                                    {item.status === "REVIEWED"
                                      ? "처리완료됨"
                                      : reviewSubmittingKey === `policy-link-${item.serviceId}` ? "처리 중..." : "처리완료"}
                                  </Button>
                                </Stack>
                              </Stack>
                            </Box>
                          )}
                        />
                      )}
                    </Stack>
                  )}
                </Stack>
              </CardContent>
            </Card>
          </Box>

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
                            <Chip label={`precheck ${standardCodeEffectObservation.precheckStatus || "—"}`} size="small" variant="outlined" />
                            <Chip label={`decision ${standardCodeEffectObservation.decisionClass || "—"}`} size="small" variant="outlined" />
                            <Chip label={`matrix status ${standardCodeEffectObservation.welfareMatrixStatus || "—"}`} size="small" variant="outlined" />
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

          <Box id="admin-wrapper-observation" sx={{ scrollMarginTop: 96 }}>
            <Card sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 10px 28px rgba(15,23,42,0.04)" }}>
              <CardContent sx={{ p: 2.5 }}>
                <Stack spacing={2}>
                  <Box>
                    <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT, textTransform: "uppercase", letterSpacing: "0.08em" }}>
                      상위 요약
                    </Typography>
                    <Typography sx={{ fontSize: 20, fontWeight: 900, color: INK, mt: 0.75, letterSpacing: "-0.02em" }}>
                      active baseline / current priority
                    </Typography>
                    <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
                      운영 상위 wrapper latest summary 기준으로, 표준코드 coverage와 recommendation observation 핵심값이 최상단 baseline/current priority handoff에 올라왔는지 확인합니다.
                    </Typography>
                  </Box>

                  {wrapperObservationQuery.isLoading && (
                    <SectionLoadingCard
                      title="상위 wrapper 관측 로딩 중"
                      description="active baseline / current priority latest summary를 읽는 중입니다."
                    />
                  )}

                  {wrapperObservationQuery.isError && (
                    <SectionErrorCard
                      title="상위 wrapper 관측 로드 실패"
                      description="latest active baseline/current priority summary를 읽지 못했습니다."
                      message={wrapperObservationErrorMessage}
                      onRetry={() => wrapperObservationQuery.refetch()}
                    />
                  )}

                  {wrapperObservation && !wrapperObservationQuery.isLoading && !wrapperObservationQuery.isError && (
                    <Stack spacing={2}>
                      <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", xl: "1fr 1fr" } }}>
                        <SectionStateCard
                          title="active baseline"
                          description={wrapperObservation.activeBaselineSummaryPath}
                        >
                          {!wrapperObservation.activeBaselineAvailable ? (
                            <Alert severity="warning">latest active baseline summary가 아직 없습니다.</Alert>
                          ) : (
                            <Stack spacing={2}>
                              <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", sm: "repeat(2, 1fr)" } }}>
                                <MetricCard title="suite" value={wrapperObservation.activeBaselineStatus || "—"} description={formatDateTime(wrapperObservation.activeBaselineGeneratedAt)} />
                                <MetricCard title="ops observation" value={wrapperObservation.activeBaselineOpsObservationStatus || "—"} description="상위 wrapper 내 ops observation 상태" />
                                <MetricCard title="attention feed" value={wrapperObservation.activeBaselineAttentionFeedStatus || "—"} description={`${formatNumber(wrapperObservation.activeBaselineAttentionFeedItemCount)}개 항목`} />
                                <MetricCard title="1개 이상 입력" value={formatNumber(wrapperObservation.activeBaselineUsersWithAnyStandardCode)} description="ops summary 승격값" />
                                <MetricCard title="전부 미입력" value={formatNumber(wrapperObservation.activeBaselineUsersMissingAllStandardCodes)} description="ops summary 승격값" />
                              </Box>
                              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                <Chip label={`attention ${wrapperObservation.activeBaselineAttentionFeedItemTitles || "—"}`} size="small" variant="outlined" />
                                <Chip label={`주거 rule 상승 ${formatNumber(wrapperObservation.activeBaselineRecommendationHousingPositiveRuleDeltaRows)}`} size="small" variant="outlined" />
                                <Chip label={`복지 positive scenarios ${formatNumber(wrapperObservation.activeBaselineRecommendationWelfarePositiveRuleScenarios)}`} size="small" variant="outlined" />
                                <Chip label={`복지 max delta ${wrapperObservation.activeBaselineRecommendationWelfareMaxRuleDelta}`} size="small" variant="outlined" />
                              </Stack>
                            </Stack>
                          )}
                        </SectionStateCard>

                        <SectionStateCard
                          title="current priority"
                          description={wrapperObservation.currentPrioritySummaryPath}
                        >
                          {!wrapperObservation.currentPriorityAvailable ? (
                            <Alert severity="warning">latest current priority summary가 아직 없습니다.</Alert>
                          ) : (
                            <Stack spacing={2}>
                              <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", sm: "repeat(2, 1fr)" } }}>
                                <MetricCard title="suite" value={wrapperObservation.currentPriorityStatus || "—"} description={formatDateTime(wrapperObservation.currentPriorityGeneratedAt)} />
                                <MetricCard title="active baseline reused" value={wrapperObservation.currentPriorityActiveBaselineReused ? "true" : "false"} description="same-config latest 재사용 여부" />
                                <MetricCard title="attention feed" value={wrapperObservation.currentPriorityAttentionFeedStatus || "—"} description={`${formatNumber(wrapperObservation.currentPriorityAttentionFeedItemCount)}개 항목`} />
                                <MetricCard title="1개 이상 입력" value={formatNumber(wrapperObservation.currentPriorityUsersWithAnyStandardCode)} description="current priority 승격값" />
                                <MetricCard
                                  title="전부 미입력"
                                  value={formatNumber(wrapperObservation.currentPriorityUsersMissingAllStandardCodes)}
                                  description="current priority 승격값"
                                  focusTarget
                                  cardProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminCardKey, ADMIN_DASHBOARD_CARD_KEYS.currentPriorityMissingStandardCodes)}
                                />
                              </Box>
                              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                <Chip label={`attention ${wrapperObservation.currentPriorityAttentionFeedItemTitles || "—"}`} size="small" variant="outlined" />
                                <Chip label={`recommendation status ${wrapperObservation.currentPriorityRecommendationObservationStatus || "—"}`} size="small" variant="outlined" />
                                <Chip label={`주거 rule 상승 ${formatNumber(wrapperObservation.currentPriorityRecommendationHousingPositiveRuleDeltaRows)}`} size="small" variant="outlined" />
                                <Chip label={`복지 scenarios ${formatNumber(wrapperObservation.currentPriorityRecommendationWelfareScenarioCount)}`} size="small" variant="outlined" />
                                <Chip label={`복지 positive ${formatNumber(wrapperObservation.currentPriorityRecommendationWelfarePositiveRuleScenarios)}`} size="small" variant="outlined" />
                                <Chip label={`복지 max delta ${wrapperObservation.currentPriorityRecommendationWelfareMaxRuleDelta}`} size="small" variant="outlined" />
                              </Stack>
                            </Stack>
                          )}
                        </SectionStateCard>
                      </Box>
                    </Stack>
                  )}
                </Stack>
              </CardContent>
            </Card>
          </Box>

          <Box id="admin-reference-codebooks" sx={{ scrollMarginTop: 96 }}>
            <Card sx={{ background: PANEL_BG, border: `1px solid ${PANEL_LINE}`, boxShadow: "0 10px 28px rgba(15,23,42,0.04)" }}>
              <CardContent sx={{ p: 2.5 }}>
                <Stack spacing={2}>
                  <Box>
                    <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT, textTransform: "uppercase", letterSpacing: "0.08em" }}>
                      기준 정보
                    </Typography>
                    <Typography sx={{ fontSize: 20, fontWeight: 900, color: INK, mt: 0.75, letterSpacing: "-0.02em" }}>
                      공식 코드북 탐색
                    </Typography>
                    <Typography sx={{ fontSize: 13, color: INK3, mt: 0.75 }}>
                      사용자 제공 표준 코드북을 운영 화면에서 바로 조회합니다. 작은 코드표는 검색 결과를, 큰 자료는 메타데이터와 샘플 행을 보여줍니다.
                    </Typography>
                  </Box>

                  {officialCodebooksQuery.isLoading && (
                    <SectionLoadingCard
                      title="공식 코드북 로딩 중"
                      description="코드셋 목록을 불러오는 중입니다."
                    />
                  )}

                  {officialCodebooksQuery.isError && (
                    <SectionErrorCard
                      title="공식 코드북 목록 로드 실패"
                      description="기준정보 API를 불러오지 못했습니다."
                      message={officialCodebooksErrorMessage}
                      onRetry={() => officialCodebooksQuery.refetch()}
                    />
                  )}

                  {!officialCodebooksQuery.isLoading && !officialCodebooksQuery.isError && (
                    <>
                      <Stack direction={{ xs: "column", lg: "row" }} spacing={2}>
                        <Stack spacing={0.75} sx={{ minWidth: { xs: "100%", lg: 320 } }}>
                          <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK2 }}>코드셋 선택</Typography>
                          <Select
                            size="small"
                            value={effectiveSelectedCodeSetKey}
                            onChange={(event) => setSelectedCodeSetKey(event.target.value)}
                            sx={{ bgcolor: PANEL_BG }}
                          >
                            {officialCodebooks.map((item) => (
                              <MenuItem key={item.codeSetKey} value={item.codeSetKey}>
                                {item.codeSetKey}
                              </MenuItem>
                            ))}
                          </Select>
                        </Stack>
                        <Stack spacing={0.75} sx={{ flex: 1 }}>
                          <Typography sx={{ fontSize: 13, fontWeight: 700, color: INK2 }}>검색</Typography>
                          <TextField
                            size="small"
                            placeholder="코드값, 라벨, 설명 검색"
                            value={codebookQueryText}
                            onChange={(event) => setCodebookQueryText(event.target.value)}
                          />
                        </Stack>
                      </Stack>

                      {selectedCodebookSummary && (
                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                          <Chip label={selectedCodebookSummary.sourceType} size="small" />
                          <Chip label={`${selectedCodebookSummary.rowCount.toLocaleString()} rows`} size="small" variant="outlined" />
                          <Chip label={selectedCodebookSummary.rowDataIncluded ? "행 조회 가능" : "메타데이터 전용"} size="small" variant="outlined" />
                          <Chip label={selectedCodebookSummary.sourceFile} size="small" variant="outlined" />
                        </Stack>
                      )}

                      {officialCodebookDetailQuery.isLoading && (
                        <SectionLoadingCard
                          title="코드북 상세 로딩 중"
                          description="선택한 코드셋의 샘플 행을 불러오는 중입니다."
                        />
                      )}

                      {officialCodebookDetailQuery.isError && (
                        <SectionErrorCard
                          title="코드북 상세 로드 실패"
                          description="선택한 코드셋의 내용을 불러오지 못했습니다."
                          message={officialCodebookDetailErrorMessage}
                          onRetry={() => officialCodebookDetailQuery.refetch()}
                        />
                      )}

                      {officialCodebookDetail && !officialCodebookDetailQuery.isLoading && !officialCodebookDetailQuery.isError && (
                        <Stack spacing={2}>
                          <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(3, 1fr)" } }}>
                            <MetricCard
                              title="전체 행 수"
                              value={formatNumber(officialCodebookDetail.rowCount)}
                              description={officialCodebookDetail.sheetName ?? "원본 시트 정보 없음"}
                            />
                            <MetricCard
                              title="표시 행 수"
                              value={formatNumber(officialCodebookDetail.matchedRowCount)}
                              description={codebookQueryText.trim() ? "검색 조건 반영" : "기본 조회"}
                              focusTarget
                              cardProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminCardKey, ADMIN_DASHBOARD_CARD_KEYS.matchedRowCount)}
                            />
                            <MetricCard
                              title="용도"
                              value={officialCodebookDetail.intendedUse ?? "—"}
                              description={officialCodebookDetail.sourceFile}
                            />
                          </Box>

                          {officialCodebookDetail.metadata && (
                            <Stack spacing={1}>
                              <Typography sx={{ fontSize: 14, fontWeight: 800, color: INK }}>메타데이터</Typography>
                              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                {Object.entries(officialCodebookDetail.metadata)
                                  .filter(([key]) => key !== "sampleRows")
                                  .map(([key, value]) => (
                                    <Chip
                                      key={key}
                                      label={`${key}: ${Array.isArray(value) ? value.join(", ") : String(value)}`}
                                      size="small"
                                      variant="outlined"
                                    />
                                  ))}
                              </Stack>
                            </Stack>
                          )}

                          {detailRows.length > 0 ? (
                            <TableContainer sx={{ border: `1px solid ${PANEL_LINE}`, borderRadius: 3, overflow: "hidden" }}>
                              <Table size="small">
                                <TableHead sx={{ bgcolor: "#f8fafc" }}>
                                  <TableRow>
                                    {(officialCodebookDetail.headers ?? Object.keys(detailRows[0] ?? {})).map((header) => (
                                      <TableCell key={header} sx={{ fontWeight: 800, color: INK2 }}>{header}</TableCell>
                                    ))}
                                  </TableRow>
                                </TableHead>
                                <TableBody>
                                  {detailRows.map((row, index) => (
                                    <TableRow key={`${effectiveSelectedCodeSetKey}-${index}`}>
                                      {(officialCodebookDetail.headers ?? Object.keys(row ?? {})).map((header) => (
                                        <TableCell key={header} sx={{ color: INK, verticalAlign: "top" }}>
                                          {row?.[header] ?? "—"}
                                        </TableCell>
                                      ))}
                                    </TableRow>
                                  ))}
                                </TableBody>
                              </Table>
                            </TableContainer>
                          ) : (
                            <Alert severity="info">표시할 행이 없습니다.</Alert>
                          )}
                        </Stack>
                      )}
                    </>
                  )}
                </Stack>
              </CardContent>
            </Card>
          </Box>

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
                    focusTarget
                    cardProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminCardKey, ADMIN_DASHBOARD_CARD_KEYS.top1LeaderSignal)}
                  />
                </Box>

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

                <Box id="admin-policy-triage-summary" sx={{ display: "grid", gap: 2, mt: 2, gridTemplateColumns: { xs: "1fr", md: "repeat(4, 1fr)" }, scrollMarginTop: 96 }}>
                  <MetricCard
                    title="policy triage"
                    value={formatStatusLabel(summaryData.policyTriage?.decisionClass)}
                    description={summaryData.policyTriage?.operatorReading || "중복/link backlog 우선순위를 계산하지 못했습니다."}
                    chip={
                      <Typography sx={{ fontSize: 12, fontWeight: 800, color: ACCENT, maxWidth: 180, textAlign: "right" }}>
                        {summaryData.policyTriage?.nextAction || "nightly observation 확인"}
                      </Typography>
                    }
                  />
                  <MetricCard
                    title="exact duplicate"
                    value={formatNumber(summaryData.policyTriage?.exactDuplicateGroups)}
                    description={`mirror ${formatNumber(summaryData.policyTriage?.mirrorVariantGroups)} · 열린 중복 ${formatNumber(summaryData.policyTriage?.openDuplicateGroups)}`}
                  />
                  <MetricCard
                    title="급부형 링크 review"
                    value={formatNumber(summaryData.policyTriage?.benefitSupportLinkReviews)}
                    description={`모집형 ${formatNumber(summaryData.policyTriage?.announcementRecruitmentLinkReviews)} · 열린 링크 ${formatNumber(summaryData.policyTriage?.openLinkReviews)}`}
                  />
                  <MetricCard
                    title="현재 처리 순서"
                    value={summaryData.policyTriage?.decisionClass === "DUPLICATE_THEN_LINK_PRIORITY" ? "exact -> mirror -> link" : summaryData.policyTriage?.decisionClass === "LINK_REVIEW_PRIORITY" ? "benefit -> announcement" : "tail review"}
                    description={summaryData.policyTriage?.nextAction || "운영 backlog 우선순위 정보 없음"}
                  />
                </Box>

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
                                onClick={() => setNotificationStaleDays(days)}
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
                                              {formatStatusLabel(item.kind)} · {item.deeplinkUrl || "deeplink 없음"} · 가장 오래된 row {formatDateTime(item.oldestCreatedAt)}
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
                                            onClick={() => handleHideNotificationStaleTarget(item)}
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
                  <ServiceListCard
                    title="반복 노출 상위 서비스"
                    items={breakdowns.topRepeatedServices}
                    countLabel="rowCount"
                    focusTarget
                    cardProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminListKey, ADMIN_DASHBOARD_LIST_KEYS.topRepeatedServices)}
                  />
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
                actionLabel="부분 성공 보기"
                actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminActionKey, ADMIN_DASHBOARD_ACTION_KEYS.collectPartialView)}
                onAction={() => jumpToSection("admin-collect-triage", { tone: "info", focusKey: ADMIN_DASHBOARD_FOCUS_KEYS.collectPartial, preset: "metricAction" })}
              />
              <MetricCard
                title="열린 회로"
                value={formatNumber(collectFailures.circuitStatuses?.filter((item) => item.open).length)}
                description={`추적 중 ${formatNumber(collectFailures.circuitStatuses?.length)}`}
                actionLabel="회로 상태 보기"
                actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminActionKey, ADMIN_DASHBOARD_ACTION_KEYS.collectCircuitView)}
                onAction={() => jumpToSection("admin-collect-triage", { tone: "warning", focusKey: ADMIN_DASHBOARD_FOCUS_KEYS.collectCircuit, preset: "metricAction" })}
              />
              <MetricCard
                title="최근 실패 샘플"
                value={formatNumber(collectFailures.recentSamples?.length)}
                description="상세 샘플 미리보기"
                actionLabel="실패 샘플 보기"
                actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminActionKey, ADMIN_DASHBOARD_ACTION_KEYS.collectFailureSampleView)}
                onAction={() => jumpToSection("admin-collect-triage", { tone: "warning", focusKey: ADMIN_DASHBOARD_FOCUS_KEYS.default, preset: "metricAction" })}
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
                  cardProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminListKey, ADMIN_DASHBOARD_LIST_KEYS.collectJobBreakdowns)}
                  items={collectFailures.jobBreakdowns}
                  renderItem={(item, index) => (
                    <Box
                      key={`${item.jobName}-${item.latestStartedAt}`}
                      {...(index === 0 ? buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTarget, ADMIN_DASHBOARD_FOCUS_KEYS.collectPartial) : {})}
                      {...(index === 0 ? buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTone, "info") : {})}
                      sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}
                    >
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
                  cardProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminListKey, ADMIN_DASHBOARD_LIST_KEYS.collectCircuitStatuses)}
                  items={collectFailures.circuitStatuses}
                  renderItem={(item) => (
                    <Box
                      key={item.circuitKey}
                      {...(item.circuitKey === firstCollectCircuitKey ? buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTarget, ADMIN_DASHBOARD_FOCUS_KEYS.collectCircuit) : {})}
                      {...(item.circuitKey === firstCollectCircuitKey ? buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTone, item.open ? "warning" : "success") : {})}
                      sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: item.open ? "#fff7ed" : "#f8fafc" }}
                    >
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
                  cardProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminListKey, ADMIN_DASHBOARD_LIST_KEYS.collectRecentFailureSamples)}
                  items={collectFailures.recentSamples}
                  renderItem={(item, index) => (
                    <Box
                      key={`${item.jobName}-${item.startedAt}-${item.errorCode}`}
                      {...(index === 0 ? buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTarget, ADMIN_DASHBOARD_FOCUS_KEYS.default) : {})}
                      {...(index === 0 ? buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTone, "warning") : {})}
                      sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}
                    >
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
          </Box>

          <Box id="admin-search-triage" sx={{ scrollMarginTop: 96 }}>
            <TriageSectionTitle
              eyebrow="검색 진단"
              title="검색 실패 상세"
              description="0건 검색 패턴, 재시도 묶음, recovery 여부를 같은 페이지에서 바로 확인합니다."
            />

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
                actionLabel="실패 샘플 보기"
                actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminActionKey, ADMIN_DASHBOARD_ACTION_KEYS.searchWarningView)}
                onAction={() => jumpToSection("admin-search-triage", { tone: "warning", focusKey: ADMIN_DASHBOARD_FOCUS_KEYS.searchWarning, preset: "metricAction" })}
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
                actionLabel="복구 묶음 보기"
                actionProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminActionKey, ADMIN_DASHBOARD_ACTION_KEYS.searchRecoveredView)}
                onAction={() => jumpToSection("admin-search-triage", { tone: "success", focusKey: ADMIN_DASHBOARD_FOCUS_KEYS.searchRecovered, preset: "metricAction" })}
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
                  cardProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminListKey, ADMIN_DASHBOARD_LIST_KEYS.searchRecoveredGroups)}
                  items={searchFailures.recoveredSearchGroups}
                  renderItem={(item, index) => (
                    <Box
                      key={`${item.actorType}-${item.actorKey}-${item.keyword}-${item.latestRecoveredAt}`}
                      {...(index === 0 ? buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTarget, ADMIN_DASHBOARD_FOCUS_KEYS.searchRecovered) : {})}
                      {...(index === 0 ? buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTone, "success") : {})}
                      sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}
                    >
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
                  cardProps={buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.adminListKey, ADMIN_DASHBOARD_LIST_KEYS.searchRecentZeroResultSamples)}
                  items={searchFailures.recentSamples}
                  renderItem={(item, index) => (
                    <Box
                      key={`${item.keyword}-${item.searchedAt}-${item.sido}-${item.sgg}`}
                      {...(index === 0 ? buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTarget, ADMIN_DASHBOARD_FOCUS_KEYS.searchWarning) : {})}
                      {...(index === 0 ? buildDashboardDataAttr(ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTone, "warning") : {})}
                      sx={{ p: 1.5, borderRadius: 2, border: `1px solid ${PANEL_LINE}`, bgcolor: "#fafbff" }}
                    >
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
          </Box>
        </Stack>
      </Box>
    </div>
  );
}
