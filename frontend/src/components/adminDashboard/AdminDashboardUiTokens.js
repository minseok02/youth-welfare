import { ADMIN_DASHBOARD_TEST_ATTRS } from "../../lib/adminDashboardTestHooks";

export const PAGE_BG = "#f7f8fc";
export const PANEL_BG = "#ffffff";
export const PANEL_LINE = "#e5e7eb";
export const INK = "#11131a";
export const INK2 = "#4a4f5c";
export const INK3 = "#6b7280";
export const ACCENT = "#2563eb";
export const WARNING_BG = "#fff7ed";
export const WARNING_BORDER = "#fdba74";
export const WARNING_TEXT = "#9a3412";
export const SUCCESS_BG = "#ecfdf5";
export const SUCCESS_BORDER = "#86efac";
export const SUCCESS_TEXT = "#166534";
export const INFO_BG = "#eff6ff";
export const INFO_BORDER = "#93c5fd";
export const INFO_TEXT = "#1d4ed8";

export const NOTIFICATION_STALE_DAY_OPTIONS = [7, 14];

export const REVIEW_GATE_TONE = {
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

export const COLLECT_EXECUTION_TONE = {
  SCHEDULED: { label: "야간 배치", bg: INFO_BG, border: INFO_BORDER, color: INFO_TEXT },
  MANUAL: { label: "수동 실행", bg: WARNING_BG, border: WARNING_BORDER, color: WARNING_TEXT },
};

export const COLLECT_LANE_TONE = {
  SNAPSHOT: { label: "스냅샷", bg: "#f8fafc", border: PANEL_LINE, color: INK2 },
  DETAIL: { label: "상세 수집", bg: "#f8fafc", border: PANEL_LINE, color: INK2 },
  ENRICHMENT: { label: "보강 처리", bg: "#f8fafc", border: PANEL_LINE, color: INK2 },
  MAINTENANCE: { label: "유지보수", bg: "#f8fafc", border: PANEL_LINE, color: INK2 },
};

export const COLLECT_RESOURCE_TONE = {
  HEAVY: { label: "고부하", bg: "#fee2e2", border: "#fca5a5", color: "#b91c1c" },
  STANDARD: { label: "표준", bg: SUCCESS_BG, border: SUCCESS_BORDER, color: SUCCESS_TEXT },
  BUDGETED: { label: "예산 제한", bg: WARNING_BG, border: WARNING_BORDER, color: WARNING_TEXT },
  ON_DEMAND: { label: "온디맨드", bg: INFO_BG, border: INFO_BORDER, color: INFO_TEXT },
};

export const WRAPPER_STATUS_TONE = {
  passed: { label: "정상", bg: SUCCESS_BG, border: SUCCESS_BORDER, color: SUCCESS_TEXT },
  ok: { label: "정상", bg: SUCCESS_BG, border: SUCCESS_BORDER, color: SUCCESS_TEXT },
  skipped: { label: "건너뜀", bg: WARNING_BG, border: WARNING_BORDER, color: WARNING_TEXT },
  failed: { label: "실패", bg: "#fee2e2", border: "#fca5a5", color: "#b91c1c" },
  missing: { label: "없음", bg: "#f8fafc", border: PANEL_LINE, color: INK2 },
};

export const ATTENTION_SEVERITY_TONE = {
  warning: { label: "주의", bg: WARNING_BG, border: WARNING_BORDER, color: WARNING_TEXT },
  info: { label: "확인", bg: INFO_BG, border: INFO_BORDER, color: INFO_TEXT },
  success: { label: "정상", bg: SUCCESS_BG, border: SUCCESS_BORDER, color: SUCCESS_TEXT },
};

const ATTENTION_SOURCE_LABELS = {
  collect: "collect",
  "user-profile-standard-codes": "standard-codes",
  "wrapper-observation": "wrapper",
  "policy-duplicate-groups": "duplicates",
  "policy-error-reports": "policy-error",
  "policy-link-reviews": "links",
  "support-inquiries": "support",
  "policy-duplicate-backlog": "duplicates",
  "policy-error-report-backlog": "policy-error",
  "policy-link-review-backlog": "links",
  "support-inquiry-backlog": "support",
  "notification-backlog": "notification",
  "notification-stale-backlog": "notification",
};

export const ADMIN_QUEUE_STATUS_OPTIONS = [
  { value: "OPEN", label: "열린 건" },
  { value: "REVIEWED", label: "처리완료" },
  { value: "ALL", label: "전체" },
];

export function formatAttentionSource(sourceOrKey) {
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

export function formatAttentionActionLabel(source) {
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
    case "policy-error":
      return "오류 제보 보기";
    case "links":
      return "링크 review 보기";
    case "support":
      return "서비스 문의 보기";
    default:
      return "관련 섹션 보기";
  }
}

export const SECTION_FLASH_TONE = {
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

export const ATTENTION_PROMOTION_RANK = {
  warning: 0,
  info: 1,
  success: 2,
};

export const ATTENTION_PROMOTION_KEY_RANK = {
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

export const ACTIVE_CARD_SX = {
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
