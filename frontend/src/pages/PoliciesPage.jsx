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

// ── 상수 ──────────────────────────────────────────────────────────────────────

const CATEGORIES = [
  { label: "전체", value: "" },
  { label: "주거", value: "주거" },
  { label: "일자리", value: "일자리" },
  { label: "교육·직업훈련", value: "교육·직업훈련" },
  { label: "금융·생활지원", value: "금융·생활지원" },
  { label: "문화·여가", value: "문화·여가" },
  { label: "건강·의료", value: "건강·의료" },
  { label: "가족·돌봄", value: "가족·돌봄" },
  { label: "안전·위기", value: "안전·위기" },
  { label: "참여·기회", value: "참여·기회" },
  { label: "분류없음", value: "기타" },
];

const REGIONS = [
  "전체", "서울", "부산", "대구", "인천", "광주", "대전", "울산",
  "세종", "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주",
];

const DISTRICT_MAP = {
  "전체": [],
  "서울": ["전체","강남구","강동구","강북구","강서구","관악구","광진구","구로구","금천구","노원구","도봉구","동대문구","동작구","마포구","서대문구","서초구","성동구","성북구","송파구","양천구","영등포구","용산구","은평구","종로구","중구","중랑구"],
  "부산": ["전체","강서구","금정구","기장군","남구","동구","동래구","부산진구","북구","사상구","사하구","서구","수영구","연제구","영도구","중구","해운대구"],
  "대구": ["전체","남구","달서구","달성군","동구","북구","서구","수성구","중구"],
  "인천": ["전체","강화군","계양구","남동구","동구","미추홀구","부평구","서구","연수구","옹진군","중구"],
  "광주": ["전체","광산구","남구","동구","북구","서구"],
  "대전": ["전체","대덕구","동구","서구","유성구","중구"],
  "울산": ["전체","남구","동구","북구","울주군","중구"],
  "세종": [],
  "경기": ["전체","가평군","고양시","과천시","광명시","광주시","구리시","군포시","김포시","남양주시","동두천시","부천시","성남시","수원시","시흥시","안산시","안양시","양주시","양평군","여주시","연천군","오산시","용인시","의왕시","의정부시","이천시","파주시","평택시","포천시","하남시","화성시"],
  "강원": ["전체","강릉시","고성군","동해시","삼척시","속초시","양구군","양양군","영월군","원주시","인제군","정선군","철원군","춘천시","태백시","평창군","홍천군","화천군","횡성군"],
  "충북": ["전체","괴산군","단양군","보은군","영동군","옥천군","음성군","제천시","증평군","진천군","청주시","충주시"],
  "충남": ["전체","계룡시","공주시","금산군","논산시","당진시","보령시","부여군","서산시","서천군","아산시","예산군","천안시","청양군","태안군","홍성군"],
  "전북": ["전체","고창군","군산시","김제시","남원시","무주군","부안군","순창군","완주군","익산시","임실군","장수군","전주시","정읍시","진안군"],
  "전남": ["전체","강진군","고흥군","곡성군","광양시","구례군","나주시","담양군","목포시","무안군","보성군","순천시","신안군","여수시","영광군","영암군","완도군","장성군","장흥군","진도군","함평군","해남군","화순군"],
  "경북": ["전체","경산시","경주시","고령군","구미시","군위군","김천시","문경시","봉화군","상주시","성주군","안동시","영덕군","영양군","영주시","영천시","예천군","울릉군","울진군","의성군","청도군","청송군","칠곡군","포항시"],
  "경남": ["전체","거제시","거창군","고성군","김해시","남해군","밀양시","사천시","산청군","양산시","의령군","진주시","창녕군","창원시","통영시","하동군","함안군","함양군","합천군"],
  "제주": ["전체","서귀포시","제주시"],
};

const INCOME_ROWS = [
  { value: "1", label: "1~2분위 (하위 20%)" },
  { value: "3", label: "3~4분위 (하위 40%)" },
  { value: "5", label: "5~6분위 (중간 40%)" },
  { value: "7", label: "7~8분위 (상위 40%)" },
  { value: "9", label: "9~10분위 (상위 20%)" },
];

const TARGET_GROUPS = [
  { label: "장애인", value: "장애인" },
  { label: "한부모·조손", value: "한부모·조손" },
  { label: "다문화·탈북민", value: "다문화·탈북민" },
  { label: "보훈대상자", value: "보훈대상자" },
  { label: "다자녀", value: "다자녀" },
];

const SOURCE_OPTIONS = ["전체", "온통청년", "복지로 중앙", "복지로 지자체", "Gov24"];
const SOURCE_TYPE_MAP = {
  "온통청년": "YOUTH",
  "복지로 중앙": "BOKJIRO_CENTRAL",
  "복지로 지자체": "BOKJIRO_LOCAL",
  "Gov24": "GOV24",
};
const SOURCE_LABEL_BY_TYPE = Object.fromEntries(
  Object.entries(SOURCE_TYPE_MAP).map(([label, type]) => [type, label])
);

const SORT_MAP = { relevance: "RELEVANCE", views: "VIEWS", latest: "LATEST", deadline: "DEADLINE" };

const TRENDING = ["월세 지원", "국민취업제도", "도약계좌", "창업캠프", "자격증 응시료", "대학생 생활안정"];

// ── 디자인 상수 ───────────────────────────────────────────────────────────────
const A = "#2563eb";
const A7 = "#1d4ed8";
const AS = "#e8efff";
const AI = "#1e3a8a";
const INK = "#11131a";
const INK2 = "#4a4f5c";
const INK3 = "#8b91a0";
const LINE = "#e7e9ef";
const LINE2 = "#f0f2f7";
const WARN = "#ef4444";
const OK = "#047857";
const OK_BG = "#ecfdf5";

// ── 헬퍼 ──────────────────────────────────────────────────────────────────────

const statusLabel = (status) => {
  if (status === "ACTIVE") return "진행중";
  if (status === "UPCOMING") return "예정";
  if (status === "CLOSED") return "종료";
  return "상시";
};

const formatDday = (dateText, status) => {
  if (status === "CLOSED") return "종료";
  if (!dateText) return status === "UPCOMING" ? "예정" : "상시/문의";
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const endDate = new Date(`${dateText}T00:00:00`);
  if (Number.isNaN(endDate.getTime())) return "상시/문의";
  const diff = Math.ceil((endDate - today) / 86400000);
  if (diff < 0) return "종료";
  if (diff === 0) return "D-Day";
  return `D-${diff}`;
};

const mapPolicySummary = (policy) => ({
  id: policy.id,
  title: policy.title,
  category: policy.unifiedCategory || "기타",
  dday: formatDday(policy.applyEndDate, policy.status) || statusLabel(policy.status),
  source: policy.hostOrg || policy.sido || policy.applyMethodName || statusLabel(policy.status),
  orgName: policy.hostOrg || policy.operatingOrg || "",
  regionText: policy.sido || "",
  summary: policy.description || "정책 설명 정보가 없습니다.",
  bookmarked: Boolean(policy.bookmarked),
  apiViewCount: policy.apiViewCount ?? 0,
  viewCount: policy.viewCount ?? 0,
  createdAt: policy.createdAt ?? null,
  registeredAt: policy.registeredAt ?? null,
  lastModifiedAt: policy.lastModifiedAt ?? null,
});

const resolveInitialSort = (paramsSearch, paramsSort) => {
  const hasSearch = Boolean(paramsSearch?.trim());
  const allowed = hasSearch ? ["relevance", "views", "latest", "deadline"] : ["views", "latest", "deadline"];
  if (allowed.includes(paramsSort)) return paramsSort;
  return hasSearch ? "relevance" : "latest";
};

const normalizeSourceTypeParam = (rawSourceType) => {
  if (!rawSourceType?.trim()) return "전체";
  const trimmed = rawSourceType.trim();
  if (SOURCE_TYPE_MAP[trimmed]) return trimmed;
  const normalized = trimmed.toUpperCase();
  return SOURCE_LABEL_BY_TYPE[normalized] || "전체";
};

const serializeSourceTypeParam = (sourceLabel) => {
  if (!sourceLabel || sourceLabel === "전체") return null;
  return SOURCE_TYPE_MAP[sourceLabel] ?? null;
};

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

// ── 정책 카드 ─────────────────────────────────────────────────────────────────

function PolicyRow({ p, onNavigate, onBookmark }) {
  const ds = ddayStyle(p.dday);
  const isUrgent = p.dday.startsWith("D-") && parseInt(p.dday.replace("D-", "")) <= 14;
  return (
    <div
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
        <div style={{ display: "flex", gap: 14, marginTop: 10, fontSize: 12, color: INK3, flexWrap: "wrap" }}>
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
      {p.source && <div style={{ fontSize: 12, color: INK3, marginTop: 8 }}>🏢 {p.source}</div>}
    </div>
  );
}

// ── 메인 컴포넌트 ─────────────────────────────────────────────────────────────

export default function PoliciesPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const { isLoggedIn, filterSettings } = useAuthStore();
  const defaultStatusFilter = filterSettings?.includeExpired ? "전부표기" : "신청가능";

  const [search, setSearch] = useState(searchParams.get("search") || "");
  const [selectedCat, setSelectedCat] = useState(searchParams.get("category") || "");
  const [region, setRegion] = useState(searchParams.get("region") || "전체");
  const [subRegion, setSubRegion] = useState(searchParams.get("subRegion") || "전체");
  const [income, setIncome] = useState(searchParams.get("income") || "전체");
  const [targetGroup, setTargetGroup] = useState(searchParams.get("targetGroup") || "");
  const [sourceType, setSourceType] = useState(normalizeSourceTypeParam(searchParams.get("sourceType")));
  const [statusFilter, setStatusFilter] = useState(searchParams.get("statusFilter") || defaultStatusFilter);
  const [sort, setSort] = useState(resolveInitialSort(searchParams.get("search") || "", searchParams.get("sort")));
  const [pageSize, setPageSize] = useState(Number(searchParams.get("pageSize")) || 10);
  const [cols, setCols] = useState(1);
  const [page, setPage] = useState(Number(searchParams.get("page")) || 1);
  const [policies, setPolicies] = useState([]);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(false);
  const [policiesLoaded, setPoliciesLoaded] = useState(false);
  const [totalCount, setTotalCount] = useState(0);
  const [toast, setToast] = useState({ open: false, msg: "", severity: "info" });
  const [incomeCalcOpen, setIncomeCalcOpen] = useState(false);
  const bookmarkActionKeyRef = useRef(null);

  // ── URL 파라미터 동기화 ──────────────────────────────────────────────────────
  useEffect(() => {
    const params = {};
    if (search) params.search = search;
    if (selectedCat) params.category = selectedCat;
    if (region !== "전체") params.region = region;
    if (subRegion !== "전체") params.subRegion = subRegion;
    if (income !== "전체") params.income = income;
    if (targetGroup) params.targetGroup = targetGroup;
    const sourceTypeParam = serializeSourceTypeParam(sourceType);
    if (sourceTypeParam) params.sourceType = sourceTypeParam;
    if (statusFilter !== defaultStatusFilter) params.statusFilter = statusFilter;
    const defaultSort = search.trim() ? "relevance" : "latest";
    if (sort !== defaultSort) params.sort = sort;
    if (page !== 1) params.page = String(page);
    if (pageSize !== 10) params.pageSize = String(pageSize);
    setSearchParams(params, { replace: true, state: location.state });
  }, [defaultStatusFilter, income, location.state, page, pageSize, region, search, selectedCat, setSearchParams, sort, sourceType, statusFilter, subRegion, targetGroup]);

  useEffect(() => {
    const nextSearch = searchParams.get("search") || "";
    const nextSelectedCat = searchParams.get("category") || "";
    const nextRegion = searchParams.get("region") || "전체";
    const nextSubRegion = searchParams.get("subRegion") || "전체";
    const nextIncome = searchParams.get("income") || "전체";
    const nextTargetGroup = searchParams.get("targetGroup") || "";
    const nextSourceType = normalizeSourceTypeParam(searchParams.get("sourceType"));
    const nextStatusFilter = searchParams.get("statusFilter") || defaultStatusFilter;
    const nextSort = resolveInitialSort(nextSearch, searchParams.get("sort"));
    const nextPageSize = Number(searchParams.get("pageSize")) || 10;
    const nextPage = Number(searchParams.get("page")) || 1;

    setSearch((prev) => (prev === nextSearch ? prev : nextSearch));
    setSelectedCat((prev) => (prev === nextSelectedCat ? prev : nextSelectedCat));
    setRegion((prev) => (prev === nextRegion ? prev : nextRegion));
    setSubRegion((prev) => (prev === nextSubRegion ? prev : nextSubRegion));
    setIncome((prev) => (prev === nextIncome ? prev : nextIncome));
    setTargetGroup((prev) => (prev === nextTargetGroup ? prev : nextTargetGroup));
    setSourceType((prev) => (prev === nextSourceType ? prev : nextSourceType));
    setStatusFilter((prev) => (prev === nextStatusFilter ? prev : nextStatusFilter));
    setSort((prev) => (prev === nextSort ? prev : nextSort));
    setPageSize((prev) => (prev === nextPageSize ? prev : nextPageSize));
    setPage((prev) => (prev === nextPage ? prev : nextPage));
  }, [defaultStatusFilter, searchParams]);

  useEffect(() => {
    if (!search.trim() && sort === "relevance") { setSort("latest"); return; }
    if (search.trim() && !searchParams.get("sort") && sort !== "relevance") setSort("relevance");
  }, [search]); // eslint-disable-line

  // ── 정책 fetch ───────────────────────────────────────────────────────────────
  useEffect(() => {
    const controller = new AbortController();
    const fetchPolicies = async () => {
      setLoading(true);
      setPoliciesLoaded(false);
      try {
        const commonParams = {
          category: selectedCat || undefined,
          sido: region === "전체" ? undefined : region,
          sgg: subRegion === "전체" ? undefined : subRegion,
          sourceType: sourceType === "전체" ? undefined : SOURCE_TYPE_MAP[sourceType],
          sort: search.trim() ? (SORT_MAP[sort] ?? "RELEVANCE") : (sort === "relevance" ? "LATEST" : (SORT_MAP[sort] ?? "LATEST")),
          incomeLevel: income === "전체" ? undefined : Number(income),
          targetGroup: targetGroup || undefined,
          page: page - 1,
          size: pageSize,
        };
        const STATUS_FILTER_MAP = { "신청가능": "ACTIVE_ONLY", "마감": "EXPIRED_ONLY", "전부표기": "ALL" };
        const apiStatusFilter = STATUS_FILTER_MAP[statusFilter] ?? "ACTIVE_ONLY";

        const endpoint = search.trim() ? "/api/policies/search" : "/api/policies";
        const reqParams = search.trim() ? { ...commonParams, keyword: search.trim(), statusFilter: apiStatusFilter } : { ...commonParams, statusFilter: apiStatusFilter };
        const { data } = await api.get(endpoint, { params: reqParams, signal: controller.signal });
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
  }, [isLoggedIn, statusFilter, page, pageSize, region, search, selectedCat, sort, sourceType, subRegion, income, targetGroup]);

  // ── 핸들러 ───────────────────────────────────────────────────────────────────
  const handleBookmark = async (id, event) => {
    event.stopPropagation();
    if (!isLoggedIn) {
      navigate("/login", {
        state: {
          from: {
            pathname: location.pathname,
            search: location.search,
            state: location.state,
          },
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
    setSelectedCat(""); setRegion("전체"); setSubRegion("전체"); setIncome("전체");
    setTargetGroup(""); setSourceType("전체"); setStatusFilter(defaultStatusFilter);
    setSort(search.trim() ? "relevance" : "latest"); setPage(1);
  };

  const handleSearch = () => {
    if (search.trim()) setSort("relevance");
    setPage(1);
  };
  const chatFromTarget = location.state?.chatFrom?.pathname === "/chat"
    ? location.state.chatFrom
    : location.state?.from?.pathname === "/chat"
      ? location.state.from
      : undefined;
  const navigateToPolicyDetail = (policyId) => {
    navigate(`/policies/${policyId}`, {
      state: {
        from: {
          pathname: location.pathname,
          search: location.search,
        },
        chatFrom: chatFromTarget,
      },
    });
  };

  // ── 활성 필터 칩 목록 ────────────────────────────────────────────────────────
  const activeFilters = [
    selectedCat && { key: "cat", label: CATEGORIES.find(c => c.value === selectedCat)?.label || selectedCat, clear: () => setSelectedCat("") },
    region !== "전체" && { key: "region", label: subRegion !== "전체" ? `${region} ${subRegion}` : region, clear: () => { setRegion("전체"); setSubRegion("전체"); } },
    income !== "전체" && { key: "income", label: INCOME_ROWS.find(r => r.value === income)?.label, clear: () => setIncome("전체") },
    targetGroup && { key: "tg", label: targetGroup, clear: () => setTargetGroup("") },
    sourceType !== "전체" && { key: "src", label: sourceType, clear: () => setSourceType("전체") },
    statusFilter !== defaultStatusFilter && { key: "status", label: statusFilter, clear: () => setStatusFilter(defaultStatusFilter) },
  ].filter(Boolean);

  useEffect(() => {
    const postLoginAction = location.state?.postLoginAction;
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
      const nextState = { ...(location.state ?? {}) };
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
      <div style={{ background: "white", borderBottom: `1px solid ${LINE}`, padding: "32px 24px 24px" }}>
        <div style={{ maxWidth: 1240, margin: "0 auto" }}>
          <div style={{ fontSize: 26, fontWeight: 800, letterSpacing: "-0.02em", color: INK }}>청년을 위한 복지정책을 찾아드려요</div>
          <div style={{ fontSize: 14, color: INK3, marginTop: 6 }}>
            {totalCount > 0 ? `${totalCount.toLocaleString()}개 정책 중에서 내 조건에 맞는 정책만 골라보세요.` : "조건에 맞는 정책을 검색해보세요."}
          </div>
          <div style={{ marginTop: 20, display: "flex", gap: 10, background: "white", border: `1px solid ${LINE}`, borderRadius: 14, padding: 8, boxShadow: "0 2px 8px rgba(0,0,0,0.04)" }}>
            <div style={{ flex: 1, display: "flex", alignItems: "center", gap: 10, padding: "0 12px" }}>
              <SearchIcon style={{ color: INK3, fontSize: 20 }} />
              <input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                onKeyDown={(e) => { if (e.key === "Enter") handleSearch(); }}
                placeholder="정책명, 키워드를 검색해보세요 (예: 월세, 창업)"
                style={{ border: 0, outline: 0, flex: 1, fontSize: 15, padding: "12px 0", background: "transparent", fontFamily: "inherit", color: INK }}
              />
            </div>
            <button
              onClick={handleSearch}
              style={{ padding: "0 22px", background: A, color: "white", border: 0, borderRadius: 10, fontSize: 14, fontWeight: 700, cursor: "pointer" }}
            >
              검색
            </button>
          </div>
          {/* 인기 검색어 */}
          <div style={{ marginTop: 12, display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            <span style={{ fontSize: 12, color: INK3, fontWeight: 700 }}>인기 검색어</span>
            {TRENDING.map((t, i) => (
              <button
                key={i}
                onClick={() => { setSearch(t); setTimeout(handleSearch, 0); }}
                style={{ fontSize: 12, color: INK2, padding: "4px 10px", background: "white", border: `1px solid ${LINE}`, borderRadius: 99, cursor: "pointer" }}
              >
                <span style={{ color: A, fontWeight: 700, marginRight: 4 }}>{i + 1}</span>{t}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* 콘텐츠 영역 */}
      <div style={{ maxWidth: 1240, margin: "0 auto", padding: "28px 24px 80px", display: "grid", gridTemplateColumns: "280px 1fr", gap: 28, alignItems: "flex-start" }}>

        {/* ── 필터 사이드바 ── */}
        <aside style={{ background: "white", border: `1px solid ${LINE}`, borderRadius: 16, padding: "6px 20px 18px", position: "sticky", top: 80 }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "14px 0 8px", borderBottom: `1px solid ${LINE2}` }}>
            <span style={{ fontSize: 15, fontWeight: 800, color: INK }}>상세 필터</span>
            <button onClick={handleResetFilter} style={{ background: "none", border: "none", fontSize: 12, color: INK3, cursor: "pointer" }}>초기화</button>
          </div>

          {/* 카테고리 */}
          <FilterSection title="카테고리">
            <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
              {CATEGORIES.map((c) => (
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
                onChange={(e) => { setRegion(e.target.value); setSubRegion("전체"); setPage(1); }}
                style={{ width: "100%", padding: "9px 12px", border: `1px solid ${LINE}`, borderRadius: 8, fontSize: 13, background: "white", fontFamily: "inherit", color: INK }}
              >
                {REGIONS.map((r) => <option key={r} value={r}>{r}</option>)}
              </select>
              {(DISTRICT_MAP[region]?.length ?? 0) > 0 && (
                <select
                  value={subRegion}
                  onChange={(e) => { setSubRegion(e.target.value); setPage(1); }}
                  style={{ width: "100%", padding: "9px 12px", border: `1px solid ${LINE}`, borderRadius: 8, fontSize: 13, background: "white", fontFamily: "inherit", color: INK }}
                >
                  {DISTRICT_MAP[region].map((d) => <option key={d} value={d}>{d}</option>)}
                </select>
              )}
            </div>
          </FilterSection>

          {/* 소득수준 */}
          <FilterSection title="소득수준" defaultOpen={false}>
            <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
              <RadioItem label="전체" checked={income === "전체"} onChange={() => setIncome("전체")} />
              {INCOME_ROWS.map((r) => (
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
              {TARGET_GROUPS.map((tg) => {
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
              {SOURCE_OPTIONS.map((source) => (
                <RadioItem
                  key={source}
                  label={source}
                  checked={sourceType === source}
                  onChange={() => { setSourceType(source); setPage(1); }}
                />
              ))}
            </div>
          </FilterSection>

          <button
            onClick={handleApplyFilter}
            style={{ width: "100%", marginTop: 16, padding: "12px 0", background: A, color: "white", border: 0, borderRadius: 10, fontSize: 14, fontWeight: 700, cursor: "pointer" }}
          >
            {totalCount > 0 ? `${totalCount.toLocaleString()}개 정책 보기` : "정책 보기"}
          </button>
        </aside>

        {/* ── 결과 영역 ── */}
        <div>
          {/* 활성 필터 칩 */}
          {activeFilters.length > 0 && (
            <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 14, flexWrap: "wrap" }}>
              <span style={{ fontSize: 12, color: INK3 }}>선택된 조건:</span>
              {activeFilters.map((f) => (
                <span
                  key={f.key}
                  style={{ display: "inline-flex", alignItems: "center", gap: 6, fontSize: 12, padding: "5px 10px 5px 12px", background: AS, color: AI, borderRadius: 99, fontWeight: 600 }}
                >
                  {f.label}
                  <button onClick={f.clear} style={{ background: "none", border: "none", cursor: "pointer", color: AI, fontSize: 14, padding: 0, lineHeight: 1 }}>×</button>
                </span>
              ))}
              <button onClick={handleResetFilter} style={{ fontSize: 12, color: A, fontWeight: 600, background: "none", border: "none", cursor: "pointer" }}>
                모두 해제
              </button>
            </div>
          )}

          {/* 결과 수 + 정렬 + 뷰 토글 */}
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
            <div style={{ fontSize: 13, color: INK2 }}>
              총 <strong style={{ color: INK }}>{totalCount.toLocaleString()}</strong>개의 정책
              {loading && <span style={{ marginLeft: 8, color: INK3 }}>· 로딩 중</span>}
            </div>
            <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
              <select
                value={sort}
                onChange={(e) => { setSort(e.target.value); setPage(1); }}
                style={{ border: `1px solid ${LINE}`, borderRadius: 8, padding: "6px 10px", fontSize: 12, background: "white", fontFamily: "inherit", color: INK2 }}
              >
                {search.trim() && <option value="relevance">관련도순</option>}
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
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                {policies.map((p) => (
                  <PolicyCard key={p.id} p={p} onNavigate={navigateToPolicyDetail} onBookmark={handleBookmark} />
                ))}
              </div>
            )
          ) : (
            <div style={{ textAlign: "center", padding: "64px 24px", background: "white", borderRadius: 16, border: `1px solid ${LINE}` }}>
              <div style={{ fontSize: 40 }}>🔍</div>
              <div style={{ marginTop: 12, fontSize: 15, color: INK2 }}>검색 결과가 없습니다</div>
              <div style={{ marginTop: 6, fontSize: 13, color: INK3 }}>다른 키워드나 조건으로 검색해보세요</div>
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
