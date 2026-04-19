import { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Box, Container, Typography, TextField, InputAdornment, Button,
  Card, CardContent, Chip, Grid, Pagination, Select, MenuItem,
  FormControl, IconButton, Snackbar, Alert, Tabs, Tab,
} from "@mui/material";
import SearchIcon from "@mui/icons-material/Search";
import BookmarkBorderIcon from "@mui/icons-material/BookmarkBorder";
import BookmarkIcon from "@mui/icons-material/Bookmark";
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

// 임시: 더미 정책 데이터 — 실제 데이터 연동 시 삭제
const DUMMY = [
  { id: 1, title: "청년 전세자금 대출 지원", category: "주거", dday: "D-12", source: "국토교통부", summary: "최대 1억원 / 온라인신청 가능", bookmarked: false },
  { id: 2, title: "청년 취업지원 프로그램", category: "일자리", dday: "D-30", source: "고용노동부", summary: "월 50만원 지원 / 서울시", bookmarked: false },
  { id: 3, title: "국민취업지원제도", category: "일자리", dday: "상시", source: "고용노동부", summary: "취업 취약계층 청년에게 취업지원서비스 및 구직촉진수당 제공", bookmarked: true },
  { id: 4, title: "청년 창업지원 프로그램", category: "금융·생활", dday: "D-45", source: "중소벤처기업부", summary: "만 39세 이하 청년 창업자 최대 1억원 사업화 자금 지원", bookmarked: false },
  { id: 5, title: "청년문화누리카드", category: "문화·여가", dday: "D-60", source: "문화체육관광부", summary: "연 11만원 문화·여행·체육 분야 이용권 지원", bookmarked: false },
  { id: 6, title: "청년 심리상담 지원", category: "건강·의료", dday: "상시", source: "보건복지부", summary: "만 34세 이하 청년 1인당 최대 10회 전문 심리상담 무료 제공", bookmarked: false },
  { id: 7, title: "청년월세 한시 특별지원", category: "주거", dday: "D-5", source: "국토교통부", summary: "만 19~34세 무주택 청년에게 월 최대 20만원, 12개월 지원", bookmarked: false },
  { id: 8, title: "청년내일저축계좌", category: "금융·생활", dday: "D-90", source: "보건복지부", summary: "근로·사업소득 월 10만원 저축 시 정부 10~30만원 매칭 지원", bookmarked: false },
  { id: 9, title: "청년 교육비 지원", category: "교육·직업훈련", dday: "D-20", source: "교육부", summary: "직업훈련 수강료 80% 지원, 최대 200만원", bookmarked: false },
  { id: 10, title: "청년 건강검진 지원", category: "건강·의료", dday: "상시", source: "보건복지부", summary: "만 20세, 30세 청년 건강검진 무료 제공", bookmarked: false },
];
// 임시 끝

export default function PoliciesPage() {
  const navigate = useNavigate();
  const { isLoggedIn } = useAuthStore();
  const [search, setSearch] = useState("");
  const [catTab, setCatTab] = useState(0);
  const [region, setRegion] = useState("전체");
  const [sort, setSort] = useState("latest");
  const [page, setPage] = useState(1);
  const [policies, setPolicies] = useState(DUMMY); // 임시: 실제 데이터 연동 시 API 응답으로 교체 후 삭제
  const [toast, setToast] = useState({ open: false, msg: "" });

  const PAGE_SIZE = 20;

  const handleBookmark = (id, e) => {
    e.stopPropagation();
    if (!isLoggedIn) {
      setToast({ open: true, msg: "로그인 후 이용 가능해요" });
      return;
    }
    setPolicies((prev) => prev.map((p) => (p.id === id ? { ...p, bookmarked: !p.bookmarked } : p)));
  };

  const selectedCatValue = CATEGORIES[catTab]?.value ?? "";

  // 임시: 실제 연동 시 필터링/페이지네이션을 API 파라미터로 교체 후 아래 블록 삭제
  const filtered = policies.filter((p) => {
    if (search && !p.title.includes(search) && !p.summary.includes(search)) return false;
    if (selectedCatValue && p.category !== CATEGORIES[catTab]?.label) return false;
    return true;
  });

  const paginated = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE); // 임시: 삭제
  const totalPages = Math.ceil(filtered.length / PAGE_SIZE); // 임시: 삭제

  const ddayColor = (dday) => {
    if (dday === "상시") return "success";
    const n = parseInt(dday.replace("D-", ""));
    return n <= 14 ? "error" : "primary";
  };

  return (
    <Box sx={{ minHeight: "100vh", bgcolor: "background.default" }}>
      <Header />

      {/* 검색 */}
      <Box sx={{ bgcolor: "white", borderBottom: "1px solid #E8F4F5", px: { xs: 2, sm: 4 }, py: 2 }}>
        <Box sx={{ maxWidth: 600, mx: "auto", display: "flex", gap: 1 }}>
          <TextField
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(1); }}
            placeholder="정책명, 키워드로 검색..."
            fullWidth
            size="small"
            InputProps={{
              startAdornment: <InputAdornment position="start"><SearchIcon color="action" fontSize="small" /></InputAdornment>,
            }}
          />
          <Button variant="contained" onClick={() => setPage(1)}>검색</Button>
        </Box>
      </Box>

      {/* 카테고리 탭 */}
      <Box sx={{ bgcolor: "white", borderBottom: "1px solid #E8F4F5" }}>
        <Tabs
          value={catTab}
          onChange={(_, v) => { setCatTab(v); setPage(1); }}
          variant="scrollable"
          scrollButtons="auto"
          sx={{ px: 2, "& .MuiTab-root": { fontSize: 13, minWidth: "auto", px: 2 } }}
        >
          {CATEGORIES.map((cat) => (
            <Tab key={cat.value} label={cat.label} />
          ))}
        </Tabs>
      </Box>

      <Container maxWidth="lg" sx={{ py: 3 }}>
        {/* 필터 + 정렬 */}
        <Box sx={{ display: "flex", gap: 1, mb: 2, alignItems: "center", flexWrap: "wrap" }}>
          <FormControl size="small">
            <Select value={region} onChange={(e) => setRegion(e.target.value)} sx={{ fontSize: 13 }}>
              {REGIONS.map((r) => <MenuItem key={r} value={r}>{r}</MenuItem>)}
            </Select>
          </FormControl>
          <FormControl size="small" sx={{ ml: "auto" }}>
            <Select value={sort} onChange={(e) => setSort(e.target.value)} sx={{ fontSize: 13 }}>
              <MenuItem value="latest">최신순</MenuItem>
              <MenuItem value="views">인기순</MenuItem>
              <MenuItem value="deadline">마감임박순</MenuItem>
            </Select>
          </FormControl>
        </Box>

        <Typography variant="body2" color="text.secondary" mb={2}>
          총 <strong>{filtered.length}</strong>개의 정책
        </Typography>

        {paginated.length > 0 ? (
          <Grid container spacing={2}>
            {paginated.map((p) => (
              <Grid item xs={12} sm={6} md={4} key={p.id}>
                <Card
                  sx={{ height: "100%", cursor: "pointer", transition: "all 0.2s", "&:hover": { transform: "translateY(-2px)", boxShadow: "0 8px 24px rgba(2,128,144,0.15)" } }}
                  onClick={() => navigate(`/policies/${p.id}`)}
                >
                  <CardContent>
                    <Box sx={{ display: "flex", justifyContent: "space-between", mb: 1 }}>
                      <Chip label={p.category} size="small" color="primary" variant="outlined" />
                      <Chip label={p.dday} size="small" color={ddayColor(p.dday)} variant={p.dday === "상시" ? "outlined" : "filled"} />
                    </Box>
                    <Typography variant="subtitle2" fontWeight={700} mb={0.5} sx={{ lineHeight: 1.4 }}>
                      {p.title}
                    </Typography>
                    <Typography variant="body2" color="text.secondary" mb={1} sx={{ fontSize: 12 }}>
                      {p.summary}
                    </Typography>
                    <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                      <Typography variant="caption" color="text.secondary">{p.source}</Typography>
                      <IconButton size="small" onClick={(e) => handleBookmark(p.id, e)} sx={{ color: p.bookmarked ? "#f59e0b" : "text.disabled" }}>
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

        {totalPages > 1 && (
          <Box sx={{ display: "flex", justifyContent: "center", mt: 4 }}>
            <Pagination count={totalPages} page={page} onChange={(_, v) => setPage(v)} color="primary" />
          </Box>
        )}
      </Container>

      <Snackbar open={toast.open} autoHideDuration={2000} onClose={() => setToast({ ...toast, open: false })} anchorOrigin={{ vertical: "bottom", horizontal: "center" }}>
        <Alert severity="info">{toast.msg}</Alert>
      </Snackbar>
    </Box>
  );
}
