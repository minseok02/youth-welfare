import { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Box, Container, Typography, TextField, InputAdornment, Button,
  Card, CardContent, CardActionArea, Chip, Grid, Pagination,
  Select, MenuItem, FormControl, Collapse, Radio, RadioGroup,
  FormControlLabel, Divider, Snackbar, Alert, Switch, Paper,
  IconButton,
} from "@mui/material";
import SearchIcon from "@mui/icons-material/Search";
import BookmarkBorderIcon from "@mui/icons-material/BookmarkBorder";
import BookmarkIcon from "@mui/icons-material/Bookmark";
import StarIcon from "@mui/icons-material/Star";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import ExpandLessIcon from "@mui/icons-material/ExpandLess";
import ViewListIcon from "@mui/icons-material/ViewList";
import GridViewIcon from "@mui/icons-material/GridView";
import Header from "../components/Header";
import { useAuthStore } from "../store/authStore";

const CATEGORIES = [
  { label: "전체", value: "" },
  { label: "주거", value: "HOUSING" },
  { label: "일자리", value: "JOB" },
  { label: "교육·직업훈련", value: "EDUCATION" },
  { label: "금융·생활", value: "FINANCE" },
  { label: "문화·여가", value: "CULTURE" },
  { label: "건강·의료", value: "HEALTH" },
  { label: "가족·돌봄", value: "FAMILY" },
  { label: "안전·위기", value: "SAFETY" },
  { label: "참여·기회", value: "PARTICIPATION" },
];

const REGIONS = ["전체", "서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주"];

const DISTRICT_MAP = {
  "전체": [],
  "서울": ["전체", "강남구", "강동구", "강북구", "강서구", "관악구", "광진구", "구로구", "금천구", "노원구", "도봉구", "동대문구", "동작구", "마포구", "서대문구", "서초구", "성동구", "성북구", "송파구", "양천구", "영등포구", "용산구", "은평구", "종로구", "중구", "중랑구"],
  "부산": ["전체", "강서구", "금정구", "기장군", "남구", "동구", "동래구", "부산진구", "북구", "사상구", "사하구", "서구", "수영구", "연제구", "영도구", "중구", "해운대구"],
  "대구": ["전체", "남구", "달서구", "달성군", "동구", "북구", "서구", "수성구", "중구"],
  "인천": ["전체", "강화군", "계양구", "남동구", "동구", "미추홀구", "부평구", "서구", "연수구", "옹진군", "중구"],
  "광주": ["전체", "광산구", "남구", "동구", "북구", "서구"],
  "대전": ["전체", "대덕구", "동구", "서구", "유성구", "중구"],
  "울산": ["전체", "남구", "동구", "북구", "울주군", "중구"],
  "세종": [],
  "경기": ["전체", "가평군", "고양시", "과천시", "광명시", "광주시", "구리시", "군포시", "김포시", "남양주시", "동두천시", "부천시", "성남시", "수원시", "시흥시", "안산시", "안성시", "안양시", "양주시", "양평군", "여주시", "연천군", "오산시", "용인시", "의왕시", "의정부시", "이천시", "파주시", "평택시", "포천시", "하남시", "화성시"],
  "강원": ["전체", "강릉시", "고성군", "동해시", "삼척시", "속초시", "양구군", "양양군", "영월군", "원주시", "인제군", "정선군", "철원군", "춘천시", "태백시", "평창군", "홍천군", "화천군", "횡성군"],
  "충북": ["전체", "괴산군", "단양군", "보은군", "영동군", "옥천군", "음성군", "제천시", "증평군", "진천군", "청주시", "충주시"],
  "충남": ["전체", "계룡시", "공주시", "금산군", "논산시", "당진시", "보령시", "부여군", "서산시", "서천군", "아산시", "예산군", "천안시", "청양군", "태안군", "홍성군"],
  "전북": ["전체", "고창군", "군산시", "김제시", "남원시", "무주군", "부안군", "순창군", "완주군", "익산시", "임실군", "장수군", "전주시", "정읍시", "진안군"],
  "전남": ["전체", "강진군", "고흥군", "곡성군", "광양시", "구례군", "나주시", "담양군", "목포시", "무안군", "보성군", "순천시", "신안군", "여수시", "영광군", "영암군", "완도군", "장성군", "장흥군", "진도군", "함평군", "해남군", "화순군"],
  "경북": ["전체", "경산시", "경주시", "고령군", "구미시", "군위군", "김천시", "문경시", "봉화군", "상주시", "성주군", "안동시", "영덕군", "영양군", "영주시", "영천시", "예천군", "울릉군", "울진군", "의성군", "청도군", "청송군", "칠곡군", "포항시"],
  "경남": ["전체", "거제시", "거창군", "고성군", "김해시", "남해군", "밀양시", "사천시", "산청군", "양산시", "의령군", "진주시", "창녕군", "창원시", "통영시", "하동군", "함안군", "함양군", "합천군"],
  "제주": ["전체", "서귀포시", "제주시"],
};

const INCOME_ROWS = [
  { value: "1", left: "1~2분위 (하위 20%)", right: "기초생활수급자" },
  { value: "3", left: "3~4분위 (하위 40%)", right: "차상위계층" },
  { value: "5", left: "5~6분위 (중간)", right: "소득 하위 50% 이하" },
  { value: "7", left: "7~8분위 (상위 40%)", right: "소득 중간 (50~100%)" },
  { value: "9", left: "9~10분위 (상위 20%)", right: "소득 상위 (100% 초과)" },
];

const EMPLOY_OPTIONS = ["전체", "재직중", "구직중", "학생", "기타"];
const SOURCE_OPTIONS = ["전체", "온통청년", "복지로 중앙", "복지로 지자체"];

// TODO: 더미 데이터 — 실제 데이터 연동 시 삭제
const DUMMY_POLICIES = [
  { id: 1, title: "청년 전세자금 대출 지원", category: "주거", dday: "D-12", source: "국토교통부", summary: "최대 1억원 / 온라인신청 가능", isNew: true, bookmarked: false, aiReason: "주거 우선순위 1위 기준 추천" },
  { id: 2, title: "청년 취업지원 프로그램", category: "일자리", dday: "D-30", source: "고용노동부", summary: "월 50만원 지원 / 서울시", bookmarked: false, aiReason: "취업 우선순위 2위 기준 추천" },
  { id: 3, title: "국민취업지원제도", category: "일자리", dday: "상시", source: "고용노동부", summary: "취업 취약계층 청년에게 취업지원서비스 및 구직촉진수당 제공", bookmarked: true },
  { id: 4, title: "청년 창업지원 프로그램", category: "금융·생활", dday: "D-45", source: "중소벤처기업부", summary: "만 39세 이하 청년 창업자 최대 1억원 사업화 자금 지원", bookmarked: false },
  { id: 5, title: "청년문화누리카드", category: "문화·여가", dday: "D-60", source: "문화체육관광부", summary: "연 11만원 문화·여행·체육 분야 이용권 지원", bookmarked: false },
  { id: 6, title: "청년 심리상담 지원", category: "건강·의료", dday: "상시", source: "보건복지부", summary: "만 34세 이하 청년 1인당 최대 10회 전문 심리상담 무료 제공", bookmarked: false },
  { id: 7, title: "청년월세 한시 특별지원", category: "주거", dday: "D-5", source: "국토교통부", summary: "만 19~34세 무주택 청년에게 월 최대 20만원, 12개월 지원", bookmarked: false },
  { id: 8, title: "청년내일저축계좌", category: "금융·생활", dday: "D-90", source: "보건복지부", summary: "근로·사업소득 월 10만원 저축 시 정부 10~30만원 매칭 지원", bookmarked: false },
];
// 더미 데이터 끝

export default function MainPage() {
  const navigate = useNavigate();
  const { isLoggedIn, user, filterSettings } = useAuthStore();

  const [search, setSearch] = useState("");
  const [selectedCat, setSelectedCat] = useState("");
  const [filterOpen, setFilterOpen] = useState(false);
  const [region, setRegion] = useState("전체");
  const [subRegion, setSubRegion] = useState("전체");
  const [income, setIncome] = useState("전체");
  const [incomeOpen, setIncomeOpen] = useState(false);
  const [employ, setEmploy] = useState("전체");
  const [source, setSource] = useState("전체");
  const [includeExpired, setIncludeExpired] = useState(filterSettings?.includeExpired ?? false);
  const [sort, setSort] = useState("views");
  const [pageSize, setPageSize] = useState(10);
  const [cols, setCols] = useState(1);
  const [page, setPage] = useState(1);
  const [isRecommendMode, setIsRecommendMode] = useState(isLoggedIn);
  // TODO: 더미 데이터 — 실제 데이터 연동 시 API 응답으로 교체 후 아래 줄 삭제
  const [policies, setPolicies] = useState(DUMMY_POLICIES);
  const [toast, setToast] = useState({ open: false, msg: "", severity: "info" });

  const showToast = (msg, severity = "info") => setToast({ open: true, msg, severity });

  const handleBookmark = (id, e) => {
    e.stopPropagation();
    if (!isLoggedIn) {
      showToast("로그인 후 이용 가능해요");
      return;
    }
    setPolicies((prev) =>
      prev.map((p) => (p.id === id ? { ...p, bookmarked: !p.bookmarked } : p))
    );
  };

  const handleRecommend = () => {
    if (!isLoggedIn) return;
    if (!user?.hasPriorities) {
      showToast("마이페이지에서 우선순위를 먼저 설정해주세요");
      return;
    }
    setIsRecommendMode(true);
    setSelectedCat("");
  };

  const handleCategorySelect = (val) => {
    setSelectedCat(val);
    if (val !== "") setIsRecommendMode(false);
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
    setSource("전체");
    setIncludeExpired(false);
    setPage(1);
  };

  // TODO: 더미 데이터 — 실제 연동 시 필터링/정렬/페이지네이션을 API 파라미터로 교체 후 아래 블록 삭제
  const filtered = policies.filter((p) => {
    if (search && !p.title.includes(search) && !p.summary.includes(search)) return false;
    if (selectedCat && p.category !== CATEGORIES.find((c) => c.value === selectedCat)?.label) return false;
    return true;
  });

  const paginated = filtered.slice((page - 1) * pageSize, page * pageSize); // TODO: 더미 데이터 — 삭제
  const totalPages = Math.ceil(filtered.length / pageSize); // TODO: 더미 데이터 — 삭제

  const ddayColor = (dday) => {
    if (dday === "상시") return "success";
    const n = parseInt(dday.replace("D-", ""));
    return n <= 14 ? "error" : "primary";
  };

  return (
    <Box sx={{ minHeight: "100vh", bgcolor: "background.default" }}>
      <Header />

      {/* 히어로 검색 + 필터 */}
      <Box sx={{
        background: "linear-gradient(135deg, #016070 0%, #00A896 100%)",
        pt: 5, pb: 3, px: 2, textAlign: "center",
      }}>
        <Typography variant="h5" fontWeight={700} color="white" mb={2}>
          청년을 위한 복지정책을 찾아드려요
        </Typography>

        {/* 검색창 */}
        <Box sx={{ maxWidth: 640, mx: "auto", display: "flex", alignItems: "center", bgcolor: "white", borderRadius: 2, overflow: "hidden", boxShadow: 3, height: 48 }}>
          <SearchIcon color="action" sx={{ ml: 1.5, flexShrink: 0 }} />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && setPage(1)}
            placeholder="검색어를 입력하세요"
            style={{ flex: 1, border: "none", outline: "none", fontSize: 14, padding: "0 12px", height: "100%", background: "transparent" }}
          />
          <Button
            variant="contained"
            disableElevation
            onClick={() => setPage(1)}
            sx={{ borderRadius: 0, px: 3, height: "100%", fontSize: 14, flexShrink: 0 }}
          >
            검색
          </Button>
        </Box>

        {/* 카테고리 선택 토글 + 내 우선순위 버튼 */}
        <Box sx={{ maxWidth: 640, mx: "auto", display: "flex", alignItems: "center", justifyContent: "space-between", mt: 1.5 }}>
          <Button
            size="small"
            onClick={() => setFilterOpen((v) => !v)}
            endIcon={filterOpen ? <ExpandLessIcon /> : <ExpandMoreIcon />}
            sx={{ color: "rgba(255,255,255,0.85)", border: "1px solid rgba(255,255,255,0.4)", borderRadius: 2, fontSize: 13 }}
          >
            카테고리 선택
            {selectedCat && (
              <Chip
                label={CATEGORIES.find((c) => c.value === selectedCat)?.label}
                size="small"
                sx={{ ml: 1, height: 18, fontSize: 11, bgcolor: "rgba(255,255,255,0.25)", color: "white" }}
              />
            )}
          </Button>
          {isLoggedIn && (
            <Button
              size="small"
              startIcon={<StarIcon fontSize="small" />}
              onClick={handleRecommend}
              sx={{
                color: isRecommendMode ? "white" : "rgba(255,255,255,0.7)",
                fontWeight: isRecommendMode ? 700 : 400,
                fontSize: 13,
              }}
            >
              내 우선순위로 보기
            </Button>
          )}
        </Box>

        {/* 필터 패널 (같은 가로 너비) */}
        <Collapse in={filterOpen}>
          <Box sx={{
            maxWidth: 640, mx: "auto", mt: 1,
            bgcolor: "white", borderRadius: 2,
            overflow: "hidden", boxShadow: 3,
            textAlign: "left",
          }}>
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
                {/* 지역: 시 + 구 */}
                <Box>
                  <Typography variant="caption" fontWeight={700} mb={0.5} display="block">지역</Typography>
                  <Box sx={{ display: "flex", gap: 1 }}>
                    <FormControl size="small" sx={{ flex: 1 }}>
                      <Select value={region} onChange={(e) => { setRegion(e.target.value); setSubRegion("전체"); }}>
                        {REGIONS.map((r) => <MenuItem key={r} value={r}>{r}</MenuItem>)}
                      </Select>
                    </FormControl>
                    {DISTRICT_MAP[region]?.length > 0 && (
                      <FormControl size="small" sx={{ flex: 1 }}>
                        <Select value={subRegion} onChange={(e) => setSubRegion(e.target.value)}>
                          {DISTRICT_MAP[region].map((d) => <MenuItem key={d} value={d}>{d}</MenuItem>)}
                        </Select>
                      </FormControl>
                    )}
                  </Box>
                </Box>

                {/* 소득수준: 접기/펼치기 */}
                <Box>
                  <Typography variant="caption" fontWeight={700} mb={0.5} display="block">소득수준</Typography>
                  <Box
                    onClick={() => setIncomeOpen((v) => !v)}
                    sx={{
                      display: "flex", alignItems: "center", justifyContent: "space-between",
                      border: "1px solid #c4c4c4", borderRadius: 1, px: 1.5, py: 0.75,
                      cursor: "pointer", bgcolor: "white",
                      "&:hover": { borderColor: "text.primary" },
                    }}
                  >
                    <Typography variant="caption" color={income === "전체" ? "text.secondary" : "text.primary"}>
                      {income === "전체"
                        ? "전체"
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
                        sx={{
                          display: "flex", alignItems: "center", p: 0.75, gap: 0.5,
                          cursor: "pointer",
                          bgcolor: income === "전체" ? "rgba(2,128,144,0.1)" : "transparent",
                          "&:hover": { bgcolor: income === "전체" ? "rgba(2,128,144,0.1)" : "#f5fafa" },
                          borderBottom: "1px solid #f0f0f0",
                        }}
                      >
                        <Radio size="small" checked={income === "전체"} sx={{ p: 0.5 }} readOnly />
                        <Typography variant="caption">전체</Typography>
                      </Box>
                      {INCOME_ROWS.map((row) => (
                        <Box
                          key={row.value}
                          onClick={(e) => { e.stopPropagation(); setIncome(row.value); setIncomeOpen(false); }}
                          sx={{
                            display: "grid", gridTemplateColumns: "1fr 1fr",
                            cursor: "pointer",
                            bgcolor: income === row.value ? "rgba(2,128,144,0.1)" : "transparent",
                            "&:hover": { bgcolor: income === row.value ? "rgba(2,128,144,0.1)" : "#f5fafa" },
                            borderBottom: "1px solid #f0f0f0",
                          }}
                        >
                          <Box sx={{ display: "flex", alignItems: "center", p: 0.75, gap: 0.5 }}>
                            <Radio size="small" checked={income === row.value} sx={{ p: 0.5 }} readOnly />
                            <Typography variant="caption">{row.left}</Typography>
                          </Box>
                          <Box sx={{ display: "flex", alignItems: "center", p: 0.75, gap: 0.5, borderLeft: "1px solid #e0e0e0" }}>
                            <Radio size="small" checked={income === row.value} sx={{ p: 0.5 }} readOnly />
                            <Typography variant="caption">{row.right}</Typography>
                          </Box>
                        </Box>
                      ))}
                    </Box>
                  </Collapse>
                </Box>

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

                {/* 마감 포함 + 버튼 */}
                <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", mt: "auto", pt: 1 }}>
                  {isLoggedIn && filterSettings?.includeExpired ? (
                    <Box sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                      <Typography variant="caption" fontWeight={700}>종료된 정책 보기</Typography>
                      <Switch size="small" checked={includeExpired} onChange={(e) => setIncludeExpired(e.target.checked)} color="primary" />
                    </Box>
                  ) : <Box />}
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
        {/* 정렬 + 표시 개수 + 열 설정 */}
        <Box sx={{ display: "flex", justifyContent: "flex-end", alignItems: "center", gap: 1, mb: 2 }}>
          <Typography variant="body2" color="text.secondary">정렬:</Typography>
          <FormControl size="small">
            <Select value={sort} onChange={(e) => setSort(e.target.value)} sx={{ fontSize: 13 }}>
              <MenuItem value="views">조회수순</MenuItem>
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
            <IconButton
              size="small"
              onClick={() => setCols(1)}
              sx={{ borderRadius: 0, bgcolor: cols === 1 ? "primary.main" : "transparent", color: cols === 1 ? "white" : "text.secondary", "&:hover": { bgcolor: cols === 1 ? "primary.dark" : "#f5f5f5" }, px: 1 }}
            >
              <ViewListIcon fontSize="small" />
            </IconButton>
            <IconButton
              size="small"
              onClick={() => setCols(2)}
              sx={{ borderRadius: 0, bgcolor: cols === 2 ? "primary.main" : "transparent", color: cols === 2 ? "white" : "text.secondary", "&:hover": { bgcolor: cols === 2 ? "primary.dark" : "#f5f5f5" }, px: 1 }}
            >
              <GridViewIcon fontSize="small" />
            </IconButton>
          </Box>
        </Box>

        {/* 추천 모드 헤더 */}
        {isLoggedIn && isRecommendMode && (
          <Typography variant="subtitle1" fontWeight={700} color="primary" mb={2} sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
            <StarIcon fontSize="small" />
            {user?.name}님의 맞춤 정책이에요
          </Typography>
        )}

        {/* 카드 그리드 */}
        {paginated.length > 0 ? (
          <Grid container spacing={2}>
            {paginated.map((p) => (
              <Grid size={cols === 1 ? 12 : { xs: 12, sm: 6 }} key={p.id}>
                <Card
                  sx={{ height: "100%", cursor: "pointer", transition: "all 0.2s", "&:hover": { transform: "translateY(-2px)", boxShadow: "0 8px 24px rgba(2,128,144,0.15)" } }}
                  onClick={() => navigate(`/policies/${p.id}`)}
                >
                  <CardContent>
                    <Box sx={{ display: "flex", justifyContent: "space-between", mb: 1 }}>
                      <Chip label={p.category} size="small" color="primary" variant="outlined" />
                      <Chip
                        label={p.dday}
                        size="small"
                        color={ddayColor(p.dday)}
                        variant={p.dday === "상시" ? "outlined" : "filled"}
                      />
                    </Box>
                    <Typography variant="subtitle2" fontWeight={700} mb={0.5} sx={{ lineHeight: 1.4 }}>
                      {p.title}
                    </Typography>
                    <Typography variant="body2" color="text.secondary" mb={1} sx={{ fontSize: 12 }}>
                      {p.summary}
                    </Typography>
                    {isLoggedIn && isRecommendMode && p.aiReason && (
                      <Typography variant="caption" color="primary" sx={{ fontStyle: "italic", display: "block", mb: 1 }}>
                        "{p.aiReason}"
                      </Typography>
                    )}
                    <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                      <Typography variant="caption" color="text.secondary">{p.source}</Typography>
                      <IconButton
                        size="small"
                        onClick={(e) => handleBookmark(p.id, e)}
                        sx={{ color: p.bookmarked ? "#f59e0b" : "text.disabled" }}
                      >
                        {p.bookmarked ? <BookmarkIcon fontSize="small" /> : <BookmarkBorderIcon fontSize="small" />}
                      </IconButton>
                    </Box>
                  </CardContent>
                </Card>
              </Grid>
            ))}
          </Grid>
        ) : (
          <Box sx={{ textAlign: "center", py: 8, color: "text.secondary" }}>
            <Typography fontSize={40}>🔍</Typography>
            <Typography mt={1}>검색 결과가 없습니다</Typography>
          </Box>
        )}

        {/* 페이지네이션 */}
        {totalPages > 1 && (
          <Box sx={{ display: "flex", justifyContent: "center", mt: 4 }}>
            <Pagination
              count={totalPages}
              page={page}
              onChange={(_, v) => setPage(v)}
              color="primary"
            />
          </Box>
        )}
      </Container>

      <Snackbar
        open={toast.open}
        autoHideDuration={3000}
        onClose={() => setToast({ ...toast, open: false })}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
      >
        <Alert severity={toast.severity} onClose={() => setToast({ ...toast, open: false })}>
          {toast.msg}
        </Alert>
      </Snackbar>
    </Box>
  );
}
