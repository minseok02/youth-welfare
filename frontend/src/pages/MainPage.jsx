import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Box, Container, Typography, Button,
  Card, CardContent, Chip,
  Snackbar, Alert, CircularProgress, Divider,
} from "@mui/material";
import StarIcon from "@mui/icons-material/Star";
import ArrowForwardIcon from "@mui/icons-material/ArrowForward";
import Header from "../components/Header";
import FloatingNav from "../components/FloatingNav";
import api from "../lib/axios";
import { useAuthStore } from "../store/authStore";

const statusLabel = (status) => {
  if (status === "ACTIVE") return "진행중";
  if (status === "UPCOMING") return "예정";
  if (status === "CLOSED") return "종료";
  return "상시";
};

const ddayColor = (dday) => {
  if (dday === "종료") return "default";
  if (dday === "상시" || dday === "상시/문의" || dday === "진행중") return "success";
  if (dday === "예정") return "info";
  if (dday === "D-Day") return "error";
  const n = Number.parseInt(String(dday).replace("D-", ""), 10);
  return Number.isNaN(n) ? "default" : n <= 14 ? "error" : "primary";
};

const mapRecommendation = (rec) => ({
  id: rec.serviceId,
  logId: rec.logId ?? null,
  title: rec.title,
  category: rec.unifiedCategory || "기타",
  dday: statusLabel(rec.status),
  summary: rec.description || "정책 설명 정보가 없습니다.",
  aiReason: rec.aiReason,
});

const mapPolicy = (policy) => ({
  id: policy.id,
  title: policy.title,
  category: policy.unifiedCategory || "기타",
  summary: policy.description || "정책 설명 정보가 없습니다.",
});

const TEASER_CATEGORIES = [
  { label: "일자리", value: "일자리" },
  { label: "교육·직업훈련", value: "교육·직업훈련" },
  { label: "주거", value: "주거" },
];

export default function MainPage() {
  const navigate = useNavigate();
  const { isLoggedIn, user } = useAuthStore();

  const [recommendations, setRecommendations] = useState([]);
  const [loadingRec, setLoadingRec] = useState(false);
  const [refreshingRec, setRefreshingRec] = useState(false);
  const [personalRefreshing, setPersonalRefreshing] = useState(false);
  const [teaserPolicies, setTeaserPolicies] = useState({});
  const [loadingTeaser, setLoadingTeaser] = useState(false);
  const [toast, setToast] = useState({ open: false, msg: "", severity: "info" });

  const showToast = useCallback((msg, severity = "info") => {
    setToast({ open: true, msg, severity });
  }, []);

  useEffect(() => {
    if (!isLoggedIn) return;
    const controller = new AbortController();

    const fetchRecommendations = async () => {
      setLoadingRec(true);
      try {
        const { data } = await api.get("/api/recommendations", {
          params: { size: 6 },
          signal: controller.signal,
        });
        setRecommendations((data.data ?? []).map(mapRecommendation));
      } catch (error) {
        if (error.name === "CanceledError" || error.code === "ERR_CANCELED") return;
        showToast("추천 정책을 불러오지 못했습니다", "error");
      } finally {
        if (!controller.signal.aborted) setLoadingRec(false);
      }
    };

    fetchRecommendations();
    return () => controller.abort();
  }, [isLoggedIn, showToast]);

  useEffect(() => {
    if (isLoggedIn) return;
    const controller = new AbortController();

    const fetchTeaser = async () => {
      setLoadingTeaser(true);
      try {
        const results = await Promise.all(
          TEASER_CATEGORIES.map((cat) =>
            api.get("/api/policies", {
              params: { category: cat.value, sort: "LATEST", size: 3, page: 0 },
              signal: controller.signal,
            })
          )
        );
        const mapped = {};
        TEASER_CATEGORIES.forEach((cat, i) => {
          const pageData = results[i].data.data ?? {};
          mapped[cat.value] = (pageData.content ?? []).map(mapPolicy);
        });
        setTeaserPolicies(mapped);
      } catch (error) {
        if (error.name === "CanceledError" || error.code === "ERR_CANCELED") return;
      } finally {
        if (!controller.signal.aborted) setLoadingTeaser(false);
      }
    };

    fetchTeaser();
    return () => controller.abort();
  }, [isLoggedIn]);

  const handleRefreshRecommendations = async () => {
    if (!user?.hasPriorities) {
      showToast("마이페이지에서 우선순위를 먼저 설정해주세요");
      return;
    }
    setRefreshingRec(true);
    try {
      const { data } = await api.post("/api/recommendations/refresh");
      setRecommendations((data.data ?? []).map(mapRecommendation));
      showToast("추천을 새로 불러왔습니다", "success");
    } catch {
      showToast("추천 갱신에 실패했습니다", "error");
    } finally {
      setRefreshingRec(false);
    }
  };

  const handlePersonalRefresh = async () => {
    if (!user?.hasPriorities) {
      showToast("마이페이지에서 우선순위를 먼저 설정해주세요");
      return;
    }
    setPersonalRefreshing(true);
    try {
      const { data } = await api.post("/api/recommendations/refresh?personal=true");
      setRecommendations((data.data ?? []).map(mapRecommendation));
      showToast("개인 맞춤 추천을 새로 받았습니다", "success");
    } catch {
      showToast("재추천에 실패했습니다", "error");
    } finally {
      setPersonalRefreshing(false);
    }
  };

  // 비로그인 뷰
  if (!isLoggedIn) {
    return (
      <Box sx={{ minHeight: "100vh", bgcolor: "background.default" }}>
        <Header />

        <Box sx={{
          background: "linear-gradient(135deg, #016070 0%, #00A896 100%)",
          pt: 8, pb: 6, px: 2, textAlign: "center",
        }}>
          <Typography variant="h4" fontWeight={700} color="white" mb={1.5}>
            나에게 딱 맞는 청년 복지정책
          </Typography>
          <Typography variant="body1" color="rgba(255,255,255,0.85)" mb={4}>
            현재 내 상황에 맞는 정책을 추천해드려요
          </Typography>
          <Box sx={{ display: "flex", gap: 2, justifyContent: "center", flexWrap: "wrap" }}>
            <Button
              variant="contained"
              size="large"
              onClick={() => navigate("/login")}
              sx={{ bgcolor: "white", color: "primary.main", fontWeight: 700, "&:hover": { bgcolor: "#f0f0f0" }, px: 4 }}
            >
              로그인하고 맞춤 추천 받기
            </Button>
            <Button
              variant="outlined"
              size="large"
              onClick={() => navigate("/signup")}
              sx={{ color: "white", borderColor: "rgba(255,255,255,0.7)", "&:hover": { borderColor: "white", bgcolor: "rgba(255,255,255,0.1)" }, px: 4 }}
            >
              회원가입
            </Button>
          </Box>
        </Box>

        <Container maxWidth="lg" sx={{ py: 5 }}>
          <Typography variant="h6" fontWeight={700} mb={3} textAlign="center">
            지금 청년들이 많이 보는 정책
          </Typography>

          {loadingTeaser ? (
            <Box sx={{ display: "flex", justifyContent: "center", py: 6 }}>
              <CircularProgress />
            </Box>
          ) : (
            TEASER_CATEGORIES.map((cat) => {
              const items = teaserPolicies[cat.value] ?? [];
              if (items.length === 0) return null;
              return (
                <Box key={cat.value} mb={5}>
                  <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 2 }}>
                    <Typography variant="subtitle1" fontWeight={700}>{cat.label}</Typography>
                    <Button
                      size="small"
                      endIcon={<ArrowForwardIcon fontSize="small" />}
                      onClick={() => navigate("/policies")}
                      sx={{ fontSize: 13 }}
                    >
                      더 보기
                    </Button>
                  </Box>
                  <Box sx={{ display: "flex", flexDirection: "column", gap: 1.5 }}>
                    {items.map((policy) => (
                      <Card
                        key={policy.id}
                        sx={{
                          cursor: "pointer", transition: "all 0.2s",
                          "&:hover": { transform: "translateY(-2px)", boxShadow: "0 8px 24px rgba(2,128,144,0.15)" },
                        }}
                        onClick={() => navigate(`/policies/${policy.id}`)}
                      >
                        <CardContent sx={{ py: "14px !important" }}>
                          <Chip label={policy.category} size="small" color="primary" variant="outlined" sx={{ mb: 0.5 }} />
                          <Typography variant="subtitle2" fontWeight={700} mb={0.3} sx={{ lineHeight: 1.4 }}>
                            {policy.title}
                          </Typography>
                          <Typography variant="body2" color="text.secondary" sx={{ fontSize: 12 }}>
                            {policy.summary.length > 120 ? `${policy.summary.slice(0, 120)}...` : policy.summary}
                          </Typography>
                        </CardContent>
                      </Card>
                    ))}
                  </Box>
                </Box>
              );
            })
          )}

          <Divider sx={{ my: 4 }} />
          <Box sx={{ textAlign: "center", py: 4 }}>
            <Typography variant="h6" fontWeight={700} mb={1}>
              로그인하면 나에게 맞는 정책을 추천해드려요
            </Typography>
            <Typography variant="body2" color="text.secondary" mb={3}>
              나이, 지역, 소득 수준에 맞춘 개인화 추천을 받아보세요
            </Typography>
            <Button
              variant="contained"
              size="large"
              onClick={() => navigate("/login")}
              sx={{ px: 5, py: 1.5, fontWeight: 700 }}
            >
              시작하기
            </Button>
          </Box>
        </Container>
      <FloatingNav />
    </Box>
    );
  }

  // 로그인 뷰
  return (
    <Box sx={{ minHeight: "100vh", bgcolor: "background.default" }}>
      <Header />

      <Box sx={{
        background: "linear-gradient(135deg, #016070 0%, #00A896 100%)",
        pt: 5, pb: 4, px: 2, textAlign: "center",
      }}>
        <Typography variant="h5" fontWeight={700} color="white" mb={1}>
          {user?.name ? `${user.name}님, 안녕하세요!` : "안녕하세요!"}
        </Typography>
        <Typography variant="body1" color="rgba(255,255,255,0.85)" mb={3}>
          이 정책은 어떤가요?
        </Typography>
        <Box sx={{ display: "flex", gap: 1.5, justifyContent: "center", flexWrap: "wrap" }}>
          <Button
            variant="outlined"
            size="small"
            onClick={handleRefreshRecommendations}
            disabled={refreshingRec || personalRefreshing}
            sx={{ color: "white", borderColor: "rgba(255,255,255,0.7)", "&:hover": { borderColor: "white", bgcolor: "rgba(255,255,255,0.1)" } }}
          >
            {refreshingRec ? "갱신 중..." : "새로고침"}
          </Button>
          <Button
            variant="contained"
            size="small"
            onClick={handlePersonalRefresh}
            disabled={personalRefreshing || refreshingRec}
            sx={{ bgcolor: "white", color: "primary.main", fontWeight: 700, "&:hover": { bgcolor: "#f0f0f0" } }}
          >
            {personalRefreshing ? "분석 중..." : "맞춤 재추천"}
          </Button>
        </Box>
      </Box>

      <Container maxWidth="lg" sx={{ py: 3 }}>
        <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 2 }}>
          <Typography variant="subtitle1" fontWeight={700} color="primary" sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
            <StarIcon fontSize="small" />
            맞춤 추천 정책
          </Typography>
          <Button
            size="small"
            endIcon={<ArrowForwardIcon fontSize="small" />}
            onClick={() => navigate("/policies")}
            sx={{ fontSize: 13 }}
          >
            모든 정책 보기
          </Button>
        </Box>

        {loadingRec ? (
          <Box sx={{ display: "flex", justifyContent: "center", py: 8 }}>
            <CircularProgress />
          </Box>
        ) : recommendations.length > 0 ? (
          <Box sx={{ display: "flex", flexDirection: "column", gap: 1.5 }}>
            {recommendations.map((rec) => (
              <Card
                key={rec.id}
                sx={{
                  cursor: "pointer", transition: "all 0.2s",
                  "&:hover": { transform: "translateY(-2px)", boxShadow: "0 8px 24px rgba(2,128,144,0.15)" },
                }}
                onClick={() => navigate(`/policies/${rec.id}${rec.logId ? `?log_id=${rec.logId}` : ""}`)}
              >
                <CardContent sx={{ py: "14px !important" }}>
                  <Box sx={{ display: "flex", alignItems: "center", gap: 1, mb: 0.5, flexWrap: "wrap" }}>
                    <Chip label={rec.category} size="small" color="primary" variant="outlined" />
                    <Chip
                      label={rec.dday}
                      size="small"
                      color={ddayColor(rec.dday)}
                      variant={rec.dday === "상시" || rec.dday === "상시/문의" || rec.dday === "진행중" ? "outlined" : "filled"}
                    />
                  </Box>
                  <Typography variant="subtitle2" fontWeight={700} mb={0.3} sx={{ lineHeight: 1.4 }}>
                    {rec.title}
                  </Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ fontSize: 12 }}>
                    {rec.summary.length > 120 ? `${rec.summary.slice(0, 120)}...` : rec.summary}
                  </Typography>
                  {rec.aiReason && (
                    <Typography variant="caption" color="primary" sx={{ fontStyle: "italic", display: "block", mt: 0.5 }}>
                      "{rec.aiReason}"
                    </Typography>
                  )}
                </CardContent>
              </Card>
            ))}
          </Box>
        ) : (
          <Box sx={{ textAlign: "center", py: 8, color: "text.secondary" }}>
            <Typography fontSize={40}>🤖</Typography>
            <Typography mt={1} mb={2}>
              {user?.hasPriorities
                ? "추천 정책을 불러오는 중 문제가 생겼어요"
                : "마이페이지에서 우선순위를 설정하면 맞춤 추천을 받을 수 있어요"}
            </Typography>
            {!user?.hasPriorities && (
              <Button variant="contained" onClick={() => navigate("/mypage")}>
                우선순위 설정하러 가기
              </Button>
            )}
          </Box>
        )}
      </Container>

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
