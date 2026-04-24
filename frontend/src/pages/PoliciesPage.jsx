import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Box,
  Container,
  Typography,
  TextField,
  InputAdornment,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  Pagination,
  Select,
  MenuItem,
  FormControl,
  IconButton,
  Snackbar,
  Alert,
  Tabs,
  Tab,
  CircularProgress,
} from "@mui/material";
import SearchIcon from "@mui/icons-material/Search";
import BookmarkBorderIcon from "@mui/icons-material/BookmarkBorder";
import BookmarkIcon from "@mui/icons-material/Bookmark";
import Header from "../components/Header";
import api from "../lib/axios";
import { useAuthStore } from "../store/authStore";

const CATEGORIES = [
  { label: "전체", value: "" },
  { label: "주거", value: "주거" },
  { label: "일자리", value: "일자리" },
  { label: "교육·직업훈련", value: "교육·직업훈련" },
  { label: "금융·생활", value: "금융·생활지원" },
  { label: "문화·여가", value: "문화·여가" },
  { label: "건강·의료", value: "건강·의료" },
  { label: "가족·돌봄", value: "가족·돌봄" },
  { label: "안전·위기", value: "안전·위기" },
  { label: "참여·기회", value: "참여·기회" },
];

const REGIONS = [
  "전체",
  "서울",
  "부산",
  "대구",
  "인천",
  "광주",
  "대전",
  "울산",
  "세종",
  "경기",
  "강원",
  "충북",
  "충남",
  "전북",
  "전남",
  "경북",
  "경남",
  "제주",
];

const SORT_MAP = {
  latest: "LATEST",
  views: "VIEWS",
  name: "NAME",
};

const PAGE_SIZE = 20;

const formatDday = (dateText, status) => {
  if (status === "CLOSED") return "종료";
  if (!dateText) return "상시";

  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const endDate = new Date(`${dateText}T00:00:00`);
  if (Number.isNaN(endDate.getTime())) return "상시";

  const diff = Math.ceil((endDate - today) / 86400000);
  if (diff < 0) return "종료";
  if (diff === 0) return "D-Day";
  return `D-${diff}`;
};

const ddayColor = (dday) => {
  if (dday === "종료") return "default";
  if (dday === "상시") return "success";
  if (dday === "D-Day") return "error";
  const n = Number.parseInt(String(dday).replace("D-", ""), 10);
  return Number.isNaN(n) ? "default" : n <= 14 ? "error" : "primary";
};

const mapPolicySummary = (policy) => ({
  id: policy.id,
  title: policy.title,
  category: policy.unifiedCategory || "기타",
  dday: formatDday(policy.applyEndDate, policy.status),
  source: policy.hostOrg || policy.applyMethodName || "출처 정보 없음",
  summary: policy.description || "정책 설명 정보가 없습니다.",
  bookmarked: Boolean(policy.bookmarked),
});

export default function PoliciesPage() {
  const navigate = useNavigate();
  const { isLoggedIn } = useAuthStore();
  const [searchInput, setSearchInput] = useState("");
  const [searchKeyword, setSearchKeyword] = useState("");
  const [catTab, setCatTab] = useState(0);
  const [region, setRegion] = useState("전체");
  const [sort, setSort] = useState("latest");
  const [page, setPage] = useState(1);
  const [policies, setPolicies] = useState([]);
  const [totalPages, setTotalPages] = useState(1);
  const [totalCount, setTotalCount] = useState(0);
  const [loading, setLoading] = useState(false);
  const [toast, setToast] = useState({ open: false, msg: "", severity: "info" });

  const selectedCategory = CATEGORIES[catTab]?.value ?? "";

  useEffect(() => {
    const controller = new AbortController();

    const fetchPolicies = async () => {
      setLoading(true);
      try {
        const commonParams = {
          category: selectedCategory || undefined,
          sido: region === "전체" ? undefined : region,
          sort: SORT_MAP[sort] ?? "LATEST",
          page: page - 1,
          size: PAGE_SIZE,
        };

        if (searchKeyword.trim()) {
          const { data } = await api.get("/api/policies/search", {
            params: {
              ...commonParams,
              keyword: searchKeyword.trim(),
            },
            signal: controller.signal,
          });

          const items = (data.data ?? []).map(mapPolicySummary);
          setPolicies(items);
          setTotalCount(items.length);
          setTotalPages(items.length === PAGE_SIZE ? page + 1 : Math.max(page, 1));
          return;
        }

        const { data } = await api.get("/api/policies", {
          params: commonParams,
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
  }, [page, region, searchKeyword, selectedCategory, sort]);

  const handleBookmark = async (id, event) => {
    event.stopPropagation();
    if (!isLoggedIn) {
      setToast({ open: true, msg: "로그인 후 이용 가능해요", severity: "info" });
      return;
    }

    try {
      await api.post(`/api/policies/${id}/bookmark`);
      setPolicies((prev) =>
        prev.map((policy) =>
          policy.id === id ? { ...policy, bookmarked: !policy.bookmarked } : policy
        )
      );
    } catch {
      setToast({ open: true, msg: "북마크 처리에 실패했습니다", severity: "error" });
    }
  };

  const handleSearch = () => {
    setPage(1);
    setSearchKeyword(searchInput.trim());
  };

  const resultLabel = searchKeyword
    ? `현재 페이지 검색 결과 ${policies.length}건`
    : `총 ${totalCount}개의 정책`;

  return (
    <Box sx={{ minHeight: "100vh", bgcolor: "background.default" }}>
      <Header />

      <Box sx={{ bgcolor: "white", borderBottom: "1px solid #E8F4F5", px: { xs: 2, sm: 4 }, py: 2 }}>
        <Box sx={{ maxWidth: 600, mx: "auto", display: "flex", gap: 1 }}>
          <TextField
            value={searchInput}
            onChange={(event) => setSearchInput(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") handleSearch();
            }}
            placeholder="정책명, 키워드로 검색..."
            fullWidth
            size="small"
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon color="action" fontSize="small" />
                </InputAdornment>
              ),
            }}
          />
          <Button variant="contained" onClick={handleSearch}>
            검색
          </Button>
        </Box>
      </Box>

      <Box sx={{ bgcolor: "white", borderBottom: "1px solid #E8F4F5" }}>
        <Tabs
          value={catTab}
          onChange={(_, value) => {
            setCatTab(value);
            setPage(1);
          }}
          variant="scrollable"
          scrollButtons="auto"
          sx={{ px: 2, "& .MuiTab-root": { fontSize: 13, minWidth: "auto", px: 2 } }}
        >
          {CATEGORIES.map((category) => (
            <Tab key={category.value} label={category.label} />
          ))}
        </Tabs>
      </Box>

      <Container maxWidth="lg" sx={{ py: 3 }}>
        <Box sx={{ display: "flex", gap: 1, mb: 2, alignItems: "center", flexWrap: "wrap" }}>
          <FormControl size="small">
            <Select
              value={region}
              onChange={(event) => {
                setRegion(event.target.value);
                setPage(1);
              }}
              sx={{ fontSize: 13 }}
            >
              {REGIONS.map((item) => (
                <MenuItem key={item} value={item}>
                  {item}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <FormControl size="small" sx={{ ml: "auto" }}>
            <Select
              value={sort}
              onChange={(event) => {
                setSort(event.target.value);
                setPage(1);
              }}
              sx={{ fontSize: 13 }}
            >
              <MenuItem value="latest">최신순</MenuItem>
              <MenuItem value="views">인기순</MenuItem>
              <MenuItem value="name">이름순</MenuItem>
            </Select>
          </FormControl>
        </Box>

        <Typography variant="body2" color="text.secondary" mb={2}>
          {resultLabel}
        </Typography>

        {loading ? (
          <Box sx={{ display: "flex", justifyContent: "center", py: 8 }}>
            <CircularProgress />
          </Box>
        ) : policies.length > 0 ? (
          <Grid container spacing={2}>
            {policies.map((policy) => (
              <Grid item xs={12} sm={6} md={4} key={policy.id}>
                <Card
                  sx={{
                    height: "100%",
                    cursor: "pointer",
                    transition: "all 0.2s",
                    "&:hover": {
                      transform: "translateY(-2px)",
                      boxShadow: "0 8px 24px rgba(2,128,144,0.15)",
                    },
                  }}
                  onClick={() => navigate(`/policies/${policy.id}`)}
                >
                  <CardContent>
                    <Box sx={{ display: "flex", justifyContent: "space-between", mb: 1 }}>
                      <Chip label={policy.category} size="small" color="primary" variant="outlined" />
                      <Chip
                        label={policy.dday}
                        size="small"
                        color={ddayColor(policy.dday)}
                        variant={policy.dday === "상시" ? "outlined" : "filled"}
                      />
                    </Box>
                    <Typography variant="subtitle2" fontWeight={700} mb={0.5} sx={{ lineHeight: 1.4 }}>
                      {policy.title}
                    </Typography>
                    <Typography variant="body2" color="text.secondary" mb={1} sx={{ fontSize: 12 }}>
                      {policy.summary}
                    </Typography>
                    <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                      <Typography variant="caption" color="text.secondary">
                        {policy.source}
                      </Typography>
                      <IconButton
                        size="small"
                        onClick={(event) => handleBookmark(policy.id, event)}
                        sx={{ color: policy.bookmarked ? "#f59e0b" : "text.disabled" }}
                      >
                        {policy.bookmarked ? (
                          <BookmarkIcon fontSize="small" />
                        ) : (
                          <BookmarkBorderIcon fontSize="small" />
                        )}
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
            <Pagination
              count={totalPages}
              page={page}
              onChange={(_, value) => setPage(value)}
              color="primary"
            />
          </Box>
        )}
      </Container>

      <Snackbar
        open={toast.open}
        autoHideDuration={2000}
        onClose={() => setToast((prev) => ({ ...prev, open: false }))}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
      >
        <Alert severity={toast.severity}>{toast.msg}</Alert>
      </Snackbar>
    </Box>
  );
}
