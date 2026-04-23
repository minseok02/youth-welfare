import { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Box, Container, Typography, Chip, Button, Divider,
  IconButton, Snackbar, Alert, Paper,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import BookmarkBorderIcon from "@mui/icons-material/BookmarkBorder";
import BookmarkIcon from "@mui/icons-material/Bookmark";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import Header from "../components/Header";
import { useAuthStore } from "../store/authStore";

// 임시: 더미 정책 상세 데이터 — 실제 데이터 연동 시 삭제 (API: GET /api/policies/:id)
const DUMMY_DETAIL = {
  id: 1,
  title: "청년 전세자금 대출 지원",
  category: "주거",
  source: "복지로 중앙",
  dday: "D-12",
  isNew: true,
  region: "서울 전체",
  ageRange: "만 19~34세",
  income: "소득 하위 70% 이하",
  onlineApply: true,
  organizer: "국토교통부",
  period: "2026.01.01 ~ 2026.06.30",
  supportContent: "전세보증금의 최대 80%, 최대 1억원까지 저금리(연 1.2%)로 대출 지원합니다. 신혼부부 및 다자녀 가구에 대한 우대금리가 적용됩니다.",
  targetContent: "만 19세 이상 34세 이하 무주택 청년으로 소득 하위 70% 이하인 경우 신청 가능합니다. 부모와 별도 거주 요건이 있을 수 있습니다.",
  applyMethod: "온라인: 복지로(www.bokjiro.go.kr) 접속 후 신청\n오프라인: 주민센터 방문 신청 가능",
  contacts: [
    { name: "국토교통부 콜센터", phone: "1599-0001" },
    { name: "한국주택금융공사", phone: "1688-8114" },
  ],
  law: "주거기본법 제17조",
  formFile: null,
  homepage: "https://www.bokjiro.go.kr",
  detailUrl: "https://www.bokjiro.go.kr",
};

const NO_DATA = "원문에서 확인해주세요.";

function SectionTitle({ children }) {
  return (
    <Typography variant="subtitle1" fontWeight={700} color="primary" mt={3} mb={1}>
      {children}
    </Typography>
  );
}

export default function PolicyDetailPage() {
  const navigate = useNavigate();
  const { isLoggedIn } = useAuthStore();
  const [bookmarked, setBookmarked] = useState(false);
  const [toast, setToast] = useState({ open: false, msg: "", severity: "info" });

  const policy = DUMMY_DETAIL; // 임시: 실제 데이터 연동 시 API 응답으로 교체 후 삭제

  const handleBookmark = () => {
    if (!isLoggedIn) {
      setToast({ open: true, msg: "로그인 후 이용 가능해요", severity: "info" });
      return;
    }
    setBookmarked((v) => !v);
  };

  const ddayColor = (dday) => {
    if (dday === "상시") return "success";
    const n = parseInt(dday.replace("D-", ""));
    return n <= 14 ? "error" : "primary";
  };

  return (
    <Box sx={{ minHeight: "100vh", bgcolor: "background.default", pb: 10 }}>
      <Header />

      <Container maxWidth="md" sx={{ py: 3 }}>
        {/* 상단 네비게이션 */}
        <Box sx={{ display: "flex", justifyContent: "space-between", mb: 2 }}>
          <Button startIcon={<ArrowBackIcon />} onClick={() => navigate(-1)} color="inherit">
            뒤로가기
          </Button>
          <IconButton
            onClick={handleBookmark}
            sx={{ color: bookmarked ? "#f59e0b" : "text.secondary" }}
          >
            {bookmarked ? <BookmarkIcon /> : <BookmarkBorderIcon />}
          </IconButton>
        </Box>

        <Paper elevation={0} sx={{ p: 3, borderRadius: 3, border: "1px solid #E8F4F5" }}>
          {/* 카테고리 + 출처 태그 */}
          <Box sx={{ display: "flex", gap: 1, mb: 1.5, flexWrap: "wrap" }}>
            <Chip label={policy.category} color="primary" size="small" />
            <Chip label={policy.source} variant="outlined" size="small" color="default" />
          </Box>

          {/* 제목 */}
          <Typography variant="h5" fontWeight={700} mb={1.5} sx={{ lineHeight: 1.4 }}>
            {policy.title}
          </Typography>

          {/* D-day + 신규 뱃지 */}
          <Box sx={{ display: "flex", gap: 1, mb: 2 }}>
            <Chip label={policy.dday} color={ddayColor(policy.dday)} size="small" />
            {policy.isNew && <Chip label="🆕 신규" size="small" variant="outlined" color="secondary" />}
          </Box>

          <Divider />

          {/* 요약 정보 칩 */}
          <SectionTitle>요약 정보</SectionTitle>
          <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap" }}>
            {policy.region && <Chip icon={<span>📍</span>} label={policy.region} size="small" variant="outlined" />}
            {policy.ageRange && <Chip icon={<span>👤</span>} label={policy.ageRange} size="small" variant="outlined" />}
            {policy.income && <Chip icon={<span>💰</span>} label={policy.income} size="small" variant="outlined" />}
            {policy.onlineApply && <Chip icon={<span>🖥️</span>} label="온라인 가능" size="small" variant="outlined" color="primary" />}
            {policy.organizer && <Chip icon={<span>🏛️</span>} label={policy.organizer} size="small" variant="outlined" />}
            {policy.period && <Chip icon={<span>🗓️</span>} label={`신청기간: ${policy.period}`} size="small" variant="outlined" />}
          </Box>

          {/* 지원내용 */}
          <SectionTitle>지원내용</SectionTitle>
          <Divider sx={{ mb: 1.5 }} />
          <Typography variant="body2" sx={{ whiteSpace: "pre-line", lineHeight: 1.8, color: "text.primary" }}>
            {policy.supportContent || `지원 내용 정보가 없습니다. ${NO_DATA}`}
          </Typography>

          {/* 신청대상 */}
          <SectionTitle>신청대상</SectionTitle>
          <Divider sx={{ mb: 1.5 }} />
          <Typography variant="body2" sx={{ whiteSpace: "pre-line", lineHeight: 1.8 }}>
            {policy.targetContent || `신청 대상 정보가 없습니다. ${NO_DATA}`}
          </Typography>

          {/* 신청방법 */}
          <SectionTitle>신청방법</SectionTitle>
          <Divider sx={{ mb: 1.5 }} />
          <Typography variant="body2" sx={{ whiteSpace: "pre-line", lineHeight: 1.8 }}>
            {policy.applyMethod || `신청 방법 정보가 없습니다. ${NO_DATA}`}
          </Typography>

          {/* 문의처 */}
          <SectionTitle>문의처</SectionTitle>
          <Divider sx={{ mb: 1.5 }} />
          {policy.contacts?.length > 0 ? (
            policy.contacts.map((c, i) => (
              <Typography key={i} variant="body2" mb={0.5}>
                📞 {c.name}&nbsp;&nbsp;{c.phone}
              </Typography>
            ))
          ) : (
            <Typography variant="body2" color="text.secondary">문의처 정보가 없습니다.</Typography>
          )}

          {/* 관련 정보 */}
          <SectionTitle>관련 정보</SectionTitle>
          <Divider sx={{ mb: 1.5 }} />
          <Box sx={{ display: "flex", flexDirection: "column", gap: 0.5 }}>
            {policy.law && (
              <Typography variant="body2">📋 관련 법령: {policy.law}</Typography>
            )}
            {policy.formFile && (
              <Typography variant="body2">
                📎 서식 파일: <Button size="small" variant="text" sx={{ p: 0, minWidth: 0 }}>신청서 다운로드</Button>
              </Typography>
            )}
            {policy.homepage && (
              <Typography variant="body2">
                🌐 홈페이지:{" "}
                <Button
                  size="small"
                  variant="text"
                  sx={{ p: 0, minWidth: 0 }}
                  onClick={() => window.open(policy.homepage, "_blank")}
                >
                  바로가기
                </Button>
              </Typography>
            )}
          </Box>
        </Paper>
      </Container>

      {/* 하단 고정 버튼 */}
      <Box sx={{
        position: "fixed", bottom: 0, left: 0, right: 0,
        bgcolor: "white", borderTop: "1px solid #E8F4F5",
        px: 2, py: 1.5, display: "flex", gap: 1, justifyContent: "center",
        maxWidth: 768, mx: "auto",
      }}>
        <Button
          variant={bookmarked ? "contained" : "outlined"}
          startIcon={bookmarked ? <BookmarkIcon /> : <BookmarkBorderIcon />}
          onClick={handleBookmark}
          sx={{ flex: 1, maxWidth: 200 }}
        >
          {bookmarked ? "북마크됨" : "북마크"}
        </Button>
        <Button
          variant="contained"
          endIcon={<OpenInNewIcon />}
          disabled={!policy.detailUrl}
          onClick={() => policy.detailUrl && window.open(policy.detailUrl, "_blank")}
          sx={{ flex: 1, maxWidth: 200 }}
        >
          {policy.detailUrl ? "원문 보러가기" : "원문 링크 없음"}
        </Button>
      </Box>

      <Snackbar open={toast.open} autoHideDuration={2000} onClose={() => setToast({ ...toast, open: false })} anchorOrigin={{ vertical: "top", horizontal: "center" }}>
        <Alert severity={toast.severity}>{toast.msg}</Alert>
      </Snackbar>
    </Box>
  );
}
