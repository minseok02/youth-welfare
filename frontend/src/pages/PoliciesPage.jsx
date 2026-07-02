import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate, useSearchParams } from "react-router-dom";
import {
  Snackbar, Alert, Pagination, CircularProgress,
} from "@mui/material";
import BookmarkBorderIcon from "@mui/icons-material/BookmarkBorder";
import BookmarkIcon from "@mui/icons-material/Bookmark";
import SearchIcon from "@mui/icons-material/Search";
import ViewListIcon from "@mui/icons-material/ViewList";
import GridViewIcon from "@mui/icons-material/GridView";
import Header from "../components/Header";
import FloatingNav from "../components/FloatingNav";
import IncomeCalculatorModal from "../components/IncomeCalculatorModal";
import api from "../lib/axios";
import { useAuthStore } from "../store/authStore";
import {
  GOV24_BENEFIT_TYPES,
  GOV24_SERVICE_FIELDS,
  GOV24_SOURCE_LABEL,
  GOV24_USER_TYPES,
  POLICY_CATEGORIES,
  POLICY_INCOME_ROWS,
  POLICY_SORT_MAP,
  POLICY_SOURCE_OPTIONS,
  POLICY_SOURCE_TYPE_MAP,
  POLICY_STATUS_FILTER_MAP,
  POLICY_TARGET_GROUPS,
} from "../lib/policyFilterOptions";
import {
  formatPolicyDday,
  formatPolicyListStatusLabel,
  splitGov24MultiLabel,
  uniqueNonBlank,
} from "../lib/policyDisplay";
import { resolveStandardProfileCodeCompletion } from "../lib/profileStandardCodes";
import {
  buildSafeReturnLocation,
  resolveSafeRouteTarget,
  sanitizePostLoginAction,
  sanitizeTransientRouteState,
} from "../lib/safeNavigation";
import {
  FILTER_REGIONS,
  formatRegionSelectionLabel,
  getDistrictOptions,
  getWardOptions,
  resolveRegionSelection,
  resolveSubmittedSgg,
} from "../lib/regionOptions";

// ── 상수 ──────────────────────────────────────────────────────────────────────

const SOURCE_LABEL_BY_TYPE = Object.fromEntries(
  Object.entries(POLICY_SOURCE_TYPE_MAP).map(([label, type]) => [type, label])
);
const GOV24_BADGE_LIMIT = 4;
const MAX_POLICY_SEARCH_STATE_KEYWORD_LENGTH = 100;
const POLICY_SOURCE_NOTICE = "정책 정보는 온통청년·복지로·정부24와 각 운영기관 공고를 기준으로 수집한 내용입니다. 신청 전 상세 페이지의 원문 안내를 확인하세요.";
const STATUS_FILTER_LABEL_BY_API = Object.fromEntries(
  Object.entries(POLICY_STATUS_FILTER_MAP).map(([label, apiValue]) => [apiValue, label])
);

const FALLBACK_TRENDING = ["월세 지원", "국민취업제도", "도약계좌", "창업캠프", "자격증 응시료", "대학생 생활안정"];
const TRENDING_LIMIT = 6;
const SUGGESTION_LIMIT = 8;

// ── 디자인 상수 ───────────────────────────────────────────────────────────────
const A = "#2563eb";
const A7 = "#1d4ed8";
const AS = "#e8efff";
const AI = "#1e3a8a";
const INK = "#11131a";
const INK2 = "#4a4f5c";
const INK3 = "#6b7280";
const LINE = "#e7e9ef";
const LINE2 = "#f0f2f7";
const WARN = "#ef4444";
const OK = "#047857";
const OK_BG = "#ecfdf5";

// ── 헬퍼 ──────────────────────────────────────────────────────────────────────

const mapPolicySummary = (policy) => ({
  id: policy.id,
  title: policy.title,
  category: policy.unifiedCategory || "기타",
  dday: formatPolicyDday(policy.applyEndDate, policy.status) || formatPolicyListStatusLabel(policy.status),
  sourceType: policy.sourceType || "",
  sourceTypeLabel: SOURCE_LABEL_BY_TYPE[policy.sourceType] || "",
  source: policy.hostOrg || policy.sido || policy.applyMethodName || formatPolicyListStatusLabel(policy.status),
  orgName: policy.hostOrg || policy.operatingOrg || "",
  regionText: policy.sido || "",
  summary: policy.description || "정책 설명 정보가 없습니다.",
  bookmarked: Boolean(policy.bookmarked),
  apiViewCount: policy.apiViewCount ?? 0,
  viewCount: policy.viewCount ?? 0,
  createdAt: policy.createdAt ?? null,
  registeredAt: policy.registeredAt ?? null,
  lastModifiedAt: policy.lastModifiedAt ?? null,
  youthOfficialBadges: buildYouthOfficialBadges(policy),
  gov24Badges: buildGov24Badges(policy),
});

const YOUTH_OFFICIAL_SUPPRESSED = new Set(["제한없음", "무관"]);

const normalizeGov24OptionParam = (value, options) => {
  if (!value) return "전체";
  const trimmed = value.trim();
  return options.includes(trimmed) ? trimmed : "전체";
};

const resolveFilterRegionSelection = (region, rawSubRegion, rawWard) => {
  if (rawWard && rawWard !== "전체") {
    return {
      subRegion: rawSubRegion || "전체",
      ward: rawWard,
    }
  }

  const { subRegion, ward } = resolveRegionSelection(region, rawSubRegion);
  return {
    subRegion: subRegion || "전체",
    ward: ward || "전체",
  }
}

const buildGov24Badges = (policy) => {
  if (policy?.sourceType !== "GOV24") return [];

  const badges = [
    policy?.gov24ServiceFieldLabel && `분야 ${policy.gov24ServiceFieldLabel}`,
    ...splitGov24MultiLabel(policy?.gov24UserTypeLabel).map((label) => `대상 ${label}`),
    ...splitGov24MultiLabel(policy?.gov24BenefitTypeLabel).map((label) => `유형 ${label}`),
  ].filter(Boolean);

  return [...new Set(badges)].slice(0, GOV24_BADGE_LIMIT);
};

function PolicyMetaBadges({ badges }) {
  if (!badges?.length) return null;
  return (
    <div style={{ display: "flex", gap: 6, marginTop: 10, flexWrap: "wrap" }}>
      {badges.map((badge) => (
        <span
          key={badge}
          style={{
            display: "inline-flex",
            alignItems: "center",
            padding: "3px 9px",
            borderRadius: 99,
            fontSize: 11,
            fontWeight: 700,
            color: AI,
            background: "white",
            border: `1px solid ${LINE}`,
          }}
        >
          {badge}
        </span>
      ))}
    </div>
  );
}

const buildYouthOfficialBadges = (policy) => {
  if (policy?.sourceType !== "YOUTH") return [];

  const badges = [
    policy?.youthIncomeConditionTypeLabel && !YOUTH_OFFICIAL_SUPPRESSED.has(policy.youthIncomeConditionTypeLabel)
      ? `소득 ${policy.youthIncomeConditionTypeLabel}`
      : null,
    ...uniqueNonBlank(policy?.youthEmploymentRequirementLabels)
      .filter((label) => !YOUTH_OFFICIAL_SUPPRESSED.has(label)),
    ...uniqueNonBlank(policy?.youthEducationRequirementLabels)
      .filter((label) => !YOUTH_OFFICIAL_SUPPRESSED.has(label)),
    ...uniqueNonBlank(policy?.youthSpecialRequirementLabels)
      .filter((label) => !YOUTH_OFFICIAL_SUPPRESSED.has(label)),
    policy?.youthMaritalStatusLabel && !YOUTH_OFFICIAL_SUPPRESSED.has(policy.youthMaritalStatusLabel)
      ? `결혼 ${policy.youthMaritalStatusLabel}`
      : null,
  ].filter(Boolean);

  return [...new Set(badges)].slice(0, 3);
};

const resolveInitialSort = (paramsSearch, paramsSort) => {
  const hasSearch = Boolean(paramsSearch?.trim());
  const allowed = hasSearch ? ["relevance", "views", "latest", "deadline"] : ["views", "latest", "deadline"];
  if (allowed.includes(paramsSort)) return paramsSort;
  return hasSearch ? "relevance" : "latest";
};

const normalizeSourceTypeParam = (rawSourceType) => {
  if (!rawSourceType?.trim()) return "전체";
  const trimmed = rawSourceType.trim();
  if (POLICY_SOURCE_TYPE_MAP[trimmed]) return trimmed;
  const normalized = trimmed.toUpperCase();
  return SOURCE_LABEL_BY_TYPE[normalized] || "전체";
};

const serializeSourceTypeParam = (sourceLabel) => {
  if (!sourceLabel || sourceLabel === "전체") return null;
  return POLICY_SOURCE_TYPE_MAP[sourceLabel] ?? null;
};

const normalizeStatusFilterParam = (rawStatusFilter, fallback) => {
  if (!rawStatusFilter?.trim()) return fallback;
  const trimmed = rawStatusFilter.trim();
  return POLICY_STATUS_FILTER_MAP[trimmed] ? trimmed : (STATUS_FILTER_LABEL_BY_API[trimmed] ?? fallback);
};

const normalizePolicySearchStateKeyword = (value) => {
  if (typeof value !== "string") return "";
  return value.trim().slice(0, MAX_POLICY_SEARCH_STATE_KEYWORD_LENGTH);
};

const stringifyRouteState = (state) => JSON.stringify(state ?? null);

const ddayStyle = (dday) => {
  if (dday === "종료") return { background: LINE2, color: INK3 };
  if (dday === "상시/문의" || dday === "진행중" || dday === "상시") return { background: OK_BG, color: OK };
  if (dday === "예정") return { background: "#eff6ff", color: "#1d4ed8" };
  if (dday === "D-Day") return { background: "#fef2f2", color: WARN };
  const n = parseInt(String(dday).replace("D-", ""), 10);
  if (!Number.isNaN(n) && n <= 14) return { background: "#fef2f2", color: WARN };
  return { background: AS, color: A };
};

// ── 필터 사이드바 섹션 ─────────────────────────────────────────────────────────

function FilterSection({ title, defaultOpen = true, children }) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div style={{ borderBottom: `1px solid ${LINE2}`, padding: "14px 0" }}>
      <div
        onClick={() => setOpen(!open)}
        style={{ display: "flex", justifyContent: "space-between", alignItems: "center", cursor: "pointer", marginBottom: open ? 10 : 0 }}
      >
        <span style={{ fontSize: 14, fontWeight: 700, color: INK }}>{title}</span>
        <span style={{ fontSize: 12, color: INK3 }}>{open ? "−" : "+"}</span>
      </div>
      {open && children}
    </div>
  );
}

function RadioItem({ label, checked, onChange }) {
  return (
    <label onClick={onChange} style={{ display: "flex", alignItems: "center", gap: 8, cursor: "pointer", padding: "4px 0" }}>
      <span style={{
        width: 16, height: 16, borderRadius: "50%", border: `1.5px solid ${checked ? A : LINE}`,
        background: checked ? A : "white", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0,
      }}>
        {checked && <span style={{ width: 6, height: 6, borderRadius: "50%", background: "white" }} />}
      </span>
      <span style={{ fontSize: 13, color: checked ? INK : INK2, fontWeight: checked ? 700 : 400 }}>{label}</span>
    </label>
  );
}

function StandardCodePolicyPrompt({
  missingCount,
  filledCount,
  totalCount,
  onNavigate,
}) {
  if (!missingCount) return null;
  return (
    <section style={{
      marginBottom: 16,
      background: "linear-gradient(135deg, #eff6ff 0%, #ffffff 100%)",
      border: `1px solid ${LINE}`,
      borderRadius: 18,
      padding: "18px 20px",
      display: "flex",
      justifyContent: "space-between",
      gap: 16,
      alignItems: "center",
      flexWrap: "wrap",
    }}>
      <div>
        <div style={{ fontSize: 12, fontWeight: 800, color: A7, letterSpacing: "0.04em", textTransform: "uppercase" }}>
          추천 정확도 보강
        </div>
        <div style={{ fontSize: 17, fontWeight: 800, color: INK, marginTop: 6, letterSpacing: "-0.02em" }}>
          선택 프로필 {filledCount}/{totalCount}개 입력됨
        </div>
        <div style={{ fontSize: 13, color: INK2, marginTop: 6, lineHeight: 1.6 }}>
          선택 프로필 {missingCount}개를 보완하면 주거·복지 자격조건 매칭이 더 정확해집니다.
        </div>
      </div>
      <button
        onClick={onNavigate}
        style={{
          padding: "12px 16px",
          borderRadius: 12,
          border: "none",
          background: A,
          color: "white",
          fontSize: 14,
          fontWeight: 700,
          cursor: "pointer",
          whiteSpace: "nowrap",
          boxShadow: "0 8px 24px rgba(37,99,235,0.14)",
        }}
      >
        선택 정보 보완하기 →
      </button>
    </section>
  );
}

function PriorityPolicyPrompt({ onNavigate }) {
  return (
    <section style={{
      marginBottom: 16,
      background: "linear-gradient(135deg, #fff7ed 0%, #ffffff 100%)",
      border: `1px solid ${LINE}`,
      borderRadius: 18,
      padding: "18px 20px",
      display: "flex",
      justifyContent: "space-between",
      gap: 16,
      alignItems: "center",
      flexWrap: "wrap",
    }}>
      <div>
        <div style={{ fontSize: 12, fontWeight: 800, color: "#c2410c", letterSpacing: "0.04em", textTransform: "uppercase" }}>
          추천 우선순위 필요
        </div>
        <div style={{ fontSize: 17, fontWeight: 800, color: INK, marginTop: 6, letterSpacing: "-0.02em" }}>
          아직 추천 우선순위를 설정하지 않았어요
        </div>
        <div style={{ fontSize: 13, color: INK2, marginTop: 6, lineHeight: 1.6 }}>
          최근 추천 품질 점검 기준으로는 우선순위가 비어 있을 때 상단 정책이 더 쉽게 반복됐습니다.
          {" "}주거, 일자리, 교육처럼 먼저 보고 싶은 축을 정해두면 재추천과 정책 탐색이 더 안정적입니다.
        </div>
      </div>
      <button
        onClick={onNavigate}
        style={{
          padding: "12px 16px",
          borderRadius: 12,
          border: "none",
          background: "#c2410c",
          color: "white",
          fontSize: 14,
          fontWeight: 700,
          cursor: "pointer",
          whiteSpace: "nowrap",
          boxShadow: "0 8px 24px rgba(194,65,12,0.14)",
        }}
      >
        우선순위 설정 →
      </button>
    </section>
  );
}

// ── 정책 카드 ─────────────────────────────────────────────────────────────────

function PolicyRow({ p, onNavigate, onBookmark }) {
  const ds = ddayStyle(p.dday);
  const isUrgent = p.dday.startsWith("D-") && parseInt(p.dday.replace("D-", "")) <= 14;
  return (
    <div
      data-testid={`policy-result-${p.id}`}
      style={{ background: "white", border: `1px solid ${isUrgent ? "#fecaca" : LINE}`, borderRadius: 14, padding: "18px 20px", cursor: "pointer", transition: "border-color .15s, box-shadow .15s", display: "flex", gap: 16 }}
      onClick={() => onNavigate(p.id)}
      onMouseEnter={e => { e.currentTarget.style.borderColor = isUrgent ? "#fca5a5" : A; e.currentTarget.style.boxShadow = "0 4px 16px rgba(37,99,235,0.08)"; }}
      onMouseLeave={e => { e.currentTarget.style.borderColor = isUrgent ? "#fecaca" : LINE; e.currentTarget.style.boxShadow = ""; }}
    >
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: "flex", gap: 6, alignItems: "center", marginBottom: 10, flexWrap: "wrap" }}>
          <span style={{ display: "inline-flex", fontSize: 12, padding: "3px 10px", borderRadius: 99, fontWeight: 600, background: AS, color: AI }}>
            {p.category}
          </span>
          <span style={{ display: "inline-flex", fontSize: 12, padding: "3px 10px", borderRadius: 99, fontWeight: 600, ...ds }}>
            {p.dday}
          </span>
        </div>
        <div style={{ fontSize: 16, fontWeight: 700, letterSpacing: "-0.01em", lineHeight: 1.35, color: INK }}>{p.title}</div>
        <div style={{ fontSize: 13, color: INK2, marginTop: 6, lineHeight: 1.55, overflow: "hidden", display: "-webkit-box", WebkitLineClamp: 1, WebkitBoxOrient: "vertical" }}>
          {p.summary}
        </div>
        <PolicyMetaBadges badges={p.youthOfficialBadges?.length ? p.youthOfficialBadges : p.gov24Badges} />
        <div style={{ display: "flex", gap: 14, marginTop: 10, fontSize: 12, color: INK3, flexWrap: "wrap" }}>
          {p.sourceTypeLabel && <span>출처 {p.sourceTypeLabel}</span>}
          {p.orgName && <span>🏢 {p.orgName}</span>}
          {p.regionText && <span>📍 {p.regionText}</span>}
          {!p.orgName && p.source && <span>🏢 {p.source}</span>}
        </div>
      </div>
      <div style={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", flexShrink: 0, gap: 4 }}>
        <button
          onClick={(e) => { e.stopPropagation(); onBookmark(p.id, e); }}
          style={{ width: 36, height: 36, borderRadius: 10, border: `1px solid ${LINE}`, display: "flex", alignItems: "center", justifyContent: "center", background: "white", cursor: "pointer", color: p.bookmarked ? "#f59e0b" : INK3 }}
        >
          {p.bookmarked ? <BookmarkIcon style={{ fontSize: 18 }} /> : <BookmarkBorderIcon style={{ fontSize: 18 }} />}
        </button>
        <span style={{ fontSize: 10, color: INK3 }}>저장</span>
      </div>
    </div>
  );
}

function PolicyCard({ p, onNavigate, onBookmark }) {
  const ds = ddayStyle(p.dday);
  return (
    <div
      data-testid={`policy-result-${p.id}`}
      style={{ background: "white", border: `1px solid ${LINE}`, borderRadius: 14, padding: 18, cursor: "pointer", transition: "box-shadow .15s" }}
      onClick={() => onNavigate(p.id)}
      onMouseEnter={e => { e.currentTarget.style.boxShadow = "0 4px 16px rgba(37,99,235,0.1)"; }}
      onMouseLeave={e => { e.currentTarget.style.boxShadow = ""; }}
    >
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 10 }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          <span style={{ display: "inline-flex", fontSize: 12, padding: "3px 10px", borderRadius: 99, fontWeight: 600, background: AS, color: AI }}>{p.category}</span>
          <span style={{ display: "inline-flex", fontSize: 12, padding: "3px 10px", borderRadius: 99, fontWeight: 600, ...ds }}>{p.dday}</span>
        </div>
        <button onClick={(e) => { e.stopPropagation(); onBookmark(p.id, e); }} style={{ background: "none", border: "none", cursor: "pointer", color: p.bookmarked ? "#f59e0b" : INK3, padding: 0 }}>
          {p.bookmarked ? <BookmarkIcon style={{ fontSize: 18 }} /> : <BookmarkBorderIcon style={{ fontSize: 18 }} />}
        </button>
      </div>
      <div style={{ fontSize: 15, fontWeight: 700, lineHeight: 1.4, color: INK, marginBottom: 6 }}>{p.title}</div>
      <div style={{ fontSize: 12, color: INK2, lineHeight: 1.5, overflow: "hidden", display: "-webkit-box", WebkitLineClamp: 2, WebkitBoxOrient: "vertical" }}>{p.summary}</div>
      <PolicyMetaBadges badges={p.youthOfficialBadges?.length ? p.youthOfficialBadges : p.gov24Badges} />
      <div style={{ display: "flex", gap: 10, marginTop: 8, fontSize: 12, color: INK3, flexWrap: "wrap" }}>
        {p.sourceTypeLabel && <span>출처 {p.sourceTypeLabel}</span>}
        {p.source && <span>🏢 {p.source}</span>}
      </div>
    </div>
  );
}

// ── 메인 컴포넌트 ─────────────────────────────────────────────────────────────

export default function PoliciesPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const returnLocation = buildSafeReturnLocation(location);
  const { isLoggedIn, user, filterSettings, setUser } = useAuthStore();
  const defaultStatusFilter = filterSettings?.includeExpired ? "전부표기" : "신청가능";
  const initialSearchKeyword = normalizePolicySearchStateKeyword(location.state?.policySearchKeyword)
    || normalizePolicySearchStateKeyword(searchParams.get("search"));

  const [draftSearch, setDraftSearch] = useState(initialSearchKeyword);
  const [appliedSearch, setAppliedSearch] = useState(initialSearchKeyword);
  const [selectedCat, setSelectedCat] = useState(searchParams.get("category") || "");
  const [region, setRegion] = useState(searchParams.get("region") || "전체");
  const initialRegionSelection = resolveFilterRegionSelection(
    searchParams.get("region") || "전체",
    searchParams.get("subRegion") || "전체",
    searchParams.get("ward") || "전체"
  );
  const [subRegion, setSubRegion] = useState(initialRegionSelection.subRegion);
  const [ward, setWard] = useState(initialRegionSelection.ward);
  const [income, setIncome] = useState(searchParams.get("income") || "전체");
  const [targetGroup, setTargetGroup] = useState(searchParams.get("targetGroup") || "");
  const [sourceType, setSourceType] = useState(normalizeSourceTypeParam(searchParams.get("sourceType")));
  const [gov24ServiceField, setGov24ServiceField] = useState(
    normalizeGov24OptionParam(searchParams.get("gov24ServiceField"), GOV24_SERVICE_FIELDS)
  );
  const [gov24UserType, setGov24UserType] = useState(
    normalizeGov24OptionParam(searchParams.get("gov24UserType"), GOV24_USER_TYPES)
  );
  const [gov24BenefitType, setGov24BenefitType] = useState(
    normalizeGov24OptionParam(searchParams.get("gov24BenefitType"), GOV24_BENEFIT_TYPES)
  );
  const [statusFilter, setStatusFilter] = useState(
    normalizeStatusFilterParam(searchParams.get("statusFilter"), defaultStatusFilter)
  );
  const [sort, setSort] = useState(resolveInitialSort(initialSearchKeyword, searchParams.get("sort")));
  const [pageSize, setPageSize] = useState(Number(searchParams.get("pageSize")) || 10);
  const [cols, setCols] = useState(1);
  const [page, setPage] = useState(Number(searchParams.get("page")) || 1);
  const [policies, setPolicies] = useState([]);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(false);
  const [policiesLoaded, setPoliciesLoaded] = useState(false);
  const [totalCount, setTotalCount] = useState(0);
  const [trendingKeywords, setTrendingKeywords] = useState(FALLBACK_TRENDING);
  const [suggestions, setSuggestions] = useState([]);
  const [searchFocused, setSearchFocused] = useState(false);
  const [highlightedSuggestionIndex, setHighlightedSuggestionIndex] = useState(-1);
  const [toast, setToast] = useState({ open: false, msg: "", severity: "info" });
  const [incomeCalcOpen, setIncomeCalcOpen] = useState(false);
  const [viewportWidth, setViewportWidth] = useState(() => (typeof window === "undefined" ? 1280 : window.innerWidth));
  const bookmarkActionKeyRef = useRef(null);
  const searchSurfaceRef = useRef(null);

  const hasSearch = Boolean(appliedSearch.trim());
  const defaultSort = appliedSearch.trim() ? "relevance" : "latest";
  const hasActiveFilters = Boolean(
    selectedCat
    || region !== "전체"
    || subRegion !== "전체"
    || ward !== "전체"
    || income !== "전체"
    || targetGroup
    || sourceType !== "전체"
    || gov24ServiceField !== "전체"
    || gov24UserType !== "전체"
    || gov24BenefitType !== "전체"
    || statusFilter !== defaultStatusFilter
  );
  const hasCustomSort = sort !== defaultSort;
  const canResetSearch = Boolean(draftSearch.trim() || appliedSearch.trim());
  const isTablet = viewportWidth < 1100;
  const isMobile = viewportWidth < 760;
  const [filterOpen, setFilterOpen] = useState(false);
  const districtOptions = getDistrictOptions(region, { includeAll: true });
  const wardOptions = getWardOptions(region, subRegion, { includeAll: true });
  const standardCodeMissingCount = user?.standardCodeMissingCount ?? 0;
  const standardCodeTotalCount = user?.standardCodeTotalCount ?? 4;
  const standardCodeFilledCount = user?.standardCodeFilledCount ?? Math.max(0, standardCodeTotalCount - standardCodeMissingCount);

  // ── URL 파라미터 동기화 ──────────────────────────────────────────────────────
  useEffect(() => {
    const params = {};
    if (selectedCat) params.category = selectedCat;
    if (region !== "전체") params.region = region;
    if (subRegion !== "전체") params.subRegion = subRegion;
    if (ward !== "전체") params.ward = ward;
    if (income !== "전체") params.income = income;
    if (targetGroup) params.targetGroup = targetGroup;
    const sourceTypeParam = serializeSourceTypeParam(sourceType);
    if (sourceTypeParam) params.sourceType = sourceTypeParam;
    if (sourceTypeParam === "GOV24" && gov24ServiceField !== "전체") params.gov24ServiceField = gov24ServiceField;
    if (sourceTypeParam === "GOV24" && gov24UserType !== "전체") params.gov24UserType = gov24UserType;
    if (sourceTypeParam === "GOV24" && gov24BenefitType !== "전체") params.gov24BenefitType = gov24BenefitType;
    if (statusFilter !== defaultStatusFilter) params.statusFilter = statusFilter;
    if (sort !== defaultSort) params.sort = sort;
    if (page !== 1) params.page = String(page);
    if (pageSize !== 10) params.pageSize = String(pageSize);
    const nextRouteState = {
      ...(sanitizeTransientRouteState(location.state) ?? {}),
    };
    const normalizedSearchState = normalizePolicySearchStateKeyword(appliedSearch);
    if (normalizedSearchState) {
      nextRouteState.policySearchKeyword = normalizedSearchState;
    } else {
      delete nextRouteState.policySearchKeyword;
    }
    const nextState = Object.keys(nextRouteState).length ? nextRouteState : undefined;
    const nextSearch = new URLSearchParams(params).toString();
    const currentState = sanitizeTransientRouteState(location.state);
    if (
      searchParams.toString() === nextSearch
      && stringifyRouteState(currentState) === stringifyRouteState(nextState)
    ) {
      return;
    }
    // 페이지 이동만 바뀐 경우엔 히스토리에 push(뒤로가기 시 이전 페이지로 복귀),
    // 필터·정렬 변경 등은 기존처럼 replace(뒤로가기로 필터 토글 안 되게).
    const curNoPage = new URLSearchParams(searchParams.toString());
    const nextNoPage = new URLSearchParams(nextSearch);
    const nextPageValue = nextNoPage.get("page") || "";
    curNoPage.delete("page");
    nextNoPage.delete("page");
    curNoPage.sort();
    nextNoPage.sort();
    const onlyPageChanged =
      curNoPage.toString() === nextNoPage.toString()
      && (searchParams.get("page") || "") !== nextPageValue;
    setSearchParams(params, {
      replace: !onlyPageChanged,
      state: nextState,
    });
  }, [appliedSearch, defaultSort, defaultStatusFilter, gov24BenefitType, gov24ServiceField, gov24UserType, income, location.state, page, pageSize, region, searchParams, selectedCat, setSearchParams, sort, sourceType, statusFilter, subRegion, targetGroup, ward]);

  useEffect(() => {
    const nextSelectedCat = searchParams.get("category") || "";
    const nextRegion = searchParams.get("region") || "전체";
    const nextRegionSelection = resolveFilterRegionSelection(
      nextRegion,
      searchParams.get("subRegion") || "전체",
      searchParams.get("ward") || "전체"
    );
    const nextSubRegion = nextRegionSelection.subRegion;
    const nextWard = nextRegionSelection.ward;
    const nextIncome = searchParams.get("income") || "전체";
    const nextTargetGroup = searchParams.get("targetGroup") || "";
    const nextSourceType = normalizeSourceTypeParam(searchParams.get("sourceType"));
    const nextGov24ServiceField = normalizeGov24OptionParam(searchParams.get("gov24ServiceField"), GOV24_SERVICE_FIELDS);
    const nextGov24UserType = normalizeGov24OptionParam(searchParams.get("gov24UserType"), GOV24_USER_TYPES);
    const nextGov24BenefitType = normalizeGov24OptionParam(searchParams.get("gov24BenefitType"), GOV24_BENEFIT_TYPES);
    const nextStatusFilter = normalizeStatusFilterParam(searchParams.get("statusFilter"), defaultStatusFilter);
    const nextSort = resolveInitialSort(appliedSearch, searchParams.get("sort"));
    const nextPageSize = Number(searchParams.get("pageSize")) || 10;
    const nextPage = Number(searchParams.get("page")) || 1;

    setSelectedCat((prev) => (prev === nextSelectedCat ? prev : nextSelectedCat));
    setRegion((prev) => (prev === nextRegion ? prev : nextRegion));
    setSubRegion((prev) => (prev === nextSubRegion ? prev : nextSubRegion));
    setWard((prev) => (prev === nextWard ? prev : nextWard));
    setIncome((prev) => (prev === nextIncome ? prev : nextIncome));
    setTargetGroup((prev) => (prev === nextTargetGroup ? prev : nextTargetGroup));
    setSourceType((prev) => (prev === nextSourceType ? prev : nextSourceType));
    setGov24ServiceField((prev) => (prev === nextGov24ServiceField ? prev : nextGov24ServiceField));
    setGov24UserType((prev) => (prev === nextGov24UserType ? prev : nextGov24UserType));
    setGov24BenefitType((prev) => (prev === nextGov24BenefitType ? prev : nextGov24BenefitType));
    setStatusFilter((prev) => (prev === nextStatusFilter ? prev : nextStatusFilter));
    setSort((prev) => (prev === nextSort ? prev : nextSort));
    setPageSize((prev) => (prev === nextPageSize ? prev : nextPageSize));
    setPage((prev) => (prev === nextPage ? prev : nextPage));
  }, [appliedSearch, defaultStatusFilter, searchParams]);

  useEffect(() => {
    if (sourceType !== GOV24_SOURCE_LABEL && gov24ServiceField !== "전체") {
      setGov24ServiceField("전체");
    }
    if (sourceType !== GOV24_SOURCE_LABEL && gov24UserType !== "전체") {
      setGov24UserType("전체");
    }
    if (sourceType !== GOV24_SOURCE_LABEL && gov24BenefitType !== "전체") {
      setGov24BenefitType("전체");
    }
  }, [gov24BenefitType, gov24ServiceField, gov24UserType, sourceType]);

  useEffect(() => {
    if (!appliedSearch.trim() && sort === "relevance") { setSort("latest"); return; }
    if (appliedSearch.trim() && !searchParams.get("sort") && sort !== "relevance") setSort("relevance");
  }, [appliedSearch]); // eslint-disable-line

  useEffect(() => {
    if (!isLoggedIn) {
      return undefined;
    }

    const controller = new AbortController();
    api.get("/api/users/me", { signal: controller.signal })
      .then(({ data }) => {
        const profile = data?.data;
        if (!profile) {
          return;
        }
        const standardCodeCompletion = resolveStandardProfileCodeCompletion(profile);
        setUser({
          ...(profile.name ? { name: profile.name } : {}),
          ...(profile.email ? { email: profile.email } : {}),
          hasPriorities: Array.isArray(profile.priorities) && profile.priorities.length > 0,
          standardCodeFilledCount: standardCodeCompletion.filledCount,
          standardCodeMissingCount: standardCodeCompletion.missingCount,
          standardCodeTotalCount: standardCodeCompletion.totalCount,
        });
      })
      .catch((error) => {
        if (error.name === "CanceledError" || error.code === "ERR_CANCELED") {
          return;
        }
      });

    return () => controller.abort();
  }, [isLoggedIn, setUser]);

  useEffect(() => {
    const controller = new AbortController();

    const fetchTrendingKeywords = async () => {
      try {
        const { data } = await api.get("/api/policies/search/trending", {
          params: { limit: TRENDING_LIMIT },
          signal: controller.signal,
        });
        const nextKeywords = Array.isArray(data.data) ? data.data.filter(Boolean) : [];
        setTrendingKeywords(nextKeywords.length > 0 ? nextKeywords : FALLBACK_TRENDING);
      } catch (error) {
        if (error.name === "CanceledError" || error.code === "ERR_CANCELED") return;
        setTrendingKeywords(FALLBACK_TRENDING);
      }
    };

    fetchTrendingKeywords();
    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (!draftSearch.trim()) {
      setSuggestions([]);
      setHighlightedSuggestionIndex(-1);
      return undefined;
    }

    const controller = new AbortController();
    const timerId = window.setTimeout(async () => {
      try {
        const { data } = await api.post(
          "/api/policies/search/suggestions",
          {
            keyword: draftSearch.trim(),
            limit: SUGGESTION_LIMIT,
          },
          { signal: controller.signal }
        );
        const nextSuggestions = Array.isArray(data.data) ? data.data.filter(Boolean) : [];
        setSuggestions(nextSuggestions);
        setHighlightedSuggestionIndex(-1);
      } catch (error) {
        if (error.name === "CanceledError" || error.code === "ERR_CANCELED") return;
        setSuggestions([]);
        setHighlightedSuggestionIndex(-1);
      }
    }, 180);

    return () => {
      window.clearTimeout(timerId);
      controller.abort();
    };
  }, [draftSearch]);

  useEffect(() => {
    const handlePointerDown = (event) => {
      if (!searchSurfaceRef.current?.contains(event.target)) {
        setSearchFocused(false);
      }
    };

    document.addEventListener("mousedown", handlePointerDown);
    return () => document.removeEventListener("mousedown", handlePointerDown);
  }, []);

  useEffect(() => {
    const handleResize = () => setViewportWidth(window.innerWidth);
    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, []);

  // ── 정책 fetch ───────────────────────────────────────────────────────────────
  useEffect(() => {
    const controller = new AbortController();
    const selectedSgg = resolveSubmittedSgg(region, subRegion, ward);
    const fetchPolicies = async () => {
      setLoading(true);
      setPoliciesLoaded(false);
      try {
        const commonParams = {
          category: selectedCat || undefined,
          sido: region === "전체" ? undefined : region,
          sgg: selectedSgg,
          sourceType: sourceType === "전체" ? undefined : POLICY_SOURCE_TYPE_MAP[sourceType],
          gov24ServiceField: sourceType === GOV24_SOURCE_LABEL && gov24ServiceField !== "전체" ? gov24ServiceField : undefined,
          gov24UserType: sourceType === GOV24_SOURCE_LABEL && gov24UserType !== "전체" ? gov24UserType : undefined,
          gov24BenefitType: sourceType === GOV24_SOURCE_LABEL && gov24BenefitType !== "전체" ? gov24BenefitType : undefined,
          sort: appliedSearch.trim() ? (POLICY_SORT_MAP[sort] ?? "RELEVANCE") : (sort === "relevance" ? "LATEST" : (POLICY_SORT_MAP[sort] ?? "LATEST")),
          incomeLevel: income === "전체" ? undefined : Number(income),
          targetGroup: targetGroup || undefined,
          page: page - 1,
          size: pageSize,
        };
        const apiStatusFilter = POLICY_STATUS_FILTER_MAP[statusFilter] ?? "ACTIVE_ONLY";

        const searchKeyword = appliedSearch.trim();
        const { data } = searchKeyword
          ? await api.post(
            "/api/policies/search",
            { ...commonParams, keyword: searchKeyword, statusFilter: apiStatusFilter },
            { signal: controller.signal }
          )
          : await api.get("/api/policies", {
            params: { ...commonParams, statusFilter: apiStatusFilter },
            signal: controller.signal,
          });
        const pageData = data.data ?? {};
        setPolicies((pageData.content ?? []).map(mapPolicySummary));
        setTotalCount(pageData.totalElements ?? 0);
        setTotalPages(Math.max(pageData.totalPages ?? 1, 1));
      } catch (error) {
        if (error.name === "CanceledError" || error.code === "ERR_CANCELED") return;
        setPolicies([]); setTotalPages(1); setTotalCount(0);
        setToast({ open: true, msg: "정책 목록을 불러오지 못했습니다", severity: "error" });
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false);
          setPoliciesLoaded(true);
        }
      }
    };
    fetchPolicies();
    return () => controller.abort();
  }, [appliedSearch, gov24BenefitType, gov24ServiceField, gov24UserType, isLoggedIn, statusFilter, page, pageSize, region, selectedCat, sort, sourceType, subRegion, income, targetGroup, ward]);

  // ── 핸들러 ───────────────────────────────────────────────────────────────────
  const handleBookmark = async (id, event) => {
    event.stopPropagation();
    if (!isLoggedIn) {
      navigate("/login", {
        state: {
          from: returnLocation,
          reason: "login-required",
          postLoginAction: {
            type: "toggle-bookmark",
            policyId: id,
          },
        },
      });
      return;
    }
    try {
      await api.post(`/api/policies/${id}/bookmark`);
      setPolicies((prev) => prev.map((p) => (p.id === id ? { ...p, bookmarked: !p.bookmarked } : p)));
    } catch { setToast({ open: true, msg: "북마크 처리에 실패했습니다", severity: "error" }); }
  };

  const handleCategorySelect = (val) => { setSelectedCat(val); setPage(1); };
  const handleApplyFilter = () => { setPage(1); };
  const handleResetFilter = () => {
    setSelectedCat(""); setRegion("전체"); setSubRegion("전체"); setWard("전체"); setIncome("전체");
    setTargetGroup(""); setSourceType("전체"); setGov24ServiceField("전체"); setGov24UserType("전체"); setGov24BenefitType("전체"); setStatusFilter(defaultStatusFilter);
    setSort(appliedSearch.trim() ? "relevance" : "latest"); setPage(1);
  };
  const handleResetAll = () => {
    setDraftSearch("");
    setAppliedSearch("");
    setSuggestions([]);
    setSearchFocused(false);
    setHighlightedSuggestionIndex(-1);
    setSelectedCat("");
    setRegion("전체");
    setSubRegion("전체");
    setWard("전체");
    setIncome("전체");
    setTargetGroup("");
    setSourceType("전체");
    setGov24ServiceField("전체");
    setGov24UserType("전체");
    setGov24BenefitType("전체");
    setStatusFilter(defaultStatusFilter);
    setSort("latest");
    setPageSize(10);
    setPage(1);
  };
  const handleClearSearch = () => {
    setDraftSearch("");
    setAppliedSearch("");
    setSuggestions([]);
    setSearchFocused(false);
    setHighlightedSuggestionIndex(-1);
    setSort("latest");
    setPage(1);
  };

  const applyKeywordSearch = (keyword) => {
    const normalizedKeyword = keyword.trim();
    setDraftSearch(normalizedKeyword);
    setAppliedSearch(normalizedKeyword);
    setSort(normalizedKeyword ? "relevance" : "latest");
    setPage(1);
    setSearchFocused(false);
    setHighlightedSuggestionIndex(-1);
  };

  const handleSearch = () => {
    if (highlightedSuggestionIndex >= 0 && suggestions[highlightedSuggestionIndex]) {
      applyKeywordSearch(suggestions[highlightedSuggestionIndex]);
      return;
    }
    applyKeywordSearch(draftSearch);
  };
  const safeChatFromStateTarget = resolveSafeRouteTarget(location.state?.chatFrom);
  const safeFromTarget = resolveSafeRouteTarget(location.state?.from);
  const chatFromTarget = safeChatFromStateTarget?.pathname === "/chat"
    ? safeChatFromStateTarget
    : safeFromTarget?.pathname === "/chat"
      ? safeFromTarget
      : undefined;
  const navigateToPolicyDetail = (policyId) => {
    navigate(`/policies/${policyId}`, {
      state: {
        from: returnLocation,
        chatFrom: chatFromTarget,
      },
    });
  };

  // ── 활성 필터 칩 목록 ────────────────────────────────────────────────────────
  const activeFilters = [
    appliedSearch.trim() && { key: "search", label: `검색어 ${appliedSearch.trim()}`, clear: handleClearSearch },
    selectedCat && { key: "cat", label: POLICY_CATEGORIES.find(c => c.value === selectedCat)?.label || selectedCat, clear: () => setSelectedCat("") },
    region !== "전체" && { key: "region", label: formatRegionSelectionLabel(region, subRegion, ward), clear: () => { setRegion("전체"); setSubRegion("전체"); setWard("전체"); } },
    income !== "전체" && { key: "income", label: POLICY_INCOME_ROWS.find(r => r.value === income)?.label, clear: () => setIncome("전체") },
    targetGroup && { key: "tg", label: targetGroup, clear: () => setTargetGroup("") },
    sourceType !== "전체" && { key: "src", label: sourceType, clear: () => setSourceType("전체") },
    sourceType === GOV24_SOURCE_LABEL && gov24ServiceField !== "전체" && { key: "gov24sf", label: `정부24 서비스분야 ${gov24ServiceField}`, clear: () => setGov24ServiceField("전체") },
    sourceType === GOV24_SOURCE_LABEL && gov24UserType !== "전체" && { key: "gov24ut", label: `정부24 사용자구분 ${gov24UserType}`, clear: () => setGov24UserType("전체") },
    sourceType === GOV24_SOURCE_LABEL && gov24BenefitType !== "전체" && { key: "gov24bt", label: `정부24 지원유형 ${gov24BenefitType}`, clear: () => setGov24BenefitType("전체") },
    statusFilter !== defaultStatusFilter && { key: "status", label: statusFilter, clear: () => setStatusFilter(defaultStatusFilter) },
    hasCustomSort && {
      key: "sort",
      label: sort === "relevance" ? "관련도순" : sort === "views" ? "인기순" : sort === "deadline" ? "마감임박순" : "최신순",
      clear: () => setSort(defaultSort),
    },
  ].filter(Boolean);

  useEffect(() => {
    const postLoginAction = sanitizePostLoginAction(location.state?.postLoginAction);
    if (!isLoggedIn || loading || postLoginAction?.type !== "toggle-bookmark") {
      if (!postLoginAction) {
        bookmarkActionKeyRef.current = null;
      }
      return;
    }
    if (!policiesLoaded) {
      return;
    }

    const clearPostLoginAction = () => {
      const nextState = { ...(sanitizeTransientRouteState(location.state) ?? {}) };
      delete nextState.postLoginAction;
      navigate(`${location.pathname}${location.search}`, {
        replace: true,
        state: Object.keys(nextState).length ? nextState : undefined,
      });
    };

    const actionKey = `${postLoginAction.type}:${postLoginAction.policyId}`;
    if (bookmarkActionKeyRef.current === actionKey) {
      return;
    }

    let cancelled = false;
    const runPostLoginAction = async () => {
      bookmarkActionKeyRef.current = actionKey;
      try {
        await api.post(`/api/policies/${postLoginAction.policyId}/bookmark`);
        if (cancelled) {
          return;
        }
        const targetPolicy = policies.find((policy) => String(policy.id) === String(postLoginAction.policyId));
        const nextBookmarked = targetPolicy ? !targetPolicy.bookmarked : true;
        setPolicies((prev) => prev.map((policy) => (
          String(policy.id) === String(postLoginAction.policyId)
            ? { ...policy, bookmarked: nextBookmarked }
            : policy
        )));
        setToast({
          open: true,
          msg: nextBookmarked ? "북마크에 저장했어요" : "북마크를 해제했어요",
          severity: "success",
        });
      } catch {
        if (!cancelled) {
          setToast({ open: true, msg: "북마크 처리에 실패했습니다", severity: "error" });
        }
      } finally {
        if (!cancelled) {
          clearPostLoginAction();
        }
      }
    };

    void runPostLoginAction();

    return () => {
      cancelled = true;
    };
  }, [isLoggedIn, loading, location.pathname, location.search, location.state, navigate, policies, policiesLoaded]);

  return (
    <div style={{ minHeight: "100vh", background: "#f7f8fc" }}>
      <Header />

      {/* 검색 히어로 */}
      <div style={{ background: "white", borderBottom: `1px solid ${LINE}`, padding: isMobile ? "20px 16px 16px" : "32px 24px 24px" }}>
        <div style={{ maxWidth: 1240, margin: "0 auto" }}>
          <div style={{ fontSize: 26, fontWeight: 800, letterSpacing: "-0.02em", color: INK }}>청년을 위한 복지정책을 찾아드려요</div>
          <div style={{ fontSize: 14, color: INK3, marginTop: 6 }}>
            {totalCount > 0 ? `${totalCount.toLocaleString()}개 정책 중에서 내 조건에 맞는 정책만 골라보세요.` : "조건에 맞는 정책을 검색해보세요."}
          </div>
          <div ref={searchSurfaceRef} style={{ marginTop: 20, position: "relative" }}>
            <div style={{ display: "flex", flexDirection: isMobile ? "column" : "row", gap: 10, background: "white", border: `1px solid ${LINE}`, borderRadius: 14, padding: 8, boxShadow: "0 2px 8px rgba(0,0,0,0.04)" }}>
              <div style={{ flex: 1, display: "flex", alignItems: "center", gap: 10, padding: "0 12px" }}>
                <SearchIcon style={{ color: INK3, fontSize: 20 }} />
                <input
                  value={draftSearch}
                  onChange={(e) => {
                    setDraftSearch(e.target.value);
                    setHighlightedSuggestionIndex(-1);
                  }}
                  onFocus={() => setSearchFocused(true)}
                  onKeyDown={(e) => {
                    if (e.key === "ArrowDown") {
                      if (suggestions.length === 0) return;
                      e.preventDefault();
                      setHighlightedSuggestionIndex((prev) => (prev + 1) % suggestions.length);
                      return;
                    }
                    if (e.key === "ArrowUp") {
                      if (suggestions.length === 0) return;
                      e.preventDefault();
                      setHighlightedSuggestionIndex((prev) => (prev <= 0 ? suggestions.length - 1 : prev - 1));
                      return;
                    }
                    if (e.key === "Escape") {
                      setSearchFocused(false);
                      setHighlightedSuggestionIndex(-1);
                      return;
                    }
                    if (e.key === "Enter") {
                      e.preventDefault();
                      handleSearch();
                    }
                  }}
                  placeholder="정책명, 키워드를 검색해보세요 (예: 월세, 창업)"
                  style={{ border: 0, outline: 0, flex: 1, fontSize: 15, padding: "12px 0", background: "transparent", fontFamily: "inherit", color: INK, minWidth: 0 }}
                />
                {canResetSearch && (
                  <button
                    onClick={handleClearSearch}
                    style={{ border: 0, background: "none", color: INK3, cursor: "pointer", fontSize: 13, fontWeight: 700, padding: 0, flexShrink: 0 }}
                  >
                    지우기
                  </button>
                )}
              </div>
              <button
                onClick={handleSearch}
                style={{ padding: isMobile ? "12px 0" : "0 22px", background: A, color: "white", border: 0, borderRadius: 10, fontSize: 14, fontWeight: 700, cursor: "pointer", width: isMobile ? "100%" : "auto" }}
              >
                검색
              </button>
            </div>
            {searchFocused && draftSearch.trim() && suggestions.length > 0 && (
              <div style={{ position: "absolute", top: "calc(100% + 10px)", left: 0, right: 0, background: "white", border: `1px solid ${LINE}`, borderRadius: 14, boxShadow: "0 14px 32px rgba(17,19,26,0.08)", overflow: "hidden", zIndex: 5 }}>
                {suggestions.map((keyword, index) => (
                  <button
                    key={keyword}
                    onMouseDown={(e) => e.preventDefault()}
                    onClick={() => applyKeywordSearch(keyword)}
                    onMouseEnter={() => setHighlightedSuggestionIndex(index)}
                    style={{
                      width: "100%",
                      display: "flex",
                      alignItems: "center",
                      gap: 10,
                      padding: "13px 16px",
                      background: index === highlightedSuggestionIndex ? "#f8fbff" : "white",
                      border: 0,
                      borderTop: `1px solid ${LINE2}`,
                      cursor: "pointer",
                      textAlign: "left",
                    }}
                  >
                    <SearchIcon style={{ color: INK3, fontSize: 18 }} />
                    <span style={{ fontSize: 14, color: INK }}>{keyword}</span>
                  </button>
                ))}
              </div>
            )}
            {searchFocused && draftSearch.trim() && suggestions.length === 0 && (
              <div style={{ position: "absolute", top: "calc(100% + 10px)", left: 0, right: 0, background: "white", border: `1px solid ${LINE}`, borderRadius: 14, boxShadow: "0 14px 32px rgba(17,19,26,0.08)", overflow: "hidden", zIndex: 5 }}>
                <button
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => applyKeywordSearch(draftSearch)}
                  style={{ width: "100%", display: "flex", alignItems: "center", gap: 10, padding: "13px 16px", background: "white", border: 0, cursor: "pointer", textAlign: "left" }}
                >
                  <SearchIcon style={{ color: INK3, fontSize: 18 }} />
                  <span style={{ fontSize: 14, color: INK }}>
                    <strong style={{ fontWeight: 800 }}>&quot;{draftSearch.trim()}&quot;</strong>로 바로 검색
                  </span>
                </button>
                <div style={{ padding: "0 16px 14px", fontSize: 12, color: INK3 }}>
                  추천 검색어가 없으면 현재 입력어로 바로 찾아볼 수 있어요.
                </div>
              </div>
            )}
          </div>
          {/* 인기 검색어 */}
          <div style={{ marginTop: 12, display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            <span style={{ fontSize: 12, color: INK3, fontWeight: 700 }}>인기 검색어</span>
            {trendingKeywords.map((t, i) => (
              <button
                key={t}
                onClick={() => applyKeywordSearch(t)}
                style={{ fontSize: 12, color: INK2, padding: "4px 10px", background: "white", border: `1px solid ${LINE}`, borderRadius: 99, cursor: "pointer" }}
              >
                <span style={{ color: A, fontWeight: 700, marginRight: 4 }}>{i + 1}</span>{t}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* 콘텐츠 영역 */}
      <div style={{ maxWidth: 1240, margin: "0 auto", padding: isTablet ? "20px 16px 80px" : "28px 24px 80px", display: "grid", gridTemplateColumns: isTablet ? "1fr" : "280px 1fr", gap: 28, alignItems: "flex-start" }}>

        {/* ── 필터 사이드바 ── */}
        <aside style={{ background: "white", border: `1px solid ${LINE}`, borderRadius: 16, padding: "6px 20px 18px", position: isTablet ? "static" : "sticky", top: isTablet ? undefined : 80, display: isTablet ? (filterOpen ? "block" : "none") : "block" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "14px 0 8px", borderBottom: `1px solid ${LINE2}` }}>
            <span style={{ fontSize: 15, fontWeight: 800, color: INK }}>상세 필터</span>
            <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
              <button onClick={handleResetFilter} style={{ background: "none", border: "none", fontSize: 12, color: INK3, cursor: "pointer" }}>초기화</button>
              {isTablet && (
                <button onClick={() => setFilterOpen(false)} style={{ background: "none", border: "none", fontSize: 20, color: INK2, cursor: "pointer", padding: "0 2px", lineHeight: 1 }}>×</button>
              )}
            </div>
          </div>

          {/* 카테고리 */}
          <FilterSection title="카테고리">
            <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
              {POLICY_CATEGORIES.map((c) => (
                <RadioItem
                  key={c.value}
                  label={c.label}
                  checked={selectedCat === c.value}
                  onChange={() => handleCategorySelect(c.value)}
                />
              ))}
            </div>
          </FilterSection>

          {/* 지역 */}
          <FilterSection title="지역">
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              <select
                value={region}
                onChange={(e) => { setRegion(e.target.value); setSubRegion("전체"); setWard("전체"); setPage(1); }}
                style={{ width: "100%", padding: "9px 12px", border: `1px solid ${LINE}`, borderRadius: 8, fontSize: 13, background: "white", fontFamily: "inherit", color: INK }}
              >
                {FILTER_REGIONS.map((r) => <option key={r} value={r}>{r}</option>)}
              </select>
              {districtOptions.length > 0 && (
                <select
                  value={subRegion}
                  onChange={(e) => { setSubRegion(e.target.value); setWard("전체"); setPage(1); }}
                  style={{ width: "100%", padding: "9px 12px", border: `1px solid ${LINE}`, borderRadius: 8, fontSize: 13, background: "white", fontFamily: "inherit", color: INK }}
                >
                  {districtOptions.map((d) => <option key={d} value={d}>{d}</option>)}
                </select>
              )}
              {wardOptions.length > 0 && (
                <select
                  value={ward}
                  onChange={(e) => { setWard(e.target.value); setPage(1); }}
                  style={{ width: "100%", padding: "9px 12px", border: `1px solid ${LINE}`, borderRadius: 8, fontSize: 13, background: "white", fontFamily: "inherit", color: INK }}
                >
                  {wardOptions.map((value) => <option key={value} value={value}>{value}</option>)}
                </select>
              )}
            </div>
          </FilterSection>

          {/* 소득수준 */}
          <FilterSection title="소득수준" defaultOpen={false}>
            <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
              <RadioItem label="전체" checked={income === "전체"} onChange={() => setIncome("전체")} />
              {POLICY_INCOME_ROWS.map((r) => (
                <RadioItem key={r.value} label={r.label} checked={income === r.value} onChange={() => setIncome(r.value)} />
              ))}
            </div>
            <button
              onClick={() => setIncomeCalcOpen(true)}
              style={{ marginTop: 8, background: "none", border: "none", fontSize: 12, color: A, cursor: "pointer", fontWeight: 600, padding: 0 }}
            >
              🔗 소득분위 확인하기
            </button>
          </FilterSection>

          {/* 특화조건 */}
          <FilterSection title="특화조건" defaultOpen={false}>
            <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
              {POLICY_TARGET_GROUPS.map((tg) => {
                const active = targetGroup === tg.value;
                return (
                  <button
                    key={tg.value}
                    onClick={() => { setTargetGroup(active ? "" : tg.value); setPage(1); }}
                    style={{ fontSize: 12, padding: "5px 12px", borderRadius: 99, border: `1.5px solid ${active ? A : LINE}`, background: active ? AS : "white", color: active ? AI : INK2, fontWeight: active ? 700 : 400, cursor: "pointer" }}
                  >
                    {tg.label}
                  </button>
                );
              })}
            </div>
          </FilterSection>

          <FilterSection title="출처" defaultOpen={false}>
            <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
              {POLICY_SOURCE_OPTIONS.map((source) => (
                <RadioItem
                  key={source}
                  label={source}
                  checked={sourceType === source}
                  onChange={() => { setSourceType(source); if (source !== GOV24_SOURCE_LABEL) { setGov24ServiceField("전체"); setGov24UserType("전체"); setGov24BenefitType("전체"); } setPage(1); }}
                />
              ))}
            </div>
          </FilterSection>

          {sourceType === GOV24_SOURCE_LABEL && (
            <FilterSection title="정부24 서비스분야" defaultOpen={false}>
              <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
                {GOV24_SERVICE_FIELDS.map((field) => (
                  <RadioItem
                    key={field}
                    label={field}
                    checked={gov24ServiceField === field}
                    onChange={() => { setGov24ServiceField(field); setPage(1); }}
                  />
                ))}
              </div>
            </FilterSection>
          )}

          {sourceType === GOV24_SOURCE_LABEL && (
            <FilterSection title="정부24 사용자구분" defaultOpen={false}>
              <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
                {GOV24_USER_TYPES.map((token) => (
                  <RadioItem
                    key={token}
                    label={token}
                    checked={gov24UserType === token}
                    onChange={() => { setGov24UserType(token); setPage(1); }}
                  />
                ))}
              </div>
            </FilterSection>
          )}

          {sourceType === GOV24_SOURCE_LABEL && (
            <FilterSection title="정부24 지원유형" defaultOpen={false}>
              <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
                {GOV24_BENEFIT_TYPES.map((token) => (
                  <RadioItem
                    key={token}
                    label={token}
                    checked={gov24BenefitType === token}
                    onChange={() => { setGov24BenefitType(token); setPage(1); }}
                  />
                ))}
              </div>
            </FilterSection>
          )}

          {isTablet ? (
            <button
              onClick={() => { handleApplyFilter(); setFilterOpen(false); }}
              style={{ width: "100%", marginTop: 16, padding: "12px 0", background: A, color: "white", border: 0, borderRadius: 10, fontSize: 14, fontWeight: 700, cursor: "pointer" }}
            >
              {totalCount > 0 ? `${totalCount.toLocaleString()}개 정책 보기` : "정책 보기"}
            </button>
          ) : (
            <div
              style={{
                marginTop: 16,
                padding: "12px 14px",
                borderRadius: 12,
                background: "#f8fbff",
                border: `1px solid ${LINE}`,
                color: INK2,
              }}
            >
              <div style={{ fontSize: 13, fontWeight: 800, color: AI }}>
                필터는 선택 즉시 반영됩니다
              </div>
              <div style={{ marginTop: 4, fontSize: 12, color: INK3, lineHeight: 1.5 }}>
                지역, 카테고리, 정부24 조건을 바꾸면 목록과 URL이 바로 업데이트됩니다.
              </div>
            </div>
          )}
        </aside>

        {/* ── 결과 영역 ── */}
        <div>
          {isLoggedIn && !user?.hasPriorities && (
            <PriorityPolicyPrompt onNavigate={() => navigate("/mypage?tab=1")} />
          )}
          {isLoggedIn && standardCodeMissingCount > 0 && (
            <StandardCodePolicyPrompt
              missingCount={standardCodeMissingCount}
              filledCount={standardCodeFilledCount}
              totalCount={standardCodeTotalCount}
              onNavigate={() => navigate("/mypage?tab=0")}
            />
          )}
          {/* 모바일 필터 버튼 */}
          {isTablet && (
            <div style={{ marginBottom: 14 }}>
              <button
                onClick={() => setFilterOpen(true)}
                style={{
                  display: "inline-flex", alignItems: "center", gap: 6,
                  padding: "9px 18px", borderRadius: 10, fontSize: 13, fontWeight: 700,
                  background: activeFilters.length > 0 ? AS : "white",
                  color: activeFilters.length > 0 ? AI : INK2,
                  border: `1.5px solid ${activeFilters.length > 0 ? A : LINE}`,
                  cursor: "pointer",
                }}
              >
                🔧 필터{activeFilters.length > 0 ? ` (${activeFilters.length}개 적용됨)` : ""}
              </button>
            </div>
          )}
          {/* 활성 필터 칩 */}
          {activeFilters.length > 0 && (
            <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 14, flexWrap: "wrap" }}>
              <span style={{ fontSize: 12, color: INK3 }}>현재 적용 중:</span>
              {activeFilters.map((f) => (
                <span
                  key={f.key}
                  style={{ display: "inline-flex", alignItems: "center", gap: 6, fontSize: 12, padding: "5px 10px 5px 12px", background: AS, color: AI, borderRadius: 99, fontWeight: 600 }}
                >
                  {f.label}
                  <button onClick={f.clear} style={{ background: "none", border: "none", cursor: "pointer", color: AI, fontSize: 14, padding: 0, lineHeight: 1 }}>×</button>
                </span>
              ))}
              <button onClick={handleResetAll} style={{ fontSize: 12, color: A, fontWeight: 600, background: "none", border: "none", cursor: "pointer" }}>
                모두 해제
              </button>
            </div>
          )}

          {/* 결과 수 + 정렬 + 뷰 토글 */}
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: isMobile ? "stretch" : "center", flexDirection: isMobile ? "column" : "row", gap: 12, marginBottom: 14 }}>
            <div style={{ fontSize: 13, color: INK2 }}>
              총 <strong style={{ color: INK }}>{totalCount.toLocaleString()}</strong>개의 정책
              {loading && <span style={{ marginLeft: 8, color: INK3 }}>· 로딩 중</span>}
            </div>
            <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
              <select
                value={sort}
                onChange={(e) => { setSort(e.target.value); setPage(1); }}
                style={{ border: `1px solid ${LINE}`, borderRadius: 8, padding: "6px 10px", fontSize: 12, background: "white", fontFamily: "inherit", color: INK2 }}
              >
                {appliedSearch.trim() && <option value="relevance">관련도순</option>}
                <option value="views">인기순</option>
                <option value="latest">최신순</option>
                <option value="deadline">마감임박순</option>
              </select>
              <select
                value={pageSize}
                onChange={(e) => { setPageSize(Number(e.target.value)); setPage(1); }}
                style={{ border: `1px solid ${LINE}`, borderRadius: 8, padding: "6px 10px", fontSize: 12, background: "white", fontFamily: "inherit", color: INK2 }}
              >
                <option value={10}>10개씩</option>
                <option value={20}>20개씩</option>
                <option value={50}>50개씩</option>
              </select>
              <div style={{ display: "flex", border: `1px solid ${LINE}`, borderRadius: 8, overflow: "hidden" }}>
                <button
                  onClick={() => setCols(1)}
                  style={{ padding: "6px 10px", background: cols === 1 ? INK : "white", color: cols === 1 ? "white" : INK3, border: 0, cursor: "pointer", display: "flex", alignItems: "center" }}
                >
                  <ViewListIcon style={{ fontSize: 16 }} />
                </button>
                <button
                  onClick={() => setCols(2)}
                  style={{ padding: "6px 10px", background: cols === 2 ? INK : "white", color: cols === 2 ? "white" : INK3, border: 0, cursor: "pointer", display: "flex", alignItems: "center" }}
                >
                  <GridViewIcon style={{ fontSize: 16 }} />
                </button>
              </div>
            </div>
          </div>
          <div style={{ marginBottom: 14, padding: "12px 14px", borderRadius: 12, background: "#f8fbff", border: `1px solid ${LINE}`, color: INK2, fontSize: 12, lineHeight: 1.6 }}>
            {POLICY_SOURCE_NOTICE}
          </div>

          {/* 목록 */}
          {loading ? (
            <div style={{ display: "flex", justifyContent: "center", padding: "64px 0" }}>
              <CircularProgress size={36} sx={{ color: A }} />
            </div>
          ) : policies.length > 0 ? (
            cols === 1 ? (
              <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
                {policies.map((p) => (
                  <PolicyRow key={p.id} p={p} onNavigate={navigateToPolicyDetail} onBookmark={handleBookmark} />
                ))}
              </div>
            ) : (
              <div style={{ display: "grid", gridTemplateColumns: isMobile ? "1fr" : "1fr 1fr", gap: 12 }}>
                {policies.map((p) => (
                  <PolicyCard key={p.id} p={p} onNavigate={navigateToPolicyDetail} onBookmark={handleBookmark} />
                ))}
              </div>
            )
          ) : (
            <div style={{ textAlign: "center", padding: "64px 24px", background: "white", borderRadius: 16, border: `1px solid ${LINE}` }}>
              <div style={{ fontSize: 40 }}>🔍</div>
              <div style={{ marginTop: 12, fontSize: 15, color: INK2 }}>
                {hasSearch || hasActiveFilters ? "조건에 맞는 정책을 찾지 못했어요" : "검색 결과가 없습니다"}
              </div>
              <div style={{ marginTop: 6, fontSize: 13, color: INK3 }}>
                {hasSearch || hasActiveFilters
                  ? "검색어를 바꾸거나 필터를 줄이면 더 많은 정책을 볼 수 있어요."
                  : "다른 키워드나 조건으로 검색해보세요."}
              </div>
              {(hasSearch || hasActiveFilters) && (
                <div style={{ display: "flex", justifyContent: "center", gap: 10, marginTop: 18, flexWrap: "wrap" }}>
                  {hasSearch && (
                    <button
                      onClick={handleClearSearch}
                      style={{ padding: "10px 14px", borderRadius: 10, border: `1px solid ${LINE}`, background: "white", color: INK2, fontSize: 13, fontWeight: 700, cursor: "pointer" }}
                    >
                      검색어 지우기
                    </button>
                  )}
                  {hasActiveFilters && (
                    <button
                      onClick={handleResetFilter}
                      style={{ padding: "10px 14px", borderRadius: 10, border: 0, background: AS, color: AI, fontSize: 13, fontWeight: 700, cursor: "pointer" }}
                    >
                      필터 초기화
                    </button>
                  )}
                </div>
              )}
            </div>
          )}

          {/* 페이지네이션 */}
          {totalPages > 1 && (
            <div style={{ display: "flex", justifyContent: "center", marginTop: 28, gap: 4 }}>
              <Pagination
                count={totalPages}
                page={page}
                onChange={(_, v) => setPage(v)}
                color="primary"
                sx={{
                  "& .MuiPaginationItem-root": { borderRadius: 2 },
                  "& .Mui-selected": { background: A, color: "white" },
                }}
              />
            </div>
          )}
        </div>
      </div>

      <IncomeCalculatorModal open={incomeCalcOpen} onClose={() => setIncomeCalcOpen(false)} onSelect={(value) => { setIncome(value); setIncomeCalcOpen(false); }} />

      <Snackbar open={toast.open} autoHideDuration={3000} onClose={() => setToast((p) => ({ ...p, open: false }))} anchorOrigin={{ vertical: "bottom", horizontal: "center" }}>
        <Alert severity={toast.severity} onClose={() => setToast((p) => ({ ...p, open: false }))}>{toast.msg}</Alert>
      </Snackbar>
      <FloatingNav />
    </div>
  );
}
