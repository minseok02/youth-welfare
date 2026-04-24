import { useState, useEffect, useCallback } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  Box, Container, Typography, Tabs, Tab, TextField, Button,
  RadioGroup, FormControlLabel, Radio, FormControl, FormLabel,
  LinearProgress, Chip, Card, CardContent, Select, MenuItem,
  Switch, Checkbox, FormGroup, Divider, Paper, IconButton,
  Dialog, DialogTitle, DialogContent, DialogActions,
  Snackbar, Alert, Grid, CircularProgress,
} from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";
import DragIndicatorIcon from "@mui/icons-material/DragIndicator";
import BookmarkIcon from "@mui/icons-material/Bookmark";
import Header from "../components/Header";
import api from "../lib/axios";
import { useAuthStore } from "../store/authStore";

const INCOME_ROWS = [
  { value: 1, left: "1~2분위 (하위 20%)", right: "기초생활수급자" },
  { value: 3, left: "3~4분위 (하위 40%)", right: "차상위계층" },
  { value: 5, left: "5~6분위 (중간)", right: "소득 하위 50% 이하" },
  { value: 7, left: "7~8분위 (상위 40%)", right: "소득 중간 (50~100%)" },
  { value: 9, left: "9~10분위 (상위 20%)", right: "소득 상위 (100% 초과)" },
];

const PRIORITY_OPTIONS = [
  { value: "HOUSING",       label: "주거" },
  { value: "JOB",           label: "일자리" },
  { value: "EDUCATION",     label: "교육·직업훈련" },
  { value: "FINANCE",       label: "금융·생활" },
  { value: "CULTURE",       label: "문화·여가" },
  { value: "PARTICIPATION", label: "참여·기회" },
  { value: "FAMILY",        label: "가족·돌봄" },
  { value: "DEADLINE",      label: "마감임박" },
];

const REGIONS = ["서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주"];

const DISTRICT_MAP = {
  "서울": ["강남구", "강동구", "강북구", "강서구", "관악구", "광진구", "구로구", "금천구", "노원구", "도봉구", "동대문구", "동작구", "마포구", "서대문구", "서초구", "성동구", "성북구", "송파구", "양천구", "영등포구", "용산구", "은평구", "종로구", "중구", "중랑구"],
  "부산": ["강서구", "금정구", "기장군", "남구", "동구", "동래구", "부산진구", "북구", "사상구", "사하구", "서구", "수영구", "연제구", "영도구", "중구", "해운대구"],
  "대구": ["남구", "달서구", "달성군", "동구", "북구", "서구", "수성구", "중구"],
  "인천": ["강화군", "계양구", "남동구", "동구", "미추홀구", "부평구", "서구", "연수구", "옹진군", "중구"],
  "광주": ["광산구", "남구", "동구", "북구", "서구"],
  "대전": ["대덕구", "동구", "서구", "유성구", "중구"],
  "울산": ["남구", "동구", "북구", "울주군", "중구"],
  "경기": ["가평군", "고양시", "과천시", "광명시", "광주시", "구리시", "군포시", "김포시", "남양주시", "동두천시", "부천시", "성남시", "수원시", "시흥시", "안산시", "안성시", "안양시", "양주시", "양평군", "여주시", "연천군", "오산시", "용인시", "의왕시", "의정부시", "이천시", "파주시", "평택시", "포천시", "하남시", "화성시"],
  "강원": ["강릉시", "고성군", "동해시", "삼척시", "속초시", "양구군", "양양군", "영월군", "원주시", "인제군", "정선군", "철원군", "춘천시", "태백시", "평창군", "홍천군", "화천군", "횡성군"],
  "충북": ["괴산군", "단양군", "보은군", "영동군", "옥천군", "음성군", "제천시", "증평군", "진천군", "청주시", "충주시"],
  "충남": ["계룡시", "공주시", "금산군", "논산시", "당진시", "보령시", "부여군", "서산시", "서천군", "아산시", "예산군", "천안시", "청양군", "태안군", "홍성군"],
  "전북": ["고창군", "군산시", "김제시", "남원시", "무주군", "부안군", "순창군", "완주군", "익산시", "임실군", "장수군", "전주시", "정읍시", "진안군"],
  "전남": ["강진군", "고흥군", "곡성군", "광양시", "구례군", "나주시", "담양군", "목포시", "무안군", "보성군", "순천시", "신안군", "여수시", "영광군", "영암군", "완도군", "장성군", "장흥군", "진도군", "함평군", "해남군", "화순군"],
  "경북": ["경산시", "경주시", "고령군", "구미시", "군위군", "김천시", "문경시", "봉화군", "상주시", "성주군", "안동시", "영덕군", "영양군", "영주시", "영천시", "예천군", "울릉군", "울진군", "의성군", "청도군", "청송군", "칠곡군", "포항시"],
  "경남": ["거제시", "거창군", "고성군", "김해시", "남해군", "밀양시", "사천시", "산청군", "양산시", "의령군", "진주시", "창녕군", "창원시", "통영시", "하동군", "함안군", "함양군", "합천군"],
  "제주": ["서귀포시", "제주시"],
};

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

const mapBookmark = (policy) => ({
  id: policy.id,
  title: policy.title,
  category: policy.unifiedCategory || "기타",
  dday: formatDday(policy.applyEndDate, policy.status),
  source: policy.hostOrg || policy.applyMethodName || "출처 정보 없음",
});

export default function MyPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { isLoggedIn, user, logout, filterSettings, setFilterSettings } = useAuthStore();
  const [tabValue, setTabValue] = useState(parseInt(searchParams.get("tab") ?? "0"));
  const [toast, setToast] = useState({ open: false, msg: "", severity: "success" });
  const showToast = useCallback((msg, severity = "success") => {
    setToast({ open: true, msg, severity });
  }, []);

  useEffect(() => {
    if (!isLoggedIn) navigate("/login");
  }, [isLoggedIn, navigate]);

  // 내 정보
  const [myInfo, setMyInfo] = useState({
    name: "", email: user?.email ?? "",
    birthYear: "", birthMonth: "", birthDay: "",
    region: "", subRegion: "", income: "", employ: "",
  });
  const [editing, setEditing] = useState(false);
  const [infoLoading, setInfoLoading] = useState(false);
  const [reloginModal, setReloginModal] = useState(false);

  const completionFields = [myInfo.birthYear, myInfo.region, myInfo.income, myInfo.employ];
  const completionPct = Math.round((completionFields.filter(Boolean).length / completionFields.length) * 100);

  // 우선순위
  const [priorities, setPriorities] = useState([]);
  const [priorityLoading, setPriorityLoading] = useState(false);
  const [dragIdx, setDragIdx] = useState(null);

  // 북마크
  const [bookmarks, setBookmarks] = useState([]);
  const [bookmarkLoading, setBookmarkLoading] = useState(false);

  // 알림
  const [notifOn, setNotifOn] = useState(false);
  const [notifFreq, setNotifFreq] = useState("DAILY");
  const [notifLoading, setNotifLoading] = useState(false);

  // 프로필 + 우선순위 + 북마크 초기 로드
  useEffect(() => {
    if (!isLoggedIn) return;
    const controller = new AbortController();

    const fetchProfile = async () => {
      setInfoLoading(true);
      try {
        const { data } = await api.get("/api/users/me", { signal: controller.signal });
        const p = data.data ?? {};
        const bd = p.birthDate ? p.birthDate.split("-") : ["", "", ""];
        setMyInfo({
          name: p.name ?? "",
          email: p.email ?? "",
          birthYear: bd[0] ?? "",
          birthMonth: bd[1] ? String(parseInt(bd[1])) : "",
          birthDay:   bd[2] ? String(parseInt(bd[2])) : "",
          region:     p.sido ?? "",
          subRegion:  p.sgg ?? "",
          income:     p.incomeLevel != null ? String(p.incomeLevel) : "",
          employ:     p.employmentStatus ?? "",
        });
        setNotifOn(p.notificationYn ?? false);
        setNotifFreq(p.notificationPeriod === "WEEKLY" ? "WEEKLY" : "DAILY");
        setPriorities((p.priorities ?? []).map((item) => item.code));
      } catch (err) {
        if (err.name === "CanceledError" || err.code === "ERR_CANCELED") return;
        showToast("프로필을 불러오지 못했습니다", "error");
      } finally {
        if (!controller.signal.aborted) setInfoLoading(false);
      }
    };

    const fetchBookmarks = async () => {
      setBookmarkLoading(true);
      try {
        const { data } = await api.get("/api/users/me/bookmarks", { signal: controller.signal });
        setBookmarks((data.data ?? []).map(mapBookmark));
      } catch (err) {
        if (err.name === "CanceledError" || err.code === "ERR_CANCELED") return;
        showToast("북마크 목록을 불러오지 못했습니다", "error");
      } finally {
        if (!controller.signal.aborted) setBookmarkLoading(false);
      }
    };

    fetchProfile();
    fetchBookmarks();
    return () => controller.abort();
  }, [isLoggedIn, showToast]);

  // 필터 기본값
  const [filterIncludeExpired, setFilterIncludeExpired] = useState(filterSettings?.includeExpired ?? false);
  const [filterSources, setFilterSources] = useState({ 온통청년: true, "복지로 중앙": true, "복지로 지자체": true });

  // 계정 탭
  const [currPw, setCurrPw] = useState("");
  const [newPw, setNewPw] = useState("");
  const [newPwConfirm, setNewPwConfirm] = useState("");
  const [pwLoading, setPwLoading] = useState(false);
  const [withdrawModal, setWithdrawModal] = useState(false);
  const [withdrawPw, setWithdrawPw] = useState("");

  const togglePriority = (val) => {
    if (priorities.includes(val)) setPriorities(priorities.filter((p) => p !== val));
    else if (priorities.length < 5) setPriorities([...priorities, val]);
  };
  const removePriority = (val) => setPriorities(priorities.filter((p) => p !== val));
  const handleDragStart = (idx) => setDragIdx(idx);
  const handleDragOver = (e, idx) => {
    e.preventDefault();
    if (dragIdx === null || dragIdx === idx) return;
    const updated = [...priorities];
    const [moved] = updated.splice(dragIdx, 1);
    updated.splice(idx, 0, moved);
    setPriorities(updated);
    setDragIdx(idx);
  };
  const handleDragEnd = () => setDragIdx(null);

  const handleSaveInfo = async () => {
    setInfoLoading(true);
    try {
      const birthDate =
        myInfo.birthYear && myInfo.birthMonth && myInfo.birthDay
          ? `${myInfo.birthYear}-${String(myInfo.birthMonth).padStart(2, "0")}-${String(myInfo.birthDay).padStart(2, "0")}`
          : undefined;
      await api.put("/api/users/me", {
        name:             myInfo.name || undefined,
        birthDate:        birthDate,
        sido:             myInfo.region || undefined,
        sgg:              myInfo.subRegion || undefined,
        incomeLevel:      myInfo.income ? parseInt(myInfo.income) : undefined,
        employmentStatus: myInfo.employ || undefined,
      });
      setEditing(false);
      showToast("저장되었습니다");
    } catch {
      showToast("저장에 실패했습니다", "error");
    } finally {
      setInfoLoading(false);
    }
  };

  const handleSavePriorities = async () => {
    setPriorityLoading(true);
    try {
      await api.put("/api/users/me/priorities", { priorityCodes: priorities });
      showToast("우선순위가 저장되었습니다");
    } catch {
      showToast("우선순위 저장에 실패했습니다", "error");
    } finally {
      setPriorityLoading(false);
    }
  };

  const handleSaveNotif = async () => {
    setNotifLoading(true);
    try {
      await api.put("/api/users/me", {
        notificationYn:     notifOn,
        notificationPeriod: notifOn ? notifFreq : "NONE",
      });
      showToast("알림 설정이 저장되었습니다");
    } catch {
      showToast("알림 설정 저장에 실패했습니다", "error");
    } finally {
      setNotifLoading(false);
    }
  };

  const handlePasswordChange = async () => {
    if (!currPw || !newPw || newPw !== newPwConfirm) { showToast("입력값을 확인해주세요", "error"); return; }
    if (newPw.length < 8) { showToast("새 비밀번호는 8자 이상이어야 합니다", "error"); return; }
    setPwLoading(true);
    try {
      await api.patch("/api/users/me/password", { currentPassword: currPw, newPassword: newPw });
      showToast("비밀번호가 변경되었습니다. 다시 로그인해주세요");
      setCurrPw(""); setNewPw(""); setNewPwConfirm("");
      setTimeout(() => { logout(); navigate("/login"); }, 1500);
    } catch (err) {
      const code = err.response?.data?.errorCode;
      showToast(code === "A004" ? "현재 비밀번호가 올바르지 않습니다" : "비밀번호 변경에 실패했습니다", "error");
    } finally {
      setPwLoading(false);
    }
  };

  const handleWithdraw = async () => {
    if (!withdrawPw) { showToast("비밀번호를 입력해주세요", "error"); return; }
    try {
      await api.delete("/api/users/me", { data: { password: withdrawPw } });
      setWithdrawModal(false);
      setWithdrawPw("");
      logout();
      navigate("/");
    } catch (err) {
      const code = err.response?.data?.errorCode;
      showToast(code === "A004" ? "비밀번호가 올바르지 않습니다" : "탈퇴 처리에 실패했습니다", "error");
    }
  };

  const handleRelogin = () => {
    setReloginModal(false);
    logout();
    navigate("/login");
  };

  const ddayColor = (dday) => {
    if (dday === "종료") return "default";
    if (dday === "상시") return "success";
    if (dday === "D-Day") return "error";
    const n = parseInt(dday.replace("D-", ""));
    return n <= 14 ? "error" : "primary";
  };

  if (!isLoggedIn) return null;

  return (
    <Box sx={{ minHeight: "100vh", bgcolor: "background.default" }}>
      <Header />

      <Container maxWidth="md" sx={{ py: 3 }}>
        <Typography variant="h5" fontWeight={700} mb={2}>마이페이지</Typography>

        {/* 프로필 완성도 바 (100% 미만일 때만) */}
        {completionPct < 100 && (
          <Card sx={{ mb: 3 }}>
            <CardContent>
              <Box sx={{ display: "flex", justifyContent: "space-between", mb: 0.5 }}>
                <Typography variant="body2" fontWeight={600}>📋 프로필 완성도</Typography>
                <Typography variant="body2" color="primary" fontWeight={700}>{completionPct}%</Typography>
              </Box>
              <LinearProgress variant="determinate" value={completionPct} sx={{ borderRadius: 4, height: 8, mb: 1 }} />
              <Typography variant="caption" color="text.secondary">
                {[!myInfo.birthYear && "생년월일", !myInfo.region && "주소", !myInfo.income && "소득수준", !myInfo.employ && "취업상태"].filter(Boolean).join(", ")}을(를) 입력하면 더 정확한 추천을 받을 수 있어요
              </Typography>
              <Box sx={{ mt: 1 }}>
                <Button size="small" variant="outlined" onClick={() => setTabValue(0)}>지금 완성하기</Button>
              </Box>
            </CardContent>
          </Card>
        )}

        {/* 탭 */}
        <Tabs value={tabValue} onChange={(_, v) => setTabValue(v)} variant="scrollable" scrollButtons="auto" sx={{ borderBottom: "1px solid #e0e0e0", mb: 3 }}>
          <Tab label="내 정보" />
          <Tab label="우선순위" />
          <Tab label="북마크" />
          <Tab label="알림 설정" />
          <Tab label="필터 기본값" />
          <Tab label="계정" />
        </Tabs>

        {/* ── 탭 1: 내 정보 ── */}
        {tabValue === 0 && (
          <Box sx={{ display: "flex", flexDirection: "column", gap: 2.5 }}>
            {infoLoading && <LinearProgress />}
            <TextField label="이름" value={myInfo.name} onChange={(e) => setMyInfo({ ...myInfo, name: e.target.value })} disabled={!editing} fullWidth />

            <Box>
              <Box sx={{ display: "flex", gap: 1 }}>
                <TextField
                  label="이메일"
                  value={myInfo.email}
                  onChange={(e) => { setMyInfo({ ...myInfo, email: e.target.value }); }}
                  disabled={!editing}
                  fullWidth
                />
                {editing && (
                  <Button variant="outlined" onClick={() => showToast("이메일 중복확인 연동은 아직 준비 중입니다", "info")} sx={{ minWidth: 90, whiteSpace: "nowrap" }}>
                    중복확인
                  </Button>
                )}
              </Box>
            </Box>

            <Box>
              <Typography variant="body2" fontWeight={600} mb={0.5}>생년월일</Typography>
              <Box sx={{ display: "flex", gap: 1 }}>
                <FormControl size="small" sx={{ flex: 3 }} disabled={!editing}>
                  <Select value={myInfo.birthYear} onChange={(e) => setMyInfo({ ...myInfo, birthYear: e.target.value, birthDay: "" })} displayEmpty MenuProps={{ PaperProps: { sx: { maxHeight: 240 } } }}>
                    <MenuItem value=""><em>년도</em></MenuItem>
                    {Array.from({ length: 127 }, (_, i) => 2026 - i).map((y) => (
                      <MenuItem key={y} value={String(y)}>{y}년</MenuItem>
                    ))}
                  </Select>
                </FormControl>
                <FormControl size="small" sx={{ flex: 2 }} disabled={!editing}>
                  <Select value={myInfo.birthMonth} onChange={(e) => setMyInfo({ ...myInfo, birthMonth: e.target.value, birthDay: "" })} displayEmpty>
                    <MenuItem value=""><em>월</em></MenuItem>
                    {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
                      <MenuItem key={m} value={String(m)}>{m}월</MenuItem>
                    ))}
                  </Select>
                </FormControl>
                <FormControl size="small" sx={{ flex: 2 }} disabled={!editing || !myInfo.birthMonth}>
                  <Select value={myInfo.birthDay} onChange={(e) => setMyInfo({ ...myInfo, birthDay: e.target.value })} displayEmpty>
                    <MenuItem value=""><em>일</em></MenuItem>
                    {Array.from({ length: myInfo.birthYear && myInfo.birthMonth ? new Date(myInfo.birthYear, myInfo.birthMonth, 0).getDate() : 31 }, (_, i) => i + 1).map((d) => (
                      <MenuItem key={d} value={String(d)}>{d}일</MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Box>
            </Box>

            <Box>
              <Typography variant="body2" fontWeight={600} mb={1}>주소</Typography>
              <Box sx={{ display: "flex", gap: 1 }}>
                <FormControl size="small" sx={{ flex: 1 }} disabled={!editing}>
                  <Select value={myInfo.region} onChange={(e) => setMyInfo({ ...myInfo, region: e.target.value, subRegion: "" })} displayEmpty>
                    <MenuItem value=""><em>시/도 선택</em></MenuItem>
                    {REGIONS.map((r) => <MenuItem key={r} value={r}>{r}</MenuItem>)}
                  </Select>
                </FormControl>
                {DISTRICT_MAP[myInfo.region]?.length > 0 && (
                  <FormControl size="small" sx={{ flex: 1 }} disabled={!editing}>
                    <Select value={myInfo.subRegion} onChange={(e) => setMyInfo({ ...myInfo, subRegion: e.target.value })} displayEmpty MenuProps={{ PaperProps: { sx: { maxHeight: 240 } } }}>
                      <MenuItem value=""><em>시/군/구 선택</em></MenuItem>
                      {DISTRICT_MAP[myInfo.region].map((d) => <MenuItem key={d} value={d}>{d}</MenuItem>)}
                    </Select>
                  </FormControl>
                )}
              </Box>
            </Box>

            {/* 소득수준 */}
            <Box>
              <Typography variant="body2" fontWeight={600} mb={1}>소득수준</Typography>
              <Box sx={{ border: "1px solid #e0e0e0", borderRadius: 2, overflow: "hidden", opacity: editing ? 1 : 0.6, pointerEvents: editing ? "auto" : "none" }}>
                <Box sx={{ display: "grid", gridTemplateColumns: "1fr 1fr", borderBottom: "1px solid #e0e0e0" }}>
                  <Typography variant="caption" sx={{ p: 1, bgcolor: "#f9f9f9", fontWeight: 700, textAlign: "center" }}>분위 기준</Typography>
                  <Typography variant="caption" sx={{ p: 1, bgcolor: "#f9f9f9", fontWeight: 700, textAlign: "center", borderLeft: "1px solid #e0e0e0" }}>보기 쉬운 기준</Typography>
                </Box>
                {INCOME_ROWS.map((row) => (
                  <Box
                    key={row.value}
                    onClick={() => editing && setMyInfo({ ...myInfo, income: String(row.value) })}
                    sx={{
                      display: "grid", gridTemplateColumns: "1fr 1fr",
                      cursor: editing ? "pointer" : "default",
                      bgcolor: myInfo.income === String(row.value) ? "rgba(2,128,144,0.08)" : "transparent",
                      borderBottom: "1px solid #f0f0f0",
                    }}
                  >
                    <Box sx={{ display: "flex", alignItems: "center", p: 1, gap: 0.5 }}>
                      <Radio size="small" checked={myInfo.income === String(row.value)} sx={{ p: 0.5 }} />
                      <Typography variant="caption">{row.left}</Typography>
                    </Box>
                    <Box sx={{ display: "flex", alignItems: "center", p: 1, gap: 0.5, borderLeft: "1px solid #e0e0e0" }}>
                      <Radio size="small" checked={myInfo.income === String(row.value)} sx={{ p: 0.5 }} />
                      <Typography variant="caption">{row.right}</Typography>
                    </Box>
                  </Box>
                ))}
              </Box>
            </Box>

            {/* 취업상태 */}
            <FormControl disabled={!editing}>
              <FormLabel sx={{ fontSize: 14, fontWeight: 600 }}>취업상태</FormLabel>
              <RadioGroup row value={myInfo.employ} onChange={(e) => setMyInfo({ ...myInfo, employ: e.target.value })}>
                {["재직중", "구직중", "학생", "기타"].map((v) => (
                  <FormControlLabel key={v} value={v} control={<Radio size="small" />} label={v} />
                ))}
              </RadioGroup>
            </FormControl>

            <Box sx={{ display: "flex", gap: 1 }}>
              <Button
                variant={editing ? "outlined" : "contained"}
                onClick={() => { if (editing) setEditing(false); else setEditing(true); }}
                fullWidth
                disabled={editing || infoLoading}
              >
                수정
              </Button>
              <Button
                variant={editing ? "contained" : "outlined"}
                onClick={handleSaveInfo}
                fullWidth
                disabled={!editing || infoLoading}
              >
                {infoLoading ? <CircularProgress size={20} color="inherit" /> : "저장"}
              </Button>
            </Box>
          </Box>
        )}

        {/* ── 탭 2: 우선순위 ── */}
        {tabValue === 1 && (
          <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
            <Box>
              <Typography variant="body1" fontWeight={700}>어떤 정책을 우선적으로 받고 싶으세요?</Typography>
              <Typography variant="body2" color="text.secondary">최대 5개 선택 · 순서가 중요해요</Typography>
            </Box>
            <Grid container spacing={1}>
              {PRIORITY_OPTIONS.map((opt) => {
                const selected = priorities.includes(opt.value);
                return (
                  <Grid item xs={4} key={opt.value}>
                    <Button
                      variant={selected ? "contained" : "outlined"}
                      fullWidth
                      onClick={() => togglePriority(opt.value)}
                      disabled={!selected && priorities.length >= 5}
                      sx={{ py: 1.5, fontSize: 13, borderRadius: 2 }}
                    >
                      {opt.label}
                    </Button>
                  </Grid>
                );
              })}
            </Grid>
            {priorities.length > 0 && (
              <Box>
                <Typography variant="body2" fontWeight={600} mb={1}>설정된 우선순위 (드래그로 순서 조정)</Typography>
                <Paper variant="outlined" sx={{ borderRadius: 2, overflow: "hidden" }}>
                  {priorities.map((val, idx) => {
                    const opt = PRIORITY_OPTIONS.find((o) => o.value === val);
                    return (
                      <Box
                        key={val}
                        draggable
                        onDragStart={() => handleDragStart(idx)}
                        onDragOver={(e) => handleDragOver(e, idx)}
                        onDragEnd={handleDragEnd}
                        sx={{
                          display: "flex", alignItems: "center", px: 2, py: 1.2,
                          borderBottom: idx < priorities.length - 1 ? "1px solid #f0f0f0" : "none",
                          bgcolor: dragIdx === idx ? "#f0f8ff" : "transparent",
                          cursor: "grab",
                        }}
                      >
                        <DragIndicatorIcon sx={{ color: "text.disabled", mr: 1, fontSize: 18 }} />
                        <Typography variant="body2" color="text.secondary" sx={{ minWidth: 50, fontSize: 12 }}>{idx + 1}순위</Typography>
                        <Typography variant="body2" fontWeight={600} sx={{ flex: 1 }}>{opt?.icon} {opt?.label}</Typography>
                        <IconButton size="small" onClick={() => removePriority(val)}><CloseIcon fontSize="small" /></IconButton>
                      </Box>
                    );
                  })}
                </Paper>
              </Box>
            )}
            <Button
              variant="contained"
              fullWidth
              onClick={handleSavePriorities}
              disabled={priorityLoading}
            >
              {priorityLoading ? <CircularProgress size={20} color="inherit" /> : "저장"}
            </Button>
          </Box>
        )}

        {/* ── 탭 3: 북마크 ── */}
        {tabValue === 2 && (
          <Box>
            <Typography variant="subtitle1" fontWeight={700} mb={2}>
              북마크한 정책 ({bookmarks.length}건)
            </Typography>
            {bookmarkLoading ? (
              <Box sx={{ display: "flex", justifyContent: "center", py: 8 }}>
                <CircularProgress />
              </Box>
            ) : bookmarks.length > 0 ? (
              <Box sx={{ display: "flex", flexDirection: "column", gap: 1.5 }}>
                {bookmarks.map((p) => (
                  <Card
                    key={p.id}
                    sx={{ cursor: "pointer", "&:hover": { boxShadow: "0 4px 16px rgba(2,128,144,0.12)" } }}
                    onClick={() => navigate(`/policies/${p.id}`)}
                  >
                    <CardContent sx={{ display: "flex", alignItems: "center", gap: 1.5, py: "12px !important" }}>
                      <Chip label={p.category} size="small" color="primary" variant="outlined" />
                      <Chip label={p.dday} size="small" color={ddayColor(p.dday)} />
                      <Typography variant="body2" fontWeight={600} sx={{ flex: 1 }}>{p.title}</Typography>
                      <Typography variant="caption" color="text.secondary">{p.source}</Typography>
                      <Button
                        size="small"
                        variant="outlined"
                        color="warning"
                        onClick={async (e) => {
                          e.stopPropagation();
                          try {
                            await api.post(`/api/policies/${p.id}/bookmark`);
                            setBookmarks((prev) => prev.filter((b) => b.id !== p.id));
                          } catch {
                            showToast("북마크 해제에 실패했습니다", "error");
                          }
                        }}
                        sx={{ fontSize: 11, minWidth: 60 }}
                      >
                        해제
                      </Button>
                    </CardContent>
                  </Card>
                ))}
              </Box>
            ) : (
              <Box sx={{ textAlign: "center", py: 8, color: "text.secondary" }}>
                <Typography fontSize={40}>📭</Typography>
                <Typography mt={1} fontWeight={600}>북마크한 정책이 없어요</Typography>
                <Typography variant="body2" mt={0.5}>관심 정책을 저장해보세요</Typography>
              </Box>
            )}
          </Box>
        )}

        {/* ── 탭 4: 알림 설정 ── */}
        {tabValue === 3 && (
          <Box sx={{ display: "flex", flexDirection: "column", gap: 2.5 }}>
            <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <Box>
                <Typography variant="body1" fontWeight={600}>알림 수신</Typography>
                <Typography variant="body2" color="text.secondary">이메일로 맞춤 정책을 알려드려요</Typography>
              </Box>
              <Switch checked={notifOn} onChange={(e) => setNotifOn(e.target.checked)} color="primary" />
            </Box>

            {notifOn && (
              <>
                <Divider />
                <Box>
                  <Typography variant="body2" fontWeight={600} mb={1}>수신 이메일</Typography>
                  <TextField value={user?.email ?? ""} disabled fullWidth size="small" />
                  <Typography variant="caption" color="text.secondary">가입 이메일로 발송됩니다</Typography>
                </Box>
                <FormControl>
                  <FormLabel sx={{ fontSize: 14, fontWeight: 600 }}>알림 주기</FormLabel>
                  <RadioGroup value={notifFreq} onChange={(e) => setNotifFreq(e.target.value)}>
                    <FormControlLabel value="DAILY" control={<Radio size="small" />} label="매일 오전 8시" />
                    <FormControlLabel value="WEEKLY" control={<Radio size="small" />} label="주 1회 (월요일 오전 8시)" />
                  </RadioGroup>
                </FormControl>
              </>
            )}
            <Button
              variant="contained"
              fullWidth
              onClick={handleSaveNotif}
              disabled={notifLoading}
            >
              {notifLoading ? <CircularProgress size={20} color="inherit" /> : "저장"}
            </Button>
            <Typography variant="caption" color="text.secondary">
              * 수신 거부는 발송된 이메일 하단 링크로 가능합니다
            </Typography>
          </Box>
        )}

        {/* ── 탭 5: 필터 기본값 ── */}
        {tabValue === 4 && (
          <Box sx={{ display: "flex", flexDirection: "column", gap: 2.5 }}>
            <Typography variant="body2" color="text.secondary">
              메인 페이지 접속 시 기본으로 적용되는 필터값을 설정해요
            </Typography>
            <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <Box>
                <Typography variant="body2" fontWeight={600}>종료된 정책 보기</Typography>
                <Typography variant="caption" color="text.secondary">
                  {filterIncludeExpired ? "ON: 종료된 정책도 함께 표시" : "OFF: 진행 중인 정책만 표시"}
                </Typography>
              </Box>
              <Switch checked={filterIncludeExpired} onChange={(e) => setFilterIncludeExpired(e.target.checked)} color="primary" />
            </Box>
            <Box>
              <Typography variant="body2" fontWeight={600} mb={1}>출처 기본값</Typography>
              <FormGroup row>
                {["온통청년", "복지로 중앙", "복지로 지자체"].map((src) => (
                  <FormControlLabel
                    key={src}
                    control={
                      <Checkbox
                        checked={filterSources[src]}
                        onChange={(e) => setFilterSources({ ...filterSources, [src]: e.target.checked })}
                        size="small"
                      />
                    }
                    label={src}
                  />
                ))}
              </FormGroup>
            </Box>
            <Button variant="contained" fullWidth onClick={() => { setFilterSettings({ includeExpired: filterIncludeExpired }); showToast("기본값이 저장되었습니다"); }}>
              저장
            </Button>
            <Typography variant="caption" color="text.secondary">저장된 설정은 다음 접속부터 적용돼요</Typography>
          </Box>
        )}

        {/* ── 탭 6: 계정 ── */}
        {tabValue === 5 && (
          <Box>
            <Typography variant="subtitle1" fontWeight={700} mb={2}>비밀번호 변경</Typography>
            <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
              <TextField label="현재 비밀번호" type="password" value={currPw} onChange={(e) => setCurrPw(e.target.value)} fullWidth />
              <TextField label="새 비밀번호" type="password" value={newPw} onChange={(e) => setNewPw(e.target.value)} fullWidth />
              <TextField
                label="새 비밀번호 확인"
                type="password"
                value={newPwConfirm}
                onChange={(e) => setNewPwConfirm(e.target.value)}
                fullWidth
                error={newPwConfirm.length > 0 && newPw !== newPwConfirm}
                helperText={newPwConfirm.length > 0 && newPw !== newPwConfirm ? "비밀번호가 일치하지 않습니다" : ""}
              />
              <Button variant="contained" fullWidth onClick={handlePasswordChange} disabled={pwLoading}>
                {pwLoading ? <CircularProgress size={20} color="inherit" /> : "비밀번호 변경"}
              </Button>
            </Box>

            <Divider sx={{ my: 4 }} />

            <Typography variant="subtitle1" fontWeight={700} mb={1}>회원탈퇴</Typography>
            <Typography variant="body2" color="text.secondary" mb={2}>
              탈퇴 시 모든 정보가 삭제되며 복구할 수 없습니다
            </Typography>
            <Button
              variant="outlined"
              fullWidth
              color="error"
              onClick={() => setWithdrawModal(true)}
            >
              회원탈퇴
            </Button>
          </Box>
        )}
      </Container>

      {/* 재로그인 모달 */}
      <Dialog open={reloginModal} onClose={() => setReloginModal(false)} maxWidth="xs" fullWidth>
        <DialogTitle>보안을 위해 다시 로그인해주세요</DialogTitle>
        <DialogContent>
          <Typography variant="body2">변경사항이 저장되었습니다. 보안을 위해 다시 로그인이 필요합니다.</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleRelogin} variant="contained">확인</Button>
        </DialogActions>
      </Dialog>

      {/* 회원탈퇴 확인 모달 */}
      <Dialog open={withdrawModal} onClose={() => { setWithdrawModal(false); setWithdrawPw(""); }} maxWidth="xs" fullWidth>
        <DialogTitle>정말 탈퇴하시겠습니까?</DialogTitle>
        <DialogContent>
          <Typography variant="body2" mb={2}>탈퇴 시 북마크, 추천 기록이 모두 삭제됩니다</Typography>
          <TextField
            label="비밀번호 확인"
            type="password"
            value={withdrawPw}
            onChange={(e) => setWithdrawPw(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleWithdraw()}
            fullWidth
            size="small"
            autoFocus
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => { setWithdrawModal(false); setWithdrawPw(""); }}>취소</Button>
          <Button onClick={handleWithdraw} variant="contained" color="error">탈퇴하기</Button>
        </DialogActions>
      </Dialog>

      <Snackbar open={toast.open} autoHideDuration={2500} onClose={() => setToast({ ...toast, open: false })} anchorOrigin={{ vertical: "bottom", horizontal: "center" }}>
        <Alert severity={toast.severity}>{toast.msg}</Alert>
      </Snackbar>
    </Box>
  );
}
