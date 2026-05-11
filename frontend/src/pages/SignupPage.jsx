import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Snackbar, Alert, CircularProgress } from "@mui/material";
import api from "../lib/axios";

const A = "#2563eb", A7 = "#1d4ed8", AS = "#e8efff", AI = "#1e3a8a";
const BG = "#f7f8fc", WHITE = "#fff";
const INK = "#11131a", INK2 = "#4a4f5c", INK3 = "#6b7280";
const LINE = "#e5e7eb", LINE2 = "#f3f4f6";
const WARN = "#ef4444";
const SUCCESS = "#166534";

const STEPS = ["기본 정보", "추천 정보", "우선순위"];

const INCOME_ROWS = [
  { value: 1, left: "1~2분위 (하위 20%)", right: "기초생활수급자" },
  { value: 3, left: "3~4분위 (하위 40%)", right: "차상위계층" },
  { value: 5, left: "5~6분위 (중간)",     right: "소득 하위 50% 이하" },
  { value: 7, left: "7~8분위 (상위 40%)", right: "소득 중간 (50~100%)" },
  { value: 9, left: "9~10분위 (상위 20%)", right: "소득 상위 (100% 초과)" },
];

const PRIORITY_OPTIONS = [
  { value: "HOUSING",       label: "주거",       ico: "🏠" },
  { value: "JOB",           label: "일자리",     ico: "💼" },
  { value: "EDUCATION",     label: "교육·직업훈련", ico: "📚" },
  { value: "FINANCE",       label: "금융·생활",  ico: "💰" },
  { value: "CULTURE",       label: "문화·여가",  ico: "🎨" },
  { value: "PARTICIPATION", label: "참여·기회",  ico: "🤝" },
  { value: "FAMILY",        label: "가족·돌봄",  ico: "👨‍👩‍👧" },
  { value: "DEADLINE",      label: "마감임박",   ico: "⏰" },
];

const REGIONS = ["서울","부산","대구","인천","광주","대전","울산","세종","경기","강원","충북","충남","전북","전남","경북","경남","제주"];

const REGION_TO_SIDO = {
  "서울": "서울특별시", "부산": "부산광역시", "대구": "대구광역시",
  "인천": "인천광역시", "광주": "광주광역시", "대전": "대전광역시",
  "울산": "울산광역시", "세종": "세종특별자치시", "경기": "경기도",
  "강원": "강원특별자치도", "충북": "충청북도", "충남": "충청남도",
  "전북": "전북특별자치도", "전남": "전라남도", "경북": "경상북도",
  "경남": "경상남도", "제주": "제주특별자치도",
};

const DISTRICT_MAP = {
  "서울": ["강남구","강동구","강북구","강서구","관악구","광진구","구로구","금천구","노원구","도봉구","동대문구","동작구","마포구","서대문구","서초구","성동구","성북구","송파구","양천구","영등포구","용산구","은평구","종로구","중구","중랑구"],
  "부산": ["강서구","금정구","기장군","남구","동구","동래구","부산진구","북구","사상구","사하구","서구","수영구","연제구","영도구","중구","해운대구"],
  "대구": ["남구","달서구","달성군","동구","북구","서구","수성구","중구"],
  "인천": ["강화군","계양구","남동구","동구","미추홀구","부평구","서구","연수구","옹진군","중구"],
  "광주": ["광산구","남구","동구","북구","서구"],
  "대전": ["대덕구","동구","서구","유성구","중구"],
  "울산": ["남구","동구","북구","울주군","중구"],
  "경기": ["가평군","고양시","과천시","광명시","광주시","구리시","군포시","김포시","남양주시","동두천시","부천시","성남시","수원시","시흥시","안산시","안성시","안양시","양주시","양평군","여주시","연천군","오산시","용인시","의왕시","의정부시","이천시","파주시","평택시","포천시","하남시","화성시"],
  "강원": ["강릉시","고성군","동해시","삼척시","속초시","양구군","양양군","영월군","원주시","인제군","정선군","철원군","춘천시","태백시","평창군","홍천군","화천군","횡성군"],
  "충북": ["괴산군","단양군","보은군","영동군","옥천군","음성군","제천시","증평군","진천군","청주시","충주시"],
  "충남": ["계룡시","공주시","금산군","논산시","당진시","보령시","부여군","서산시","서천군","아산시","예산군","천안시","청양군","태안군","홍성군"],
  "전북": ["고창군","군산시","김제시","남원시","무주군","부안군","순창군","완주군","익산시","임실군","장수군","전주시","정읍시","진안군"],
  "전남": ["강진군","고흥군","곡성군","광양시","구례군","나주시","담양군","목포시","무안군","보성군","순천시","신안군","여수시","영광군","영암군","완도군","장성군","장흥군","진도군","함평군","해남군","화순군"],
  "경북": ["경산시","경주시","고령군","구미시","군위군","김천시","문경시","봉화군","상주시","성주군","안동시","영덕군","영양군","영주시","영천시","예천군","울릉군","울진군","의성군","청도군","청송군","칠곡군","포항시"],
  "경남": ["거제시","거창군","고성군","김해시","남해군","밀양시","사천시","산청군","양산시","의령군","진주시","창녕군","창원시","통영시","하동군","함안군","함양군","합천군"],
  "제주": ["서귀포시","제주시"],
};

const iCss = (err) => ({
  width: "100%", padding: "11px 14px", borderRadius: 10,
  border: `1.5px solid ${err ? WARN : LINE}`,
  background: WHITE, fontSize: 14, fontFamily: "inherit", color: INK,
  outline: "none", boxSizing: "border-box",
});

const selCss = (disabled) => ({
  width: "100%", padding: "11px 14px", borderRadius: 10,
  border: `1.5px solid ${LINE}`, background: disabled ? LINE2 : WHITE,
  fontSize: 14, fontFamily: "inherit", color: INK, outline: "none",
  boxSizing: "border-box", appearance: "none", cursor: disabled ? "not-allowed" : "pointer",
});

function Field({ label, error, hint, children }) {
  return (
    <div>
      {label && <label style={{ fontSize: 13, fontWeight: 700, color: INK2, display: "block", marginBottom: 6 }}>{label}</label>}
      {children}
      {error && <div style={{ fontSize: 12, color: WARN, marginTop: 5 }}>⚠ {error}</div>}
      {hint && !error && <div style={{ fontSize: 12, color: INK3, marginTop: 5 }}>{hint}</div>}
    </div>
  );
}

export default function SignupPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState(0);
  const [loading, setLoading] = useState(false);
  const [toast, setToast] = useState({ open: false, msg: "", severity: "info" });

  // Step 1
  const [name, setName] = useState("");
  const [nameError, setNameError] = useState("");
  const [email, setEmail] = useState("");
  const [emailVerified, setEmailVerified] = useState(false);
  const [emailFormatError, setEmailFormatError] = useState("");
  const [codeSent, setCodeSent] = useState(false);
  const [codeInput, setCodeInput] = useState("");
  const [codeMsg, setCodeMsg] = useState("");
  const [sendingCode, setSendingCode] = useState(false);
  const [verifyingCode, setVerifyingCode] = useState(false);
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

  const nameRegex = /^[가-힣]{2,10}$/;
  const emailRegex1 = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
  const emailRegex2 = /^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$/;
  const isValidEmail = (v) => emailRegex1.test(v) && emailRegex2.test(v);

  const pwValid = pw.length >= 8 && /[a-zA-Z]/.test(pw) && /[0-9]/.test(pw);
  const pwMatch = pw === pwConfirm && pwConfirm.length > 0;
  const nameValid = nameRegex.test(name);
  const step1Valid = nameValid && emailVerified && pwValid && pwMatch;
  const birthDateComplete = birthYear && birthMonth && birthDay;

  const resetEmailVerification = () => {
    setEmailVerified(false); setCodeSent(false); setCodeInput(""); setCodeMsg("");
  };

  const handleSendCode = async () => {
    if (!isValidEmail(email)) { setEmailFormatError("올바른 이메일 형식이 아닙니다"); return; }
    setEmailFormatError("");
    setSendingCode(true);
    try {
      await api.post("/api/auth/email-verification/send", null, { params: { email } });
      setCodeSent(true);
      setCodeMsg("인증코드를 발송했습니다. 이메일을 확인해주세요.");
    } catch (e) {
      const status = e.response?.status;
      setCodeMsg(status === 429 ? "잠시 후 다시 시도해주세요 (60초 대기)" : "발송에 실패했습니다. 다시 시도해주세요.");
    } finally {
      setSendingCode(false);
    }
  };

  const handleVerifyCode = async () => {
    if (!codeInput.trim()) return;
    setVerifyingCode(true);
    try {
      await api.post("/api/auth/email-verification/verify", null, { params: { email, code: codeInput.trim() } });
      setEmailVerified(true);
      setCodeMsg("✅ 이메일 인증이 완료되었습니다.");
    } catch {
      setCodeMsg("인증코드가 올바르지 않거나 만료되었습니다.");
    } finally {
      setVerifyingCode(false);
    }
  };

  const handleSignup = async () => {
    setLoading(true);
    try {
      await api.post("/api/auth/signup", {
        name, email, password: pw,
        birthDate: `${birthYear}-${String(birthMonth).padStart(2,"0")}-${String(birthDay).padStart(2,"0")}`,
        ...(region && { sido: REGION_TO_SIDO[region] ?? region }),
        ...(subRegion && { sgg: subRegion }),
        ...(income && { incomeLevel: parseInt(income, 10) }),
        ...(employ && { employmentStatus: employ }),
      });
      navigate("/login", { replace: true, state: { reason: "signup-complete", email, signupPriorities: priorities } });
    } catch (err) {
      showToast(err.response?.data?.message ?? "회원가입 중 오류가 발생했어요", "error");
    } finally {
      setLoading(false);
    }
  };

  const togglePriority = (val) => {
    if (priorities.includes(val)) setPriorities(priorities.filter(p => p !== val));
    else if (priorities.length < 5) setPriorities([...priorities, val]);
  };
  const removePriority = (val) => setPriorities(priorities.filter(p => p !== val));
  const handleDragStart = (idx) => setDragIdx(idx);
  const handleDragOver = (e, idx) => {
    e.preventDefault();
    if (dragIdx === null || dragIdx === idx) return;
    const arr = [...priorities];
    const [moved] = arr.splice(dragIdx, 1);
    arr.splice(idx, 0, moved);
    setPriorities(arr);
    setDragIdx(idx);
  };
  const handleDragEnd = () => setDragIdx(null);

  const btnPrimary = (disabled) => ({
    width: "100%", padding: "13px 0", borderRadius: 10, border: 0,
    background: disabled ? LINE2 : A, color: disabled ? INK3 : WHITE,
    fontSize: 14, fontWeight: 700, cursor: disabled ? "not-allowed" : "pointer",
    display: "flex", alignItems: "center", justifyContent: "center", gap: 8,
  });

  return (
    <div style={{ minHeight: "100vh", background: BG, display: "flex", flexDirection: "column" }}>
      <div style={{ background: A, padding: "14px 24px", display: "flex", alignItems: "center", cursor: "pointer" }} onClick={() => navigate("/")}>
        <div style={{ width: 28, height: 28, borderRadius: 8, background: "rgba(255,255,255,0.2)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 14, marginRight: 8 }}>🏠</div>
        <span style={{ fontSize: 16, fontWeight: 700, color: WHITE }}>청년복지플랫폼</span>
      </div>

      <div style={{ flex: 1, display: "flex", alignItems: "flex-start", justifyContent: "center", padding: "32px 16px 64px" }}>
        <div style={{ width: "100%", maxWidth: 540, background: WHITE, borderRadius: 20, border: `1px solid ${LINE}`, padding: "40px", boxShadow: "0 4px 24px rgba(37,99,235,0.06)" }}>
          {/* Title */}
          <div style={{ textAlign: "center", marginBottom: 32 }}>
            <div style={{ fontSize: 22, fontWeight: 800, color: INK, letterSpacing: "-0.02em", marginBottom: 4 }}>회원가입</div>
            <div style={{ fontSize: 14, color: INK3 }}>청년복지플랫폼에 오신 것을 환영해요 👋</div>
          </div>

          {/* Stepper */}
          <div style={{ display: "flex", alignItems: "center", marginBottom: 36 }}>
            {STEPS.map((label, i) => (
              <div key={i} style={{ display: "flex", alignItems: "center", flex: i < STEPS.length - 1 ? 1 : "none" }}>
                <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 6 }}>
                  <div style={{
                    width: 32, height: 32, borderRadius: "50%",
                    background: i < step ? A : i === step ? A : LINE2,
                    color: i <= step ? WHITE : INK3,
                    display: "flex", alignItems: "center", justifyContent: "center",
                    fontSize: 13, fontWeight: 800, flexShrink: 0,
                    border: i === step ? `2px solid ${A}` : "none",
                    boxSizing: "border-box",
                  }}>
                    {i < step ? "✓" : i + 1}
                  </div>
                  <div style={{ fontSize: 11, fontWeight: i === step ? 700 : 500, color: i === step ? A : INK3, whiteSpace: "nowrap" }}>{label}</div>
                </div>
                {i < STEPS.length - 1 && (
                  <div style={{ flex: 1, height: 2, background: i < step ? A : LINE2, margin: "0 8px", marginBottom: 20 }} />
                )}
              </div>
            ))}
          </div>

          {/* ── Step 1: 기본 정보 ── */}
          {step === 0 && (
            <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
              <Field label="이름 *" error={nameError}>
                <input style={iCss(!!nameError)} value={name}
                  onChange={e => { setName(e.target.value); setNameError(""); }}
                  onBlur={() => { if (name && !nameRegex.test(name)) setNameError("이름은 특수 기호 및 숫자를 제외한 한글 2~10자로 입력해주세요."); }}
                  placeholder="홍길동" />
              </Field>

              <Field label="이메일 *" error={emailFormatError}>
                <div style={{ display: "flex", gap: 8 }}>
                  <input style={{ ...iCss(!!emailFormatError), opacity: emailVerified ? 0.7 : 1 }}
                    type="email" value={email}
                    onChange={e => { setEmail(e.target.value); setEmailFormatError(""); resetEmailVerification(); }}
                    onBlur={() => { if (email && !isValidEmail(email)) setEmailFormatError("올바른 이메일 형식이 아닙니다"); }}
                    placeholder="example@email.com"
                    disabled={emailVerified} />
                  <button onClick={handleSendCode} disabled={sendingCode || emailVerified} style={{
                    padding: "0 16px", borderRadius: 10, border: `1.5px solid ${emailVerified ? LINE : A}`,
                    background: emailVerified ? LINE2 : WHITE, color: emailVerified ? INK3 : A,
                    fontSize: 13, fontWeight: 700, cursor: emailVerified ? "not-allowed" : "pointer", whiteSpace: "nowrap", flexShrink: 0,
                  }}>
                    {sendingCode ? "발송 중..." : codeSent ? "재발송" : "인증코드 받기"}
                  </button>
                </div>
                {codeMsg && (
                  <div style={{ fontSize: 12, marginTop: 6, color: emailVerified ? "#166534" : INK3 }}>{codeMsg}</div>
                )}
                {codeSent && !emailVerified && (
                  <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
                    <input
                      style={iCss(false)}
                      placeholder="인증코드 6자리"
                      value={codeInput}
                      onChange={e => setCodeInput(e.target.value.replace(/\D/g, "").slice(0, 6))}
                      maxLength={6}
                    />
                    <button onClick={handleVerifyCode} disabled={verifyingCode || codeInput.length !== 6} style={{
                      padding: "0 16px", borderRadius: 10, border: 0, background: codeInput.length === 6 ? A : LINE2,
                      color: codeInput.length === 6 ? WHITE : INK3, fontSize: 13, fontWeight: 700,
                      cursor: codeInput.length === 6 ? "pointer" : "not-allowed", whiteSpace: "nowrap", flexShrink: 0,
                    }}>
                      {verifyingCode ? "확인 중..." : "인증확인"}
                    </button>
                  </div>
                )}
              </Field>

              <Field label="비밀번호 *" hint="8자 이상, 영문+숫자 조합"
                error={pw.length > 0 && !pwValid ? "8자 이상, 영문+숫자 조합으로 입력해주세요" : ""}>
                <input style={iCss(pw.length > 0 && !pwValid)} type="password" value={pw} onChange={e => setPw(e.target.value)} placeholder="비밀번호 입력" />
              </Field>

              <Field label="비밀번호 확인 *"
                error={pwConfirm.length > 0 && !pwMatch ? "비밀번호가 일치하지 않습니다" : ""}
                hint={pwMatch ? "✅ 비밀번호가 일치합니다" : ""}>
                <input style={iCss(pwConfirm.length > 0 && !pwMatch)} type="password" value={pwConfirm} onChange={e => setPwConfirm(e.target.value)} placeholder="비밀번호 다시 입력" />
              </Field>

              <button disabled={!step1Valid} onClick={() => setStep(1)} style={btnPrimary(!step1Valid)}>
                다음 →
              </button>
              <div style={{ textAlign: "center" }}>
                <button onClick={() => navigate("/login")} style={{ background: "transparent", border: 0, color: INK3, fontSize: 13, cursor: "pointer" }}>
                  이미 계정이 있으신가요? <span style={{ color: A, fontWeight: 700 }}>로그인</span>
                </button>
              </div>
            </div>
          )}

          {/* ── Step 2: 추천 정보 ── */}
          {step === 1 && (
            <div style={{ display: "flex", flexDirection: "column", gap: 20 }}>
              <div style={{ padding: "12px 16px", background: AS, borderRadius: 10, fontSize: 13, color: AI }}>
                💡 생년월일은 필수이고, 나머지는 마이페이지에서 수정할 수 있어요
              </div>

              <Field label="생년월일 *">
                <div style={{ display: "grid", gridTemplateColumns: "1.3fr 1fr 1fr", gap: 8 }}>
                  <select style={selCss(false)} value={birthYear} onChange={e => { setBirthYear(e.target.value); setBirthDay(""); }}>
                    <option value="">년도</option>
                    {Array.from({ length: 127 }, (_, i) => 2026 - i).map(y => <option key={y} value={String(y)}>{y}년</option>)}
                  </select>
                  <select style={selCss(false)} value={birthMonth} onChange={e => { setBirthMonth(e.target.value); setBirthDay(""); }}>
                    <option value="">월</option>
                    {Array.from({ length: 12 }, (_, i) => i + 1).map(m => <option key={m} value={String(m)}>{m}월</option>)}
                  </select>
                  <select style={selCss(!birthMonth)} disabled={!birthMonth} value={birthDay} onChange={e => setBirthDay(e.target.value)}>
                    <option value="">일</option>
                    {Array.from({ length: birthYear && birthMonth ? new Date(birthYear, birthMonth, 0).getDate() : 31 }, (_, i) => i + 1).map(d => <option key={d} value={String(d)}>{d}일</option>)}
                  </select>
                </div>
              </Field>

              <Field label="주소">
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
                  <select style={selCss(false)} value={region} onChange={e => { setRegion(e.target.value); setSubRegion(""); }}>
                    <option value="">시/도 선택</option>
                    {REGIONS.map(r => <option key={r} value={r}>{r}</option>)}
                  </select>
                  <select style={selCss(!DISTRICT_MAP[region])} disabled={!DISTRICT_MAP[region]} value={subRegion} onChange={e => setSubRegion(e.target.value)}>
                    <option value="">시/군/구 선택</option>
                    {(DISTRICT_MAP[region] || []).map(d => <option key={d} value={d}>{d}</option>)}
                  </select>
                </div>
              </Field>

              <Field label="소득수준">
                <div style={{ border: `1px solid ${LINE}`, borderRadius: 12, overflow: "hidden" }}>
                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", background: BG, padding: "10px 16px", fontSize: 12, fontWeight: 700, color: INK2, borderBottom: `1px solid ${LINE}` }}>
                    <div>분위 기준</div>
                    <div style={{ borderLeft: `1px solid ${LINE}`, paddingLeft: 16 }}>보기 쉬운 기준</div>
                  </div>
                  {INCOME_ROWS.map(row => (
                    <label key={row.value} onClick={() => setIncome(String(row.value))} style={{
                      display: "grid", gridTemplateColumns: "1fr 1fr", padding: "12px 16px",
                      borderBottom: `1px solid ${LINE2}`, cursor: "pointer", alignItems: "center",
                      background: income === String(row.value) ? AS : WHITE,
                    }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13 }}>
                        <input type="radio" readOnly checked={income === String(row.value)} onChange={() => {}} style={{ accentColor: A }} />
                        {row.left}
                      </div>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13, color: INK2, borderLeft: `1px solid ${LINE2}`, paddingLeft: 16 }}>
                        <input type="radio" readOnly checked={income === String(row.value)} onChange={() => {}} style={{ accentColor: A }} />
                        {row.right}
                      </div>
                    </label>
                  ))}
                </div>
              </Field>

              <Field label="취업상태">
                <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 8 }}>
                  {["재직중", "구직중", "학생", "기타"].map(v => {
                    const active = employ === v;
                    return (
                      <button key={v} onClick={() => setEmploy(v)} style={{
                        padding: "11px 8px", borderRadius: 10,
                        border: `1.5px solid ${active ? A : LINE}`,
                        background: active ? AS : WHITE, color: active ? AI : INK2,
                        fontSize: 13, fontWeight: 700, cursor: "pointer",
                      }}>
                        {v}
                      </button>
                    );
                  })}
                </div>
              </Field>

              <div style={{ display: "flex", gap: 10 }}>
                <button onClick={() => setStep(0)} style={{ flex: 1, padding: "13px 0", borderRadius: 10, border: `1.5px solid ${LINE}`, background: WHITE, color: INK2, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                  ← 이전
                </button>
                <button disabled={!birthDateComplete} onClick={() => setStep(2)} style={btnPrimary(!birthDateComplete)}>
                  다음 →
                </button>
              </div>
              {!birthDateComplete && (
                <div style={{ fontSize: 12, color: WARN, textAlign: "center" }}>생년월일을 입력해야 다음 단계로 진행할 수 있습니다</div>
              )}
            </div>
          )}

          {/* ── Step 3: 우선순위 ── */}
          {step === 2 && (
            <div style={{ display: "flex", flexDirection: "column", gap: 20 }}>
              <div>
                <div style={{ fontSize: 16, fontWeight: 800, color: INK, marginBottom: 4 }}>어떤 정책을 우선적으로 받고 싶으세요?</div>
                <div style={{ fontSize: 13, color: INK3 }}>최대 5개 선택 · 순서가 중요해요 (건너뛸 수 있어요)</div>
              </div>

              <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 10 }}>
                {PRIORITY_OPTIONS.map(c => {
                  const idx = priorities.indexOf(c.value);
                  const active = idx >= 0;
                  return (
                    <button key={c.value} onClick={() => togglePriority(c.value)}
                      disabled={!active && priorities.length >= 5}
                      style={{
                        position: "relative", padding: "20px 10px 14px", borderRadius: 14,
                        border: `2px solid ${active ? A : LINE}`,
                        background: active ? AS : WHITE,
                        textAlign: "center", cursor: (!active && priorities.length >= 5) ? "not-allowed" : "pointer",
                        opacity: (!active && priorities.length >= 5) ? 0.5 : 1,
                      }}>
                      {active && (
                        <span style={{ position: "absolute", top: 7, right: 7, width: 20, height: 20, borderRadius: "50%", background: A, color: WHITE, fontSize: 10, fontWeight: 800, display: "flex", alignItems: "center", justifyContent: "center" }}>
                          {idx + 1}
                        </span>
                      )}
                      <div style={{ fontSize: 24, marginBottom: 6 }}>{c.ico}</div>
                      <div style={{ fontSize: 12, fontWeight: 700, color: active ? AI : INK }}>{c.label}</div>
                    </button>
                  );
                })}
              </div>

              {priorities.length > 0 && (
                <div>
                  <div style={{ fontSize: 13, fontWeight: 700, color: INK2, marginBottom: 8 }}>선택한 우선순위 (드래그로 순서 조정)</div>
                  <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                    {priorities.map((val, i) => {
                      const c = PRIORITY_OPTIONS.find(o => o.value === val);
                      return (
                        <div key={val} draggable
                          onDragStart={() => handleDragStart(i)}
                          onDragOver={e => handleDragOver(e, i)}
                          onDragEnd={handleDragEnd}
                          style={{
                            display: "flex", alignItems: "center", gap: 12, padding: "12px 14px",
                            background: dragIdx === i ? AS : BG,
                            border: `1px solid ${dragIdx === i ? A : LINE}`,
                            borderRadius: 10, cursor: "grab",
                          }}>
                          <span style={{ color: INK3, fontSize: 14 }}>⋮⋮</span>
                          <span style={{ width: 24, height: 24, borderRadius: "50%", background: A, color: WHITE, fontSize: 11, fontWeight: 800, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>{i + 1}</span>
                          <span style={{ fontSize: 16 }}>{c?.ico}</span>
                          <span style={{ fontSize: 13, fontWeight: 700, flex: 1, color: INK }}>{c?.label}</span>
                          <button onClick={() => removePriority(val)} style={{ background: "transparent", border: 0, color: INK3, fontSize: 14, cursor: "pointer", padding: "2px 6px" }}>✕</button>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}

              <div style={{ display: "flex", gap: 10 }}>
                <button onClick={() => setStep(1)} style={{ flex: 1, padding: "13px 0", borderRadius: 10, border: `1.5px solid ${LINE}`, background: WHITE, color: INK2, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                  ← 이전
                </button>
                <button disabled={loading || !birthDateComplete} onClick={handleSignup} style={btnPrimary(loading || !birthDateComplete)}>
                  {loading ? <CircularProgress size={20} sx={{ color: WHITE }} /> : "가입 완료 🎉"}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>

      <Snackbar open={toast.open} autoHideDuration={3000} onClose={() => setToast(t => ({ ...t, open: false }))} anchorOrigin={{ vertical: "bottom", horizontal: "center" }}>
        <Alert severity={toast.severity}>{toast.msg}</Alert>
      </Snackbar>
    </div>
  );
}
