import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  Box, Container, Typography, Button,
  Card, CardContent, Chip, Pagination,
  Select, MenuItem, FormControl, IconButton,
  Snackbar, Alert, CircularProgress,
  Collapse, Radio, RadioGroup, FormControlLabel,
} from "@mui/material";
import SearchIcon from "@mui/icons-material/Search";
import BookmarkBorderIcon from "@mui/icons-material/BookmarkBorder";
import BookmarkIcon from "@mui/icons-material/Bookmark";
import ViewListIcon from "@mui/icons-material/ViewList";
import GridViewIcon from "@mui/icons-material/GridView";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import ExpandLessIcon from "@mui/icons-material/ExpandLess";
import Header from "../components/Header";
import FloatingNav from "../components/FloatingNav";
import IncomeCalculatorModal from "../components/IncomeCalculatorModal";
import api from "../lib/axios";
import { useAuthStore } from "../store/authStore";

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
  // 임시: 미분류(기타) 정책 점검용. 복지로 로컬 카테고리 매핑 정비 후 제거 예정
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
  { value: "1", left: "1~2분위 (하위 20%)", right: "기초생활수급자" },
  { value: "3", left: "3~4분위 (하위 40%)", right: "차상위계층" },
  { value: "5", left: "5~6분위 (중간)", right: "소득 하위 50% 이하" },
  { value: "7", left: "7~8분위 (상위 40%)", right: "소득 중간 (50~100%)" },
  { value: "9", left: "9~10분위 (상위 20%)", right: "소득 상위 (100% 초과)" },
];

const EMPLOY_OPTIONS = ["전체", "재직중", "구직중", "학생", "기타"];

const SORT_MAP = { relevance: "RELEVANCE", views: "VIEWS", latest: "LATEST", deadline: "DEADLINE" };

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

const ddayColor = (dday) => {
  if (dday === "종료") return "default";
  if (dday === "상시/문의" || dday === "진행중") return "success";
  if (dday === "예정") return "info";
  if (dday === "D-Day") return "error";
  const n = Number.parseInt(String(dday).replace("D-", ""), 10);
  return Number.isNaN(n) ? "default" : n <= 14 ? "error" : "primary";
};

const mapPolicySummary = (policy) => ({
  id: policy.id,
  title: policy.title,
  category: policy.unifiedCategory || "기타",
  dday: formatDday(policy.applyEndDate, policy.status) || statusLabel(policy.status),
  // source 우선순위: 주관기관 → 지역(복지로 지자체만 sido 있음) → 신청방법
  source: policy.hostOrg || policy.sido || policy.applyMethodName || statusLabel(policy.status),
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
  const allowed = hasSearch
    ? ["relevance", "views", "latest", "deadline"]
    : ["views", "latest", "deadline"];
  if (allowed.includes(paramsSort)) return paramsSort;
  return hasSearch ? "relevance" : "latest";
};

export default function PoliciesPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { isLoggedIn } = useAuthStore();

  const [search, setSearch] = useState(searchParams.get("search") || "");
  const [selectedCat, setSelectedCat] = useState(searchParams.get("category") || "");
  const [filterOpen, setFilterOpen] = useState(false);
  const [region, setRegion] = useState(searchParams.get("region") || "전체");
  const [subRegion, setSubRegion] = useState(searchParams.get("subRegion") || "전체");
  const [income, setIncome] = useState(searchParams.get("income") || "전체");
  const [incomeOpen, setIncomeOpen] = useState(false);
  const [employ, setEmploy] = useState(searchParams.get("employ") || "전체");
  const [sourceType, setSourceType] = useState(searchParams.get("sourceType") || "전체");
  const [statusFilter, setStatusFilter] = useState(searchParams.get("statusFilter") || "신청가능");
  const [sort, setSort] = useState(resolveInitialSort(searchParams.get("search") || "", searchParams.get("sort")));
  const [pageSize, setPageSize] = useState(Number(searchParams.get("pageSize")) || 10);
  const [cols, setCols] = useState(1);
  const [page, setPage] = useState(Number(searchParams.get("page")) || 1);
  const [policies, setPolicies] = useState([]);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(false);
  const [totalCount, setTotalCount] = useState(0);
  const [toast, setToast] = useState({ open: false, msg: "", severity: "info" });
  const [incomeCalcOpen, setIncomeCalcOpen] = useState(false);

  useEffect(() => {
    const params = {};
    if (search) params.search = search;
    if (selectedCat) params.category = selectedCat;
    if (region !== "전체") params.region = region;
    if (subRegion !== "전체") params.subRegion = subRegion;
    if (income !== "전체") params.income = income;
    if (employ !== "전체") params.employ = employ;
    if (sourceType !== "전체") params.sourceType = sourceType;
    if (statusFilter !== "신청가능") params.statusFilter = statusFilter;
    const defaultSort = search.trim() ? "relevance" : "latest";
    if (sort !== defaultSort) params.sort = sort;
    if (page !== 1) params.page = String(page);
    if (pageSize !== 10) params.pageSize = String(pageSize);
    setSearchParams(params, { replace: true });
  }, [search, selectedCat, region, subRegion, income, employ, sourceType, statusFilter, sort, page, pageSize, setSearchParams]);

  useEffect(() => {
    const defaultSort = search.trim() ? "relevance" : "latest";
    if (!search.trim() && sort === "relevance") {
      setSort("latest");
      return;
    }
    if (search.trim() && !searchParams.get("sort") && sort !== defaultSort) {
      setSort(defaultSort);
    }
  }, [search, sort, searchParams]);

  useEffect(() => {
    const controller = new AbortController();

    const fetchPolicies = async () => {
      setLoading(true);
      try {
        const SOURCE_TYPE_MAP = {
          "온통청년": "YOUTH",
          "복지로 중앙": "BOKJIRO_CENTRAL",
          "복지로 지자체": "BOKJIRO_LOCAL",
        };
        const commonParams = {
          category: selectedCat || undefined,
          sido: region === "전체" ? undefined : region,
          sgg: subRegion === "전체" ? undefined : subRegion,
          sourceType: sourceType === "전체" ? undefined : SOURCE_TYPE_MAP[sourceType],
          sort: search.trim()
            ? (SORT_MAP[sort] ?? "RELEVANCE")
            : (sort === "relevance" ? "LATEST" : (SORT_MAP[sort] ?? "LATEST")),
          page: page - 1,
          size: pageSize,
        };

        // "마감"(EXPIRED_ONLY)은 DB status=CLOSED뿐 아니라 온통청년처럼 applyEndDate만 지난 정책도 포함
        const STATUS_FILTER_MAP = { "신청가능": "ACTIVE_ONLY", "마감": "EXPIRED_ONLY", "전부표기": "ALL" };
        const apiStatusFilter = STATUS_FILTER_MAP[statusFilter] ?? "ACTIVE_ONLY";

        if (search.trim()) {
          const { data } = await api.get("/api/policies/search", {
            params: { ...commonParams, keyword: search.trim(), statusFilter: apiStatusFilter },
            signal: controller.signal,
          });
          const pageData = data.data ?? {};
          setPolicies((pageData.content ?? []).map(mapPolicySummary));
          setTotalCount(pageData.totalElements ?? 0);
          setTotalPages(Math.max(pageData.totalPages ?? 1, 1));
          return;
        }

        const { data } = await api.get("/api/policies", {
          params: { ...commonParams, statusFilter: apiStatusFilter },
          signal: controller.signal,
        });
        const pageData = data.data ?? {};
        setPolicies((pageData.content ?? []).map(mapPolicySummary));
        setTotalCount(pageData.totalElements ?? 0);
        setTotalPages(Math.max(pageData.totalPages ?? 1, 1));
      } catch (error) {
        if (error.name === "CanceledError" || error.code === "ERR_CANCELED") return;
        setPolicies([]);
        setTotalPages(1);
        setTotalCount(0);
        setToast({ open: true, msg: "정책 목록을 불러오지 못했습니다", severity: "error" });
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    };

    fetchPolicies();
    return () => controller.abort();
  }, [statusFilter, page, pageSize, region, search, selectedCat, sort, sourceType, subRegion]);

  const handleBookmark = async (id, event) => {
    event.stopPropagation();
    if (!isLoggedIn) {
      setToast({ open: true, msg: "로그인 후 이용 가능해요", severity: "info" });
      return;
    }
    try {
      await api.post(`/api/policies/${id}/bookmark`);
      setPolicies((prev) =>
        prev.map((p) => (p.id === id ? { ...p, bookmarked: !p.bookmarked } : p))
      );
    } catch {
      setToast({ open: true, msg: "북마크 처리에 실패했습니다", severity: "error" });
    }
  };

  const handleCategorySelect = (val) => {
    setSelectedCat(val);
    setPage(1);
  };

  const handleApplyFilter = () => {
    setPage(1);
    setFilterOpen(false);
  };

  const handleResetFilter = () => {
    setSelectedCat("");
    setRegion("전체");
    setSubRegion("전체");
    setIncome("전체");
    setEmploy("전체");
    setSourceType("전체");
    setStatusFilter("신청가능");
    setSort(search.trim() ? "relevance" : "latest");
    setPage(1);
  };

  return (
    <Box sx={{ minHeight: "100vh", bgcolor: "background.default" }}>
      <Header />

      {/* 히어로 검색 + 필터 */}
      <Box sx={{ background: "linear-gradient(135deg, #016070 0%, #00A896 100%)", pt: 5, pb: 3, px: 2, textAlign: "center" }}>
        <Typography variant="h5" fontWeight={700} color="white" mb={2}>
          청년을 위한 복지정책을 찾아드려요
        </Typography>

        {/* 검색창 + 카테고리 — 하나의 흰 박스 */}
        <Box sx={{ maxWidth: 640, mx: "auto", bgcolor: "white", borderRadius: 2, overflow: "hidden", boxShadow: 3 }}>
          <Box sx={{ display: "flex", alignItems: "center", height: 48, borderBottom: "1px solid #f0f0f0" }}>
            <SearchIcon color="action" sx={{ ml: 1.5, flexShrink: 0 }} />
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  if (search.trim() && !searchParams.get("sort")) {
                    setSort("relevance");
                  }
                  setPage(1);
                }
              }}
              placeholder="검색어를 입력하세요"
              style={{ flex: 1, border: "none", outline: "none", fontSize: 14, padding: "0 12px", height: "100%", background: "transparent" }}
            />
            <Button
              variant="contained"
              disableElevation
              onClick={() => {
                if (search.trim() && !searchParams.get("sort")) {
                  setSort("relevance");
                }
                setPage(1);
              }}
              sx={{ borderRadius: 0, px: 3, height: "100%", fontSize: 14, flexShrink: 0 }}
            >
              검색
            </Button>
          </Box>
          <Box sx={{ px: 1.5, py: 0.75, bgcolor: "#f5f5f5", borderTop: "1px solid #e0e0e0", textAlign: "left" }}>
            <Button
              size="small"
              onClick={() => setFilterOpen((v) => !v)}
              endIcon={filterOpen ? <ExpandLessIcon /> : <ExpandMoreIcon />}
              sx={{ color: selectedCat ? "primary.main" : "text.secondary", fontWeight: selectedCat ? 700 : 400, fontSize: 13 }}
            >
              상세조건 선택
              {selectedCat && (
                <Chip
                  label={CATEGORIES.find((c) => c.value === selectedCat)?.label}
                  size="small"
                  color="primary"
                  sx={{ ml: 1, height: 18, fontSize: 11 }}
                />
              )}
            </Button>
          </Box>
        </Box>

        {/* 필터 패널 */}
        <Collapse in={filterOpen}>
          <Box sx={{ maxWidth: 640, mx: "auto", mt: 1, bgcolor: "white", borderRadius: 2, overflow: "hidden", boxShadow: 3, textAlign: "left" }}>
            <Box sx={{ display: "flex" }}>

              {/* 왼쪽: 카테고리 */}
              <Box sx={{ width: 160, borderRight: "1px solid #f0f0f0", py: 1, flexShrink: 0 }}>
                <RadioGroup value={selectedCat} onChange={(e) => handleCategorySelect(e.target.value)}>
                  {CATEGORIES.map((cat) => (
                    <FormControlLabel
                      key={cat.value}
                      value={cat.value}
                      control={<Radio size="small" sx={{ py: 0.3 }} />}
                      label={cat.label}
                      sx={{ mx: 0, px: 1.5, "& .MuiFormControlLabel-label": { fontSize: 13 } }}
                    />
                  ))}
                </RadioGroup>
              </Box>

              {/* 오른쪽: 상세 필터 */}
              <Box sx={{ flex: 1, p: 2, display: "flex", flexDirection: "column", gap: 1.5, overflowY: "auto", maxHeight: 440 }}>

                {/* 지역 */}
                <Box>
                  <Typography variant="caption" fontWeight={700} mb={0.5} display="block">지역</Typography>
                  <Box sx={{ display: "flex", gap: 1 }}>
                    <FormControl size="small" sx={{ flex: 1 }}>
                      <Select value={region} onChange={(e) => { const r = e.target.value; setRegion(r); setSubRegion("전체"); setSort("latest"); setPage(1); }}>
                        {REGIONS.map((r) => <MenuItem key={r} value={r}>{r}</MenuItem>)}
                      </Select>
                    </FormControl>
                    {(DISTRICT_MAP[region]?.length ?? 0) > 0 && (
                      <FormControl size="small" sx={{ flex: 1 }}>
                        <Select value={subRegion} onChange={(e) => setSubRegion(e.target.value)}>
                          {DISTRICT_MAP[region].map((d) => <MenuItem key={d} value={d}>{d}</MenuItem>)}
                        </Select>
                      </FormControl>
                    )}
                  </Box>
                </Box>

                {/* 소득수준 */}
                <Box>
                  <Typography variant="caption" fontWeight={700} mb={0.5} display="block">소득수준</Typography>
                  <Box
                    onClick={() => setIncomeOpen((v) => !v)}
                    sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", border: "1px solid #c4c4c4", borderRadius: 1, px: 1.5, py: 0.75, cursor: "pointer", bgcolor: "white", "&:hover": { borderColor: "text.primary" } }}
                  >
                    <Typography variant="caption" color={income === "전체" ? "text.secondary" : "text.primary"}>
                      {income === "전체" ? "전체"
                        : income === "1" ? "기초생활수급자"
                        : income === "3" ? "차상위계층"
                        : income === "5" ? "소득 하위 50% 이하"
                        : income === "7" ? "소득 중간 (50~100%)"
                        : "소득 상위 (100% 초과)"}
                    </Typography>
                    {incomeOpen ? <ExpandLessIcon sx={{ fontSize: 18, color: "text.secondary" }} /> : <ExpandMoreIcon sx={{ fontSize: 18, color: "text.secondary" }} />}
                  </Box>
                  <Collapse in={incomeOpen}>
                    <Box sx={{ border: "1px solid #e0e0e0", borderTop: "none", borderRadius: "0 0 4px 4px", overflow: "hidden" }}>
                      <Box sx={{ display: "grid", gridTemplateColumns: "1fr 1fr", borderBottom: "1px solid #e0e0e0" }}>
                        <Typography variant="caption" sx={{ p: 0.75, bgcolor: "#f9f9f9", fontWeight: 700, textAlign: "center" }}>분위 기준</Typography>
                        <Typography variant="caption" sx={{ p: 0.75, bgcolor: "#f9f9f9", fontWeight: 700, textAlign: "center", borderLeft: "1px solid #e0e0e0" }}>보기 쉬운 기준</Typography>
                      </Box>
                      <Box
                        onClick={(e) => { e.stopPropagation(); setIncome("전체"); setIncomeOpen(false); }}
                        sx={{ display: "flex", alignItems: "center", p: 0.75, gap: 0.5, cursor: "pointer", bgcolor: income === "전체" ? "rgba(2,128,144,0.1)" : "transparent", "&:hover": { bgcolor: "#f5fafa" }, borderBottom: "1px solid #f0f0f0" }}
                      >
                        <Radio size="small" checked={income === "전체"} readOnly sx={{ p: 0.5 }} />
                        <Typography variant="caption">전체</Typography>
                      </Box>
                      {INCOME_ROWS.map((row) => (
                        <Box
                          key={row.value}
                          onClick={(e) => { e.stopPropagation(); setIncome(row.value); setIncomeOpen(false); }}
                          sx={{ display: "grid", gridTemplateColumns: "1fr 1fr", cursor: "pointer", bgcolor: income === row.value ? "rgba(2,128,144,0.1)" : "transparent", "&:hover": { bgcolor: "#f5fafa" }, borderBottom: "1px solid #f0f0f0" }}
                        >
                          <Box sx={{ display: "flex", alignItems: "center", p: 0.75, gap: 0.5 }}>
                            <Radio size="small" checked={income === row.value} readOnly sx={{ p: 0.5 }} />
                            <Typography variant="caption">{row.left}</Typography>
                          </Box>
                          <Box sx={{ display: "flex", alignItems: "center", p: 0.75, gap: 0.5, borderLeft: "1px solid #e0e0e0" }}>
                            <Radio size="small" checked={income === row.value} readOnly sx={{ p: 0.5 }} />
                            <Typography variant="caption">{row.right}</Typography>
                          </Box>
                        </Box>
                      ))}
                    </Box>
                  </Collapse>
                  <Typography
                    variant="caption"
                    color="primary"
                    sx={{ mt: 0.75, display: "block", cursor: "pointer", "&:hover": { textDecoration: "underline" } }}
                    onClick={() => setIncomeCalcOpen(true)}
                  >
                    소득분위 확인하기
                  </Typography>
                </Box>

                {/* 데이터 출처 — 로그인 사용자만 */}
                {isLoggedIn && (
                  <Box>
                    <Typography variant="caption" fontWeight={700} mb={0.5} display="block">데이터 출처</Typography>
                    <RadioGroup row value={sourceType} onChange={(e) => setSourceType(e.target.value)}>
                      {["전체", "온통청년", "복지로 중앙", "복지로 지자체"].map((o) => (
                        <FormControlLabel
                          key={o} value={o}
                          control={<Radio size="small" sx={{ py: 0.3 }} />}
                          label={<Typography variant="caption">{o}</Typography>}
                          sx={{ mx: 0, mr: 1 }}
                        />
                      ))}
                    </RadioGroup>
                  </Box>
                )}

                {/* 취업상태 */}
                <Box>
                  <Typography variant="caption" fontWeight={700} mb={0.5} display="block">취업상태</Typography>
                  <RadioGroup row value={employ} onChange={(e) => setEmploy(e.target.value)}>
                    {EMPLOY_OPTIONS.map((o) => (
                      <FormControlLabel
                        key={o} value={o}
                        control={<Radio size="small" sx={{ py: 0.3 }} />}
                        label={<Typography variant="caption">{o}</Typography>}
                        sx={{ mx: 0, mr: 1 }}
                      />
                    ))}
                  </RadioGroup>
                </Box>

                {/* 신청 상태 필터 + 버튼 — 로그인 사용자만 */}
                <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", mt: "auto", pt: 1 }}>
                  {isLoggedIn ? (
                    <Box>
                      <Typography variant="caption" fontWeight={700} mb={0.5} display="block">신청 상태</Typography>
                      <RadioGroup row value={statusFilter} onChange={(e) => { setStatusFilter(e.target.value); setPage(1); }}>
                        {["신청가능", "마감", "전부표기"].map((o) => (
                          <FormControlLabel key={o} value={o} control={<Radio size="small" />} label={<Typography variant="caption">{o}</Typography>} sx={{ mr: 0.5 }} />
                        ))}
                      </RadioGroup>
                    </Box>
                  ) : (
                    <Box />
                  )}
                  <Box sx={{ display: "flex", gap: 1 }}>
                    <Button variant="contained" size="small" onClick={handleApplyFilter}>필터 적용</Button>
                    <Button variant="outlined" size="small" onClick={handleResetFilter}>초기화</Button>
                  </Box>
                </Box>

              </Box>
            </Box>
          </Box>
        </Collapse>
      </Box>

      {/* 정책 목록 */}
      <Container maxWidth="lg" sx={{ py: 3 }}>

        {/* 정렬 + 표시 개수 + 열 토글 */}
        <Box sx={{ display: "flex", justifyContent: "flex-end", alignItems: "center", gap: 1, mb: 2 }}>
          <Typography variant="body2" color="text.secondary">정렬:</Typography>
          <FormControl size="small">
            <Select value={sort} onChange={(e) => { setSort(e.target.value); setPage(1); }} sx={{ fontSize: 13 }}>
              {search.trim() && <MenuItem value="relevance">관련도순</MenuItem>}
              <MenuItem value="views">인기순</MenuItem>
              <MenuItem value="latest">최신순</MenuItem>
              <MenuItem value="deadline">마감임박순</MenuItem>
            </Select>
          </FormControl>
          <Typography variant="body2" color="text.secondary">표시:</Typography>
          <FormControl size="small">
            <Select value={pageSize} onChange={(e) => { setPageSize(e.target.value); setPage(1); }} sx={{ fontSize: 13 }}>
              <MenuItem value={10}>10개</MenuItem>
              <MenuItem value={30}>30개</MenuItem>
            </Select>
          </FormControl>
          <Box sx={{ display: "flex", border: "1px solid #e0e0e0", borderRadius: 1, overflow: "hidden" }}>
            <IconButton size="small" onClick={() => setCols(1)} sx={{ borderRadius: 0, bgcolor: cols === 1 ? "primary.main" : "transparent", color: cols === 1 ? "white" : "text.secondary", "&:hover": { bgcolor: cols === 1 ? "primary.dark" : "#f5f5f5" }, px: 1 }}>
              <ViewListIcon fontSize="small" />
            </IconButton>
            <IconButton size="small" onClick={() => setCols(2)} sx={{ borderRadius: 0, bgcolor: cols === 2 ? "primary.main" : "transparent", color: cols === 2 ? "white" : "text.secondary", "&:hover": { bgcolor: cols === 2 ? "primary.dark" : "#f5f5f5" }, px: 1 }}>
              <GridViewIcon fontSize="small" />
            </IconButton>
          </Box>
        </Box>

        <Typography variant="body2" color="text.secondary" mb={2}>
          총 {totalCount}개의 정책
        </Typography>

        {loading ? (
          <Box sx={{ display: "flex", justifyContent: "center", py: 8 }}>
            <CircularProgress />
          </Box>
        ) : policies.length > 0 ? (
          cols === 1 ? (
            <Box sx={{ display: "flex", flexDirection: "column", gap: 1.5 }}>
              {policies.map((p) => (
                <Card
                  key={p.id}
                  sx={{ cursor: "pointer", transition: "all 0.2s", "&:hover": { transform: "translateY(-2px)", boxShadow: "0 8px 24px rgba(2,128,144,0.15)" } }}
                  onClick={() => navigate(`/policies/${p.id}`)}
                >
                  <CardContent sx={{ display: "flex", alignItems: "flex-start", gap: 2, py: "14px !important" }}>
                    <Box sx={{ flex: 1, minWidth: 0 }}>
                      <Box sx={{ display: "flex", alignItems: "center", gap: 1, mb: 0.5, flexWrap: "wrap" }}>
                        <Chip label={p.category} size="small" color="primary" variant="outlined" />
                        <Chip label={p.dday} size="small" color={ddayColor(p.dday)} variant={p.dday === "상시/문의" || p.dday === "진행중" ? "outlined" : "filled"} />
                      </Box>
                      <Typography variant="subtitle2" fontWeight={700} mb={0.3} sx={{ lineHeight: 1.4 }}>
                        {p.title}
                      </Typography>
                      <Typography variant="body2" color="text.secondary" sx={{ fontSize: 12 }}>
                        {p.summary.length > 120 ? `${p.summary.slice(0, 120)}...` : p.summary}
                      </Typography>
                      <Typography variant="caption" color="text.disabled" mt={0.5} display="block">
                        {p.source}
                      </Typography>
                    </Box>
                    <IconButton size="small" onClick={(e) => handleBookmark(p.id, e)} sx={{ color: p.bookmarked ? "#f59e0b" : "text.disabled", flexShrink: 0 }}>
                      {p.bookmarked ? <BookmarkIcon fontSize="small" /> : <BookmarkBorderIcon fontSize="small" />}
                    </IconButton>
                  </CardContent>
                </Card>
              ))}
            </Box>
          ) : (
            <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", sm: "1fr 1fr" }, gap: 2 }}>
              {policies.map((p) => (
                <Card
                  key={p.id}
                  sx={{ cursor: "pointer", transition: "all 0.2s", "&:hover": { transform: "translateY(-2px)", boxShadow: "0 8px 24px rgba(2,128,144,0.15)" } }}
                  onClick={() => navigate(`/policies/${p.id}`)}
                >
                  <CardContent>
                    <Box sx={{ display: "flex", justifyContent: "space-between", mb: 1 }}>
                      <Chip label={p.category} size="small" color="primary" variant="outlined" />
                      <Chip label={p.dday} size="small" color={ddayColor(p.dday)} variant={p.dday === "상시/문의" || p.dday === "진행중" ? "outlined" : "filled"} />
                    </Box>
                    <Typography variant="subtitle2" fontWeight={700} mb={0.5} sx={{ lineHeight: 1.4 }}>
                      {p.title}
                    </Typography>
                    <Typography variant="body2" color="text.secondary" mb={1} sx={{ fontSize: 12 }}>
                      {p.summary.length > 80 ? `${p.summary.slice(0, 80)}...` : p.summary}
                    </Typography>
                    <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                      <Typography variant="caption" color="text.secondary">{p.source}</Typography>
                      <IconButton size="small" onClick={(e) => handleBookmark(p.id, e)} sx={{ color: p.bookmarked ? "#f59e0b" : "text.disabled" }}>
                        {p.bookmarked ? <BookmarkIcon fontSize="small" /> : <BookmarkBorderIcon fontSize="small" />}
                      </IconButton>
                    </Box>
                  </CardContent>
                </Card>
              ))}
            </Box>
          )
        ) : (
          <Box sx={{ textAlign: "center", py: 8, color: "text.secondary" }}>
            <Typography fontSize={40}>🔍</Typography>
            <Typography mt={1}>검색 결과가 없습니다</Typography>
          </Box>
        )}

        {totalPages > 1 && (
          <Box sx={{ display: "flex", justifyContent: "center", mt: 4 }}>
            <Pagination count={totalPages} page={page} onChange={(_, v) => setPage(v)} color="primary" />
          </Box>
        )}
      </Container>

      <IncomeCalculatorModal
        open={incomeCalcOpen}
        onClose={() => setIncomeCalcOpen(false)}
        onSelect={(value) => { setIncome(value); setIncomeOpen(false); }}
      />
      <Snackbar
        open={toast.open}
        autoHideDuration={3000}
        onClose={() => setToast((prev) => ({ ...prev, open: false }))}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
      >
        <Alert severity={toast.severity} onClose={() => setToast((prev) => ({ ...prev, open: false }))}>
          {toast.msg}
        </Alert>
      </Snackbar>
      <FloatingNav />
    </Box>
  );
}
