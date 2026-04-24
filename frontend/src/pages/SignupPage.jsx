import { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Box, Paper, Typography, TextField, Button, Stepper, Step, StepLabel,
  RadioGroup, FormControlLabel, Radio, FormControl, FormLabel,
  Select, MenuItem, Chip, IconButton, Divider, Snackbar, Alert,
  CircularProgress, Link, Grid,
} from "@mui/material";
import HomeIcon from "@mui/icons-material/Home";
import CloseIcon from "@mui/icons-material/Close";
import DragIndicatorIcon from "@mui/icons-material/DragIndicator";
import { useAuthStore } from "../store/authStore";
import api from "../lib/axios";

const STEPS = ["기본 정보", "추천 정보", "우선순위"];

const INCOME_ROWS = [
  { value: 1, left: "1~2분위 (하위 20%)", right: "기초생활수급자" },
  { value: 3, left: "3~4분위 (하위 40%)", right: "차상위계층" },
  { value: 5, left: "5~6분위 (중간)", right: "소득 하위 50% 이하" },
  { value: 7, left: "7~8분위 (상위 40%)", right: "소득 중간 (50~100%)" },
  { value: 9, left: "9~10분위 (상위 20%)", right: "소득 상위 (100% 초과)" },
];

const PRIORITY_OPTIONS = [
  { value: "HOUSING", label: "주거" },
  { value: "JOB", label: "일자리" },
  { value: "EDUCATION", label: "교육" },
  { value: "FINANCE", label: "금융" },
  { value: "CULTURE", label: "문화" },
  { value: "HEALTH", label: "건강" },
  { value: "FAMILY", label: "가족" },
  { value: "SAFETY", label: "안전" },
  { value: "PARTICIPATION", label: "참여" },
  { value: "DEADLINE", label: "마감임박" },
  { value: "ONLINE", label: "온라인" },
];

const REGIONS = ["서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주"];

const REGION_TO_SIDO = {
  "서울": "서울특별시",
  "부산": "부산광역시",
  "대구": "대구광역시",
  "인천": "인천광역시",
  "광주": "광주광역시",
  "대전": "대전광역시",
  "울산": "울산광역시",
  "세종": "세종특별자치시",
  "경기": "경기도",
  "강원": "강원특별자치도",
  "충북": "충청북도",
  "충남": "충청남도",
  "전북": "전북특별자치도",
  "전남": "전라남도",
  "경북": "경상북도",
  "경남": "경상남도",
  "제주": "제주특별자치도",
};

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

export default function SignupPage() {
  const navigate = useNavigate();
  const { login } = useAuthStore();
  const [step, setStep] = useState(0);
  const [loading, setLoading] = useState(false);
  const [toast, setToast] = useState({ open: false, msg: "", severity: "info" });

  // Step 1
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [emailChecked, setEmailChecked] = useState(false);
  const [emailMsg, setEmailMsg] = useState("");
  const [pw, setPw] = useState("");
  const [pwConfirm, setPwConfirm] = useState("");

  // Step 2
  const [birthYear, setBirthYear] = useState("");
  const [birthMonth, setBirthMonth] = useState("");
  const [birthDay, setBirthDay] = useState("");
  const [region, setRegion] = useState("");
  const [subRegion, setSubRegion] = useState("");
  const [income, setIncome] = useState("");
  const [employ, setEmploy] = useState("");

  // Step 3
  const [priorities, setPriorities] = useState([]);
  const [dragIdx, setDragIdx] = useState(null);

  const showToast = (msg, severity = "info") => setToast({ open: true, msg, severity });

  // Step 1 검증
  const pwValid = pw.length >= 8 && /[a-zA-Z]/.test(pw) && /[0-9]/.test(pw);
  const pwMatch = pw === pwConfirm && pwConfirm.length > 0;
  const step1Valid = name && emailChecked && emailMsg.includes("가능") && pwValid && pwMatch;
  const birthDateComplete = birthYear && birthMonth && birthDay;

  const handleCheckEmail = async () => {
    if (!email) return;
    try {
      const { data } = await api.get(`/api/auth/check-email?email=${encodeURIComponent(email)}`);
      const available = data?.data?.available === true;
      setEmailMsg(available ? "✅ 사용 가능한 이메일입니다" : "❌ 이미 사용 중인 이메일입니다");
      setEmailChecked(available);
    } catch (err) {
      setEmailMsg(err.response?.data?.message ?? "❌ 이메일 중복확인에 실패했습니다");
      setEmailChecked(false);
    }
  };

  const handleSignup = async () => {
    setLoading(true);
    try {
      const body = {
        name,
        email,
        password: pw,
        birthDate: `${birthYear}-${String(birthMonth).padStart(2, "0")}-${String(birthDay).padStart(2, "0")}`,
        ...(region && { sido: REGION_TO_SIDO[region] ?? region }),
        ...(subRegion && { sgg: subRegion }),
        ...(income && { incomeLevel: parseInt(income, 10) }),
        ...(employ && { employmentStatus: employ }),
      };
      await api.post("/api/auth/signup", body);

      const loginResponse = await api.post("/api/auth/login", { email, password: pw });
      const accessToken = loginResponse?.data?.data?.accessToken;
      if (!accessToken) {
        throw new Error("회원가입 후 로그인 응답에 accessToken이 없습니다");
      }

      login(accessToken, { name, email });

      const profileResponse = await api.get("/api/users/me");
      const profile = profileResponse?.data?.data;
      login(accessToken, {
        name: profile?.name ?? name,
        email: profile?.email ?? email,
      });

      if (priorities.length > 0) {
        await api.put("/api/users/me/priorities", { priorityCodes: priorities });
      }

      showToast("가입 완료! 환영합니다 🎉", "success");
      setTimeout(() => navigate("/"), 1200);
    } catch (err) {
      showToast(err.response?.data?.message ?? "회원가입 중 오류가 발생했어요", "error");
    } finally {
      setLoading(false);
    }
  };

  const togglePriority = (val) => {
    if (priorities.includes(val)) {
      setPriorities(priorities.filter((p) => p !== val));
    } else if (priorities.length < 5) {
      setPriorities([...priorities, val]);
    }
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

  return (
    <Box sx={{ minHeight: "100vh", bgcolor: "background.default", display: "flex", flexDirection: "column" }}>
      {/* 헤더 */}
      <Box sx={{ bgcolor: "primary.main", px: 4, py: 1.5, display: "flex", alignItems: "center", cursor: "pointer" }} onClick={() => navigate("/")}>
        <HomeIcon sx={{ color: "white", mr: 0.5 }} />
        <Typography variant="h6" fontWeight={700} color="white">청년복지플랫폼</Typography>
      </Box>

      <Box sx={{ flex: 1, display: "flex", alignItems: "flex-start", justifyContent: "center", p: 3 }}>
        <Paper elevation={0} sx={{ width: "100%", maxWidth: 520, p: { xs: 3, sm: 5 }, borderRadius: 3, border: "1px solid #E8F4F5" }}>
          <Stepper activeStep={step} sx={{ mb: 4 }}>
            {STEPS.map((label) => (
              <Step key={label}><StepLabel>{label}</StepLabel></Step>
            ))}
          </Stepper>

          {/* ── Step 1: 기본 정보 ── */}
          {step === 0 && (
            <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
              <TextField
                label="이름 *"
                value={name}
                onChange={(e) => setName(e.target.value)}
                fullWidth
              />

              <Box>
                <Box sx={{ display: "flex", gap: 1 }}>
                  <TextField
                    label="이메일 *"
                    type="email"
                    value={email}
                    onChange={(e) => { setEmail(e.target.value); setEmailChecked(false); setEmailMsg(""); }}
                    fullWidth
                  />
                  <Button variant="outlined" onClick={handleCheckEmail} sx={{ minWidth: 90, whiteSpace: "nowrap" }}>
                    중복확인
                  </Button>
                </Box>
                {emailMsg && (
                  <Typography variant="caption" color={emailMsg.includes("가능") ? "success.main" : "error"} mt={0.5} display="block">
                    {emailMsg}
                  </Typography>
                )}
              </Box>

              <Box>
                <TextField
                  label="비밀번호 *"
                  type="password"
                  value={pw}
                  onChange={(e) => setPw(e.target.value)}
                  fullWidth
                  helperText="8자 이상, 영문+숫자 조합"
                  error={pw.length > 0 && !pwValid}
                />
              </Box>

              <Box>
                <TextField
                  label="비밀번호 확인 *"
                  type="password"
                  value={pwConfirm}
                  onChange={(e) => setPwConfirm(e.target.value)}
                  fullWidth
                  error={pwConfirm.length > 0 && !pwMatch}
                  helperText={pwConfirm.length > 0 ? (pwMatch ? "✅ 비밀번호가 일치합니다" : "❌ 비밀번호가 일치하지 않습니다") : ""}
                />
              </Box>

              <Button
                variant="contained"
                fullWidth
                size="large"
                disabled={!step1Valid}
                onClick={() => setStep(1)}
                sx={{ mt: 1 }}
              >
                다음 →
              </Button>
            </Box>
          )}

          {/* ── Step 2: 추천 정보 ── */}
          {step === 1 && (
            <Box sx={{ display: "flex", flexDirection: "column", gap: 2.5 }}>
              <Typography variant="body2" color="text.secondary">
                생년월일은 회원가입 필수이고, 나머지는 마이페이지에서 수정할 수 있어요
              </Typography>

              <Box>
                <Typography variant="body2" fontWeight={600} mb={0.5}>생년월일</Typography>
                <Box sx={{ display: "flex", gap: 1 }}>
                  <FormControl size="small" sx={{ flex: 3 }}>
                    <Select value={birthYear} onChange={(e) => { setBirthYear(e.target.value); setBirthDay(""); }} displayEmpty MenuProps={{ PaperProps: { sx: { maxHeight: 240 } } }}>
                      <MenuItem value=""><em>년도</em></MenuItem>
                      {Array.from({ length: 127 }, (_, i) => 2026 - i).map((y) => (
                        <MenuItem key={y} value={String(y)}>{y}년</MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                  <FormControl size="small" sx={{ flex: 2 }}>
                    <Select value={birthMonth} onChange={(e) => { setBirthMonth(e.target.value); setBirthDay(""); }} displayEmpty>
                      <MenuItem value=""><em>월</em></MenuItem>
                      {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
                        <MenuItem key={m} value={String(m)}>{m}월</MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                  <FormControl size="small" sx={{ flex: 2 }} disabled={!birthMonth}>
                    <Select value={birthDay} onChange={(e) => setBirthDay(e.target.value)} displayEmpty>
                      <MenuItem value=""><em>일</em></MenuItem>
                      {Array.from({ length: birthYear && birthMonth ? new Date(birthYear, birthMonth, 0).getDate() : 31 }, (_, i) => i + 1).map((d) => (
                        <MenuItem key={d} value={String(d)}>{d}일</MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </Box>
              </Box>

              <Box>
                <Typography variant="body2" fontWeight={600} mb={1}>주소</Typography>
                <Box sx={{ display: "flex", gap: 1 }}>
                  <FormControl size="small" sx={{ flex: 1 }}>
                    <Select value={region} onChange={(e) => { setRegion(e.target.value); setSubRegion(""); }} displayEmpty>
                      <MenuItem value=""><em>시/도 선택</em></MenuItem>
                      {REGIONS.map((r) => <MenuItem key={r} value={r}>{r}</MenuItem>)}
                    </Select>
                  </FormControl>
                  {DISTRICT_MAP[region]?.length > 0 && (
                    <FormControl size="small" sx={{ flex: 1 }}>
                      <Select value={subRegion} onChange={(e) => setSubRegion(e.target.value)} displayEmpty MenuProps={{ PaperProps: { sx: { maxHeight: 240 } } }}>
                        <MenuItem value=""><em>시/군/구 선택</em></MenuItem>
                        {DISTRICT_MAP[region].map((d) => <MenuItem key={d} value={d}>{d}</MenuItem>)}
                      </Select>
                    </FormControl>
                  )}
                </Box>
              </Box>

              {/* 소득수준 — 좌우 2열 */}
              <Box>
                <Typography variant="body2" fontWeight={600} mb={1}>소득수준</Typography>
                <Box sx={{ border: "1px solid #e0e0e0", borderRadius: 2, overflow: "hidden" }}>
                  <Box sx={{ display: "grid", gridTemplateColumns: "1fr 1fr", borderBottom: "1px solid #e0e0e0" }}>
                    <Typography variant="caption" sx={{ p: 1, bgcolor: "#f9f9f9", fontWeight: 700, textAlign: "center" }}>분위 기준</Typography>
                    <Typography variant="caption" sx={{ p: 1, bgcolor: "#f9f9f9", fontWeight: 700, textAlign: "center", borderLeft: "1px solid #e0e0e0" }}>보기 쉬운 기준</Typography>
                  </Box>
                  {INCOME_ROWS.map((row) => (
                    <Box
                      key={row.value}
                      onClick={() => setIncome(String(row.value))}
                      sx={{
                        display: "grid", gridTemplateColumns: "1fr 1fr",
                        cursor: "pointer", bgcolor: income === String(row.value) ? "primary.light" : "transparent",
                        "&:hover": { bgcolor: income === String(row.value) ? "primary.light" : "#f5fafa" },
                        borderBottom: "1px solid #f0f0f0",
                      }}
                    >
                      <Box sx={{ display: "flex", alignItems: "center", p: 1, gap: 0.5 }}>
                        <Radio size="small" checked={income === String(row.value)} sx={{ p: 0.5 }} />
                        <Typography variant="caption">{row.left}</Typography>
                      </Box>
                      <Box sx={{ display: "flex", alignItems: "center", p: 1, gap: 0.5, borderLeft: "1px solid #e0e0e0" }}>
                        <Radio size="small" checked={income === String(row.value)} sx={{ p: 0.5 }} />
                        <Typography variant="caption">{row.right}</Typography>
                      </Box>
                    </Box>
                  ))}
                </Box>
              </Box>

              {/* 취업상태 */}
              <FormControl>
                <FormLabel sx={{ fontSize: 14, fontWeight: 600 }}>취업상태</FormLabel>
                <RadioGroup row value={employ} onChange={(e) => setEmploy(e.target.value)}>
                  {["재직중", "구직중", "학생", "기타"].map((v) => (
                    <FormControlLabel key={v} value={v} control={<Radio size="small" />} label={v} />
                  ))}
                </RadioGroup>
              </FormControl>

              <Box sx={{ display: "flex", gap: 1 }}>
                <Button variant="outlined" fullWidth onClick={() => setStep(0)}>← 이전</Button>
                <Button variant="contained" fullWidth onClick={() => setStep(2)} disabled={!birthDateComplete}>다음 →</Button>
              </Box>
              {!birthDateComplete && (
                <Typography variant="caption" color="error.main">
                  생년월일을 입력해야 다음 단계로 진행할 수 있습니다
                </Typography>
              )}
            </Box>
          )}

          {/* ── Step 3: 우선순위 ── */}
          {step === 2 && (
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
                  <Typography variant="body2" fontWeight={600} mb={1}>
                    선택한 우선순위 (드래그로 순서 조정)
                  </Typography>
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
                          <Typography variant="body2" color="text.secondary" sx={{ minWidth: 50, fontSize: 12 }}>
                            {idx + 1}순위
                          </Typography>
                          <Typography variant="body2" fontWeight={600} sx={{ flex: 1 }}>
                            {opt?.icon} {opt?.label}
                          </Typography>
                          <IconButton size="small" onClick={() => removePriority(val)}>
                            <CloseIcon fontSize="small" />
                          </IconButton>
                        </Box>
                      );
                    })}
                  </Paper>
                </Box>
              )}

              <Button
                variant="contained"
                fullWidth
                size="large"
                onClick={handleSignup}
                disabled={loading || !birthDateComplete}
                sx={{ mt: 1 }}
              >
                {loading ? <CircularProgress size={22} color="inherit" /> : "가입 완료 🎉"}
              </Button>
            </Box>
          )}
        </Paper>
      </Box>

      <Snackbar open={toast.open} autoHideDuration={3000} onClose={() => setToast({ ...toast, open: false })} anchorOrigin={{ vertical: "bottom", horizontal: "center" }}>
        <Alert severity={toast.severity}>{toast.msg}</Alert>
      </Snackbar>
    </Box>
  );
}
