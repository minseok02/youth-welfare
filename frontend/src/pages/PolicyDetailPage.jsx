import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
  Box,
  Container,
  Typography,
  Chip,
  Button,
  Divider,
  IconButton,
  Snackbar,
  Alert,
  Paper,
  CircularProgress,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import BookmarkBorderIcon from "@mui/icons-material/BookmarkBorder";
import BookmarkIcon from "@mui/icons-material/Bookmark";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import Header from "../components/Header";
import FloatingNav from "../components/FloatingNav";
import api from "../lib/axios";
import { useAuthStore } from "../store/authStore";

const NO_DATA = "원문에서 확인해주세요.";

const HTML_ENTITIES = {
  "&amp;": "&", "&lt;": "<", "&gt;": ">", "&quot;": '"', "&#39;": "'",
  "&nbsp;": " ", "&middot;": "·", "&bull;": "•", "&ndash;": "–",
  "&mdash;": "—", "&laquo;": "«", "&raquo;": "»", "&times;": "×",
};
const decodeHtml = (text) => {
  if (!text) return text;
  return text.replace(/&[a-zA-Z0-9#]+;/g, (entity) => HTML_ENTITIES[entity] ?? entity);
};

function SectionTitle({ children }) {
  return (
    <Typography variant="subtitle1" fontWeight={700} color="primary" mt={3} mb={1}>
      {children}
    </Typography>
  );
}

const formatDate = (value) => {
  if (!value) return null;
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return null;
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, "0")}.${String(
    date.getDate()
  ).padStart(2, "0")}`;
};

const formatPeriod = (start, end) => {
  const formattedStart = formatDate(start);
  const formattedEnd = formatDate(end);
  if (formattedStart && formattedEnd) return `${formattedStart} ~ ${formattedEnd}`;
  if (formattedStart) return `${formattedStart} ~`;
  if (formattedEnd) return `~ ${formattedEnd}`;
  return null;
};

const formatAgeRange = (minAge, maxAge) => {
  if (minAge && maxAge) return `만 ${minAge}~${maxAge}세`;
  if (minAge) return `만 ${minAge}세 이상`;
  if (maxAge) return `만 ${maxAge}세 이하`;
  return null;
};

const formatIncome = (minIncome, maxIncome) => {
  if (minIncome && maxIncome) return `소득 ${minIncome} ~ ${maxIncome}`;
  if (minIncome) return `소득 ${minIncome} 이상`;
  if (maxIncome) return `소득 ${maxIncome} 이하`;
  return null;
};

const formatSource = (sourceType) => {
  if (sourceType === "YOUTH") return "온통청년";
  if (sourceType === "BOKJIRO_CENTRAL") return "복지로 중앙";
  if (sourceType === "BOKJIRO_LOCAL") return "복지로 지자체";
  return sourceType || "출처 정보 없음";
};

// DB status만 보면 온통청년 정책이 ACTIVE인데도 신청 마감인 경우가 있어
// applyEndDate도 함께 확인한다.
const formatStatusLabel = (status, applyEndDate) => {
  if (status === "CLOSED") return "종료";
  if (applyEndDate) {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    if (new Date(`${applyEndDate}T00:00:00`) < today) return "종료";
  }
  if (status === "ACTIVE") return "진행중";
  if (status === "UPCOMING") return "예정";
  return "상태 정보 없음";
};

const formatDday = (endDate, status) => {
  if (status === "CLOSED") return "종료";
  if (!endDate) return "상시";

  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const target = new Date(`${endDate}T00:00:00`);
  if (Number.isNaN(target.getTime())) return "상시";

  const diff = Math.ceil((target - today) / 86400000);
  if (diff < 0) return "종료";
  if (diff === 0) return "D-Day";
  return `D-${diff}`;
};

const ddayColor = (dday) => {
  if (dday === "상시") return "success";
  if (dday === "종료") return "default";
  if (dday === "D-Day") return "error";
  const n = Number.parseInt(String(dday).replace("D-", ""), 10);
  return Number.isNaN(n) ? "default" : n <= 14 ? "error" : "primary";
};

const parseContacts = (raw) => {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) {
      return parsed
        .map((item) => ({
          name: item?.name || item?.deptNm || item?.orgNm || "",
          phone: item?.phone || item?.telNo || item?.contact || "",
        }))
        .filter((item) => item.name || item.phone);
    }
  } catch {
    return raw
      .split(/\n+/)
      .map((line) => line.trim())
      .filter(Boolean)
      .map((line) => ({ name: line, phone: "" }));
  }
  return [];
};

export default function PolicyDetailPage() {
  const navigate = useNavigate();
  const { id } = useParams();
  const [searchParams] = useSearchParams();
  const { isLoggedIn } = useAuthStore();

  const [policy, setPolicy] = useState(null);
  const [bookmarked, setBookmarked] = useState(false);
  const [loading, setLoading] = useState(true);
  const [toast, setToast] = useState({ open: false, msg: "", severity: "info" });

  useEffect(() => {
    const controller = new AbortController();

    const fetchPolicy = async () => {
      setLoading(true);
      try {
        const logId = searchParams.get("log_id");
        const { data } = await api.get(`/api/policies/${id}`, {
          params: {
            logId: logId || undefined,
          },
          signal: controller.signal,
        });
        setPolicy(data.data);
        setBookmarked(Boolean(data.data?.bookmarked));
      } catch (error) {
        if (error.name === "CanceledError" || error.code === "ERR_CANCELED") return;
        setToast({ open: true, msg: "정책 상세를 불러오지 못했습니다", severity: "error" });
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    };

    fetchPolicy();
    return () => controller.abort();
  }, [id, searchParams]);

  const contacts = useMemo(() => parseContacts(policy?.contactList), [policy?.contactList]);
  const visibleTags = useMemo(() => {
    if (!policy?.tags?.length) return [];

    const seen = new Set();
    return policy.tags
      .map((tag) => tag?.tagValue?.trim())
      .filter((tagValue) => {
        if (!tagValue || seen.has(tagValue)) return false;
        if (/^[A-Z0-9_]+$/.test(tagValue)) return false; // COND_AGE_MAX_39 등 내부 조건 코드 제외
        seen.add(tagValue);
        return true;
      });
  }, [policy?.tags]);

  const summaryChips = useMemo(() => {
    if (!policy) return [];
    const chips = [];
    const readableRegions = policy.regions?.filter(r => !/^\d+$/.test(r));
    if (readableRegions?.length) chips.push({ label: readableRegions.join(", "), icon: "📍" });

    const ageRange = formatAgeRange(policy.minAge, policy.maxAge);
    if (ageRange) chips.push({ label: ageRange, icon: "👤" });

    const income = formatIncome(policy.minIncome, policy.maxIncome);
    if (income) chips.push({ label: income, icon: "💰" });

    if (policy.isOnlineApply) chips.push({ label: "온라인 가능", icon: "🖥️", color: "primary" });

    const organizer = policy.hostOrg || policy.operatingOrg;
    if (organizer) chips.push({ label: organizer, icon: "🏛️" });

    const period =
      formatPeriod(policy.applyStartDate, policy.applyEndDate) ||
      formatPeriod(policy.startDate, policy.endDate);
    if (period) chips.push({ label: `신청기간: ${period}`, icon: "🗓️" });

    if (policy.lifeStage) chips.push({ label: policy.lifeStage, icon: "🌱" });

    return chips;
  }, [policy]);

  const handleBookmark = async () => {
    if (!isLoggedIn) {
      setToast({ open: true, msg: "로그인 후 이용 가능해요", severity: "info" });
      return;
    }

    try {
      await api.post(`/api/policies/${id}/bookmark`);
      const nextBookmarked = !bookmarked;
      setBookmarked(nextBookmarked);
      setPolicy((current) => (current ? { ...current, bookmarked: nextBookmarked } : current));
      setToast({
        open: true,
        msg: nextBookmarked ? "북마크에 저장했어요" : "북마크를 해제했어요",
        severity: "success",
      });
    } catch {
      setToast({ open: true, msg: "북마크 처리에 실패했습니다", severity: "error" });
    }
  };

  const policyDday = policy ? formatDday(policy.applyEndDate, policy.status) : "상시";

  return (
    <Box sx={{ minHeight: "100vh", bgcolor: "background.default", pb: 10 }}>
      <Header />

      <Container maxWidth="md" sx={{ py: 3 }}>
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

        {loading ? (
          <Paper
            elevation={0}
            sx={{
              p: 6,
              borderRadius: 3,
              border: "1px solid #E8F4F5",
              display: "flex",
              justifyContent: "center",
            }}
          >
            <CircularProgress />
          </Paper>
        ) : !policy ? (
          <Paper elevation={0} sx={{ p: 4, borderRadius: 3, border: "1px solid #E8F4F5" }}>
            <Typography variant="h6" fontWeight={700} mb={1}>
              정책 정보를 찾지 못했습니다
            </Typography>
            <Typography variant="body2" color="text.secondary">
              목록으로 돌아가 다른 정책을 선택해주세요.
            </Typography>
          </Paper>
        ) : (
          <Paper elevation={0} sx={{ p: 3, borderRadius: 3, border: "1px solid #E8F4F5" }}>
            <Box sx={{ display: "flex", gap: 1, mb: 1.5, flexWrap: "wrap" }}>
              <Chip label={policy.unifiedCategory || "기타"} color="primary" size="small" />
              <Chip label={formatSource(policy.sourceType)} variant="outlined" size="small" />
              {policy.supportCycle && (
                <Chip label={policy.supportCycle} variant="outlined" size="small" />
              )}
              {policy.provisionType && (
                <Chip label={policy.provisionType} variant="outlined" size="small" />
              )}
            </Box>

            <Typography variant="h5" fontWeight={700} mb={1.5} sx={{ lineHeight: 1.4 }}>
              {policy.title}
            </Typography>

            <Box sx={{ display: "flex", gap: 1, mb: 2, flexWrap: "wrap" }}>
              <Chip label={policyDday} color={ddayColor(policyDday)} size="small" />
              <Chip label={formatStatusLabel(policy.status, policy.applyEndDate)} size="small" variant="outlined" />
            </Box>

            <Divider />

            <SectionTitle>요약 정보</SectionTitle>
            <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap" }}>
              {summaryChips.map((item) => (
                <Chip
                  key={`${item.icon}-${item.label}`}
                  icon={<span>{item.icon}</span>}
                  label={item.label}
                  size="small"
                  variant="outlined"
                  color={item.color || "default"}
                />
              ))}
            </Box>

            <SectionTitle>정책 소개</SectionTitle>
            <Divider sx={{ mb: 1.5 }} />
            <Typography variant="body2" sx={{ whiteSpace: "pre-line", lineHeight: 1.8 }}>
              {decodeHtml(policy.description) || `정책 소개 정보가 없습니다. ${NO_DATA}`}
            </Typography>

            <SectionTitle>지원내용</SectionTitle>
            <Divider sx={{ mb: 1.5 }} />
            <Typography variant="body2" sx={{ whiteSpace: "pre-line", lineHeight: 1.8 }}>
              {decodeHtml(policy.supportDetail || policy.supportContent) || `지원 내용 정보가 없습니다. ${NO_DATA}`}
            </Typography>

            <SectionTitle>신청대상</SectionTitle>
            <Divider sx={{ mb: 1.5 }} />
            <Typography variant="body2" sx={{ whiteSpace: "pre-line", lineHeight: 1.8 }}>
              {decodeHtml(policy.targetDetail) || `신청 대상 정보가 없습니다. ${NO_DATA}`}
            </Typography>

            <SectionTitle>신청방법</SectionTitle>
            <Divider sx={{ mb: 1.5 }} />
            <Typography variant="body2" sx={{ whiteSpace: "pre-line", lineHeight: 1.8 }}>
              {decodeHtml(policy.applyMethodDetail || policy.applyMethodName) ||
                `신청 방법 정보가 없습니다. ${NO_DATA}`}
            </Typography>

            <SectionTitle>문의처</SectionTitle>
            <Divider sx={{ mb: 1.5 }} />
            {contacts.length > 0 ? (
              contacts.map((contact, index) => (
                <Typography key={`${contact.name}-${index}`} variant="body2" mb={0.5}>
                  📞 {contact.name}
                  {contact.phone ? `  ${contact.phone}` : ""}
                </Typography>
              ))
            ) : (
              <Typography variant="body2" color="text.secondary">
                문의처 정보가 없습니다.
              </Typography>
            )}

            {!!visibleTags.length && (
              <>
                <SectionTitle>관련 태그</SectionTitle>
                <Divider sx={{ mb: 1.5 }} />
                <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap" }}>
                  {visibleTags.map((tagValue) => (
                    <Chip
                      key={tagValue}
                      label={tagValue}
                      size="small"
                      variant="outlined"
                    />
                  ))}
                </Box>
              </>
            )}
          </Paper>
        )}
      </Container>

      <Box
        sx={{
          position: "fixed",
          bottom: 0,
          left: 0,
          right: 0,
          bgcolor: "white",
          borderTop: "1px solid #E8F4F5",
          px: 2,
          py: 1.5,
          display: "flex",
          gap: 1,
          justifyContent: "center",
          maxWidth: 768,
          mx: "auto",
        }}
      >
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
          disabled={!policy?.detailUrl}
          onClick={() => policy?.detailUrl && window.open(policy.detailUrl, "_blank")}
          sx={{ flex: 1, maxWidth: 200 }}
        >
          {policy?.detailUrl ? "원문 보러가기" : "원문 링크 없음"}
        </Button>
      </Box>

      <Snackbar
        open={toast.open}
        autoHideDuration={2200}
        onClose={() => setToast((prev) => ({ ...prev, open: false }))}
        anchorOrigin={{ vertical: "top", horizontal: "center" }}
      >
        <Alert severity={toast.severity}>{toast.msg}</Alert>
      </Snackbar>
      <FloatingNav />
    </Box>
  );
}
