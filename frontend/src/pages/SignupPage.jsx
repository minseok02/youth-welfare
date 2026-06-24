import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Snackbar, Alert, CircularProgress, Dialog, DialogContent, IconButton, useMediaQuery } from "@mui/material";
import HomeIcon from "@mui/icons-material/Home";
import CloseIcon from "@mui/icons-material/Close";
import api from "../lib/axios";
import PrivacyPolicyContent from "../components/PrivacyPolicyContent";
import PrioritySortableList from "../components/PrioritySortableList";
import { fetchProfileStandardCodebookOptions } from "../lib/officialCodebookOptions";
import { resolveSafeRouteTarget, sanitizePostLoginAction } from "../lib/safeNavigation";
import {
  REGIONS,
  REGION_TO_SIDO,
  getDistrictOptions,
  getWardOptions,
  resolveSubmittedSgg,
} from "../lib/regionOptions";

const A = "#2563eb", A7 = "#1d4ed8", AS = "#e8efff", AI = "#1e3a8a";
const BG = "#f7f8fc", WHITE = "#fff";
const INK = "#11131a", INK2 = "#4a4f5c", INK3 = "#6b7280";
const LINE = "#e5e7eb", LINE2 = "#f3f4f6";
const WARN = "#ef4444";
const SUCCESS = "#166534";

const STEPS = ["기본 정보", "추천 정보", "우선순위"];
const EMAIL_REGEX = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
const PASSWORD_REGEX = /^(?=.*[A-Za-z])(?=.*\d)[\x21-\x7E]{10,72}$/;

const INCOME_ROWS = [
  { value: 1, left: "1~2분위 (하위 20%)", right: "기초생활 지원 대상" },
  { value: 3, left: "3~4분위 (하위 40%)", right: "차상위 지원 대상" },
  { value: 5, left: "5~6분위 (중간)",     right: "소득 하위 50% 이하" },
  { value: 7, left: "7~8분위 (상위 40%)", right: "소득 중간 (50~100%)" },
  { value: 9, left: "9~10분위 (상위 20%)", right: "소득 상위 (100% 초과)" },
];

const PRIORITY_OPTIONS = [
  { value: "HOUSING",       label: "주거",          fg: "#f59e0b" },
  { value: "JOB",           label: "일자리",         fg: "#2563eb" },
  { value: "EDUCATION",     label: "교육·직업훈련",  fg: "#16a34a" },
  { value: "FINANCE",       label: "금융·생활",      fg: "#db2777" },
  { value: "CULTURE",       label: "문화·여가",      fg: "#7c3aed" },
  { value: "PARTICIPATION", label: "참여·기회",      fg: "#ea580c" },
  { value: "FAMILY",        label: "가족·돌봄",      fg: "#0d9488" },
  { value: "DEADLINE",      label: "마감임박",        fg: "#dc2626" },
];

const EMPLOYMENT_STATUS_OPTIONS = ["재직중", "구직중", "학생", "해당 없음", "기타"];
const HOUSEHOLD_TYPES = ["해당 없음", "1인가구", "한부모", "다자녀", "조손", "기타"];
const EMPTY_PROFILE_CODE_OPTIONS = {
  houseTenure: [],
  housingType: [],
  basicLivingRecipientType: [],
  disabilityGrade: [],
};

const iCss = (err) => ({
  width: "100%", padding: "11px 14px", borderRadius: 8,
  border: `1.5px solid ${err ? WARN : LINE}`,
  background: WHITE, fontSize: 14, fontFamily: "inherit", color: INK,
  outline: "none", boxSizing: "border-box",
});

const selCss = (disabled) => ({
  width: "100%", padding: "11px 14px", borderRadius: 8,
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
  const location = useLocation();
  const isMobile = useMediaQuery("(max-width: 600px)");
  const safeFromTarget = resolveSafeRouteTarget(location.state?.from);
  const safeChatFromStateTarget = resolveSafeRouteTarget(location.state?.chatFrom);
  const nestedFromState = safeFromTarget?.state ?? {};
  const chatFrom = safeChatFromStateTarget ?? nestedFromState.chatFrom;
  const postLoginAction = sanitizePostLoginAction(location.state?.postLoginAction) ?? nestedFromState.postLoginAction;
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
  const [privacyNoticeConfirmed, setPrivacyNoticeConfirmed] = useState(false);
  const [privacyModalOpen, setPrivacyModalOpen] = useState(false);

  // Step 2
  const [birthYear, setBirthYear] = useState("");
  const [birthMonth, setBirthMonth] = useState("");
  const [birthDay, setBirthDay] = useState("");
  const [region, setRegion] = useState("");
  const [subRegion, setSubRegion] = useState("");
  const [ward, setWard] = useState("");
  const [income, setIncome] = useState("");
  const [employ, setEmploy] = useState("");
  const [householdType, setHouseholdType] = useState("");
  const [houseTenureCode, setHouseTenureCode] = useState("");
  const [housingTypeCode, setHousingTypeCode] = useState("");
  const [basicLivingRecipientTypeCode, setBasicLivingRecipientTypeCode] = useState("");
  const [disabilityGradeCode, setDisabilityGradeCode] = useState("");
  const [optionalProfileConsentAgreed, setOptionalProfileConsentAgreed] = useState(false);
  const [sensitiveInfoConsentAgreed, setSensitiveInfoConsentAgreed] = useState(false);
  const [profileCodeOptions, setProfileCodeOptions] = useState(EMPTY_PROFILE_CODE_OPTIONS);
  const [step2Attempted, setStep2Attempted] = useState(false);

  // Step 3
  const [priorities, setPriorities] = useState([]);

  const showToast = (msg, severity = "info") => setToast({ open: true, msg, severity });

  useEffect(() => {
    let active = true;
    fetchProfileStandardCodebookOptions()
      .then((options) => {
        if (active) {
          setProfileCodeOptions(options);
        }
      })
      .catch(() => {
        if (active) {
          showToast("공식 코드 목록을 불러오지 못했습니다", "warning");
        }
      });
    return () => {
      active = false;
    };
  }, []);

  const nameRegex = /^[가-힣]{2,10}$/;
  const normalizeEmailInput = (value) => value.trim().toLowerCase();
  const isValidEmail = (v) => EMAIL_REGEX.test(normalizeEmailInput(v)) && normalizeEmailInput(v).length <= 254;

  const pwValid = PASSWORD_REGEX.test(pw);
  const pwMatch = pw === pwConfirm && pwConfirm.length > 0;
  const nameValid = nameRegex.test(name);
  const step1Valid = nameValid && emailVerified && pwValid && pwMatch && privacyNoticeConfirmed;
  const birthDateComplete = Boolean(birthYear && birthMonth && birthDay);
  const birthDateValue = birthDateComplete
    ? `${birthYear}-${String(birthMonth).padStart(2,"0")}-${String(birthDay).padStart(2,"0")}`
    : "";
  const birthDateAtLeastFourteen = !birthDateComplete || (() => {
    const today = new Date();
    const birthday = new Date(Number(birthYear), Number(birthMonth) - 1, Number(birthDay));
    const fourteenBirthday = new Date(birthday);
    fourteenBirthday.setFullYear(birthday.getFullYear() + 14);
    return fourteenBirthday <= today;
  })();
  const hasOptionalProfileInput = Boolean(
    region || subRegion || ward || income || employ || householdType
    || houseTenureCode || housingTypeCode || basicLivingRecipientTypeCode
  );
  const optionalConsentRequired = hasOptionalProfileInput && !optionalProfileConsentAgreed;
  const step2Valid = birthDateComplete && birthDateAtLeastFourteen && !optionalConsentRequired;

  const birthDone = birthDateComplete && birthDateAtLeastFourteen;
  const birthError = step2Attempted && !birthDone;
  const consentError = step2Attempted && optionalConsentRequired;
  const birthBorderColor = step2Attempted ? (birthDone ? SUCCESS : WARN) : LINE;

  const handleStep2Next = () => {
    setStep2Attempted(true);
    if (!birthDateComplete) {
      showToast("생년월일을 입력해주세요", "warning");
      return;
    }
    if (!birthDateAtLeastFourteen) {
      showToast("만 14세 미만은 회원가입을 할 수 없습니다", "warning");
      return;
    }
    if (optionalConsentRequired) {
      showToast("입력하신 추천 정보를 저장하려면 선택 개인정보 수집 및 이용에 동의 체크해 주세요.", "warning");
      return;
    }
    setStep(2);
  };

  const resetEmailVerification = () => {
    setEmailVerified(false); setCodeSent(false); setCodeInput(""); setCodeMsg("");
  };

  const handleOptionalProfileConsentChange = (checked) => {
    setOptionalProfileConsentAgreed(checked);
    if (!checked) {
      setRegion("");
      setSubRegion("");
      setWard("");
      setIncome("");
      setEmploy("");
      setHouseholdType("");
      setHouseTenureCode("");
      setHousingTypeCode("");
      setBasicLivingRecipientTypeCode("");
      setPriorities([]);
    }
  };

  const handleSensitiveInfoConsentChange = (checked) => {
    setSensitiveInfoConsentAgreed(checked);
    if (!checked) setDisabilityGradeCode("");
  };

  const handleSendCode = async () => {
    if (!isValidEmail(email)) { setEmailFormatError("올바른 이메일 형식이 아닙니다"); return; }
    setEmailFormatError("");
    const normalizedEmail = normalizeEmailInput(email);
    setEmail(normalizedEmail);
    setSendingCode(true);
    try {
      await api.post("/api/auth/email-verification/send", { email: normalizedEmail });
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
    const normalizedEmail = normalizeEmailInput(email);
    setVerifyingCode(true);
    try {
      await api.post("/api/auth/email-verification/verify", { email: normalizedEmail, code: codeInput.trim() });
      setEmailVerified(true);
      setEmail(normalizedEmail);
      setCodeMsg("이메일 인증이 완료되었습니다.");
    } catch {
      setCodeMsg("인증코드가 올바르지 않거나 만료되었습니다.");
    } finally {
      setVerifyingCode(false);
    }
  };

  const handleSignup = async () => {
    setLoading(true);
    try {
      if (priorities.length > 0 && !optionalProfileConsentAgreed) {
        setStep2Attempted(true);
        setStep(1);
        showToast("우선순위를 저장하려면 선택 개인정보 수집 및 이용에 동의해 주세요.", "warning");
        return;
      }
      const submittedSgg = resolveSubmittedSgg(region, subRegion, ward);
      const normalizedEmail = normalizeEmailInput(email);
      const payload = {
        name, email: normalizedEmail, password: pw,
        birthDate: birthDateValue,
        privacyNoticeConfirmed,
        optionalProfileConsentAgreed,
        sensitiveInfoConsentAgreed,
        ...(optionalProfileConsentAgreed && region && { sido: REGION_TO_SIDO[region] ?? region }),
        ...(optionalProfileConsentAgreed && submittedSgg && { sgg: submittedSgg }),
        ...(optionalProfileConsentAgreed && income && { incomeLevel: parseInt(income, 10) }),
        ...(optionalProfileConsentAgreed && householdType && { householdType }),
        ...(optionalProfileConsentAgreed && employ && { employmentStatus: employ }),
        ...(optionalProfileConsentAgreed && houseTenureCode && { houseTenureCode }),
        ...(optionalProfileConsentAgreed && housingTypeCode && { housingTypeCode }),
        ...(optionalProfileConsentAgreed && basicLivingRecipientTypeCode && { basicLivingRecipientTypeCode }),
        ...(sensitiveInfoConsentAgreed && disabilityGradeCode && { disabilityGradeCode }),
        ...(optionalProfileConsentAgreed && priorities.length > 0 && { priorityCodes: priorities }),
      };
      await api.post("/api/auth/signup", payload);
      navigate("/login", {
        replace: true,
        state: {
          reason: "signup-complete",
          email: normalizedEmail,
          signupPriorities: priorities,
          from: safeFromTarget ?? undefined,
          chatFrom,
          postLoginAction,
        },
      });
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
  const districtOptions = getDistrictOptions(region);
  const wardOptions = getWardOptions(region, subRegion);
  const optionalProfileInputDisabled = !optionalProfileConsentAgreed;
  const selectedStandardCodeCount = [
    houseTenureCode,
    housingTypeCode,
    basicLivingRecipientTypeCode,
  ].filter(Boolean).length;

  const btnPrimary = (disabled) => ({
    width: "100%", padding: "13px 0", borderRadius: 10, border: 0,
    background: disabled ? LINE2 : A, color: disabled ? INK3 : WHITE,
    fontSize: 14, fontWeight: 700, cursor: disabled ? "not-allowed" : "pointer",
    display: "flex", alignItems: "center", justifyContent: "center", gap: 8,
  });

  return (
    <div style={{ minHeight: "100vh", background: BG, display: "flex", flexDirection: "column" }}>
      <div style={{ background: A, padding: "14px 24px", display: "flex", alignItems: "center", cursor: "pointer" }} onClick={() => navigate("/")}>
        <div style={{ width: 28, height: 28, borderRadius: 8, background: "rgba(255,255,255,0.2)", display: "flex", alignItems: "center", justifyContent: "center", color: WHITE, marginRight: 8 }}><HomeIcon sx={{ fontSize: 18 }} /></div>
        <span style={{ fontSize: 16, fontWeight: 700, color: WHITE }}>청년복지플랫폼</span>
      </div>

      <div style={{ flex: 1, display: "flex", alignItems: "flex-start", justifyContent: "center", padding: "32px 16px 64px" }}>
        <div style={{ width: "100%", maxWidth: 540, background: WHITE, borderRadius: 20, border: `1px solid ${LINE}`, padding: isMobile ? "28px 16px" : "40px", boxShadow: "0 4px 24px rgba(37,99,235,0.06)" }}>
          {/* Title */}
          <div style={{ textAlign: "center", marginBottom: 32 }}>
            <div style={{ fontSize: 22, fontWeight: 800, color: INK, letterSpacing: "-0.02em" }}>회원가입</div>
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
                    onBlur={() => {
                      const normalizedEmail = normalizeEmailInput(email);
                      setEmail(normalizedEmail);
                      if (normalizedEmail && !isValidEmail(normalizedEmail)) setEmailFormatError("올바른 이메일 형식이 아닙니다");
                    }}
                    placeholder="example@email.com"
                    maxLength={254}
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
                  <div style={{ fontSize: 12, marginTop: 6, color: emailVerified ? "#166534" : INK3 }}>
                    {emailVerified && <span style={{ fontWeight: 800 }}>✔ </span>}{codeMsg}
                  </div>
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

              <Field label="비밀번호 *" hint="공백 없이 10~72자, 영문과 숫자 포함"
                error={pw.length > 0 && !pwValid ? "공백 없이 10~72자, 영문과 숫자를 포함해주세요" : ""}>
                <input style={iCss(pw.length > 0 && !pwValid)} type="password" value={pw} onChange={e => setPw(e.target.value)} placeholder="비밀번호 입력" maxLength={72} />
              </Field>

              <Field label="비밀번호 확인 *"
                error={pwConfirm.length > 0 && !pwMatch ? "비밀번호가 일치하지 않습니다" : ""}
                hint={pwMatch ? <span style={{ color: "#166534" }}><span style={{ fontWeight: 800 }}>✔ </span>비밀번호가 일치합니다</span> : ""}>
                <input style={iCss(pwConfirm.length > 0 && !pwMatch)} type="password" value={pwConfirm} onChange={e => setPwConfirm(e.target.value)} placeholder="비밀번호 다시 입력" maxLength={72} />
              </Field>

              <label style={{
                display: "flex", gap: 10, alignItems: "flex-start", padding: "13px 14px",
                border: `1px solid ${privacyNoticeConfirmed ? A : LINE}`, borderRadius: 8,
                background: privacyNoticeConfirmed ? AS : "#f8fafc", cursor: "pointer",
              }}>
                <input
                  type="checkbox"
                  checked={privacyNoticeConfirmed}
                  onChange={e => setPrivacyNoticeConfirmed(e.target.checked)}
                  style={{ marginTop: 2, accentColor: A }}
                />
                <span style={{ fontSize: 13, color: INK2, lineHeight: 1.55 }}>
                  계정 생성 및 서비스 제공을 위한 개인정보 처리 안내를 확인했습니다.
                  <button
                    type="button"
                    onClick={e => { e.preventDefault(); e.stopPropagation(); setPrivacyModalOpen(true); }}
                    style={{ color: A, fontWeight: 800, marginLeft: 4, background: "none", border: 0, padding: 0, cursor: "pointer", textDecoration: "underline", font: "inherit" }}
                  >
                    개인정보 처리방침
                  </button>
                </span>
              </label>

              <button disabled={!step1Valid} onClick={() => setStep(1)} style={btnPrimary(!step1Valid)}>
                다음 →
              </button>
              <div style={{ textAlign: "center" }}>
                <button onClick={() => navigate("/login", {
                  state: {
                    from: safeFromTarget ?? undefined,
                    email,
                    chatFrom,
                    postLoginAction,
                  },
                })} style={{ background: "transparent", border: 0, color: INK3, fontSize: 13, cursor: "pointer" }}>
                  이미 계정이 있으신가요? <span style={{ color: A, fontWeight: 700 }}>로그인</span>
                </button>
              </div>
            </div>
          )}

          {/* ── Step 2: 추천 정보 ── */}
          {step === 1 && (
            <div style={{ display: "flex", flexDirection: "column", gap: 20 }}>
              <div style={{ padding: "12px 16px", background: AS, borderRadius: 10, fontSize: 13, color: AI }}>
                💡 생년월일은 필수이고, 나머지는 동의한 경우에만 추천 정확도 향상을 위해 저장합니다
              </div>

              <Field label="생년월일 *">
                <div style={{ display: "grid", gridTemplateColumns: "1.3fr 1fr 1fr", gap: 8 }}>
                  <select style={{ ...selCss(false), borderColor: birthBorderColor }} value={birthYear} onChange={e => { setBirthYear(e.target.value); setBirthDay(""); }}>
                    <option value="">년도</option>
                    {Array.from({ length: 127 }, (_, i) => 2026 - i).map(y => <option key={y} value={String(y)}>{y}년</option>)}
                  </select>
                  <select style={{ ...selCss(false), borderColor: birthBorderColor }} value={birthMonth} onChange={e => { setBirthMonth(e.target.value); setBirthDay(""); }}>
                    <option value="">월</option>
                    {Array.from({ length: 12 }, (_, i) => i + 1).map(m => <option key={m} value={String(m)}>{m}월</option>)}
                  </select>
                  <select style={{ ...selCss(!birthMonth), borderColor: birthBorderColor }} disabled={!birthMonth} value={birthDay} onChange={e => setBirthDay(e.target.value)}>
                    <option value="">일</option>
                    {Array.from({ length: birthYear && birthMonth ? new Date(birthYear, birthMonth, 0).getDate() : 31 }, (_, i) => i + 1).map(d => <option key={d} value={String(d)}>{d}일</option>)}
                  </select>
                </div>
                {step2Attempted && birthDone && (
                  <div style={{ fontSize: 12, color: SUCCESS, fontWeight: 700, marginTop: 6 }}>✔ 생년월일 입력 완료</div>
                )}
                {birthError && !birthDateComplete && (
                  <div style={{ fontSize: 12, color: WARN, marginTop: 6 }}>생년월일을 모두 선택해주세요</div>
                )}
              </Field>

              {birthDateComplete && !birthDateAtLeastFourteen && (
                <div style={{ padding: "12px 14px", borderRadius: 10, background: "#fef2f2", border: `1px solid #fecaca`, fontSize: 12, color: "#991b1b" }}>
                  만 14세 미만은 현재 회원가입을 할 수 없습니다.
                </div>
              )}

              <label style={{
                display: "flex", gap: 10, alignItems: "flex-start", padding: "13px 14px",
                border: `1.5px solid ${consentError ? WARN : (step2Attempted && optionalProfileConsentAgreed) ? SUCCESS : LINE}`, borderRadius: 8,
                background: (step2Attempted && optionalProfileConsentAgreed) ? "#ecfdf5" : consentError ? "#fef2f2" : "#f8fafc", cursor: "pointer",
              }}>
                <input
                  type="checkbox"
                  checked={optionalProfileConsentAgreed}
                  onChange={e => handleOptionalProfileConsentChange(e.target.checked)}
                  style={{ marginTop: 2, accentColor: A }}
                />
                <span style={{ fontSize: 13, color: INK2, lineHeight: 1.55 }}>
                  맞춤형 복지 추천을 위한 선택 개인정보 수집·이용에 동의합니다.
                  <span style={{ display: "block", color: INK3, marginTop: 2 }}>
                    지역, 소득수준, 취업상태, 가구형태, 주거 및 수급 관련 정보가 포함됩니다.
                  </span>
                  {step2Attempted && optionalProfileConsentAgreed && (
                    <span style={{ display: "block", color: SUCCESS, fontWeight: 700, marginTop: 6 }}>✔ 동의 완료</span>
                  )}
                  {consentError && (
                    <span style={{ display: "block", color: WARN, fontWeight: 700, marginTop: 6 }}>추천 정보를 저장하려면 동의가 필요해요</span>
                  )}
                </span>
              </label>

              <Field label="주소">
                <div style={{ display: "grid", gridTemplateColumns: wardOptions.length > 0 ? "1fr 1fr 1fr" : "1fr 1fr", gap: 8 }}>
                  <select style={selCss(optionalProfileInputDisabled)} disabled={optionalProfileInputDisabled} value={region} onChange={e => { setRegion(e.target.value); setSubRegion(""); setWard(""); }}>
                    <option value="">시/도 선택</option>
                    {REGIONS.map(r => <option key={r} value={r}>{r}</option>)}
                  </select>
                  <select style={selCss(optionalProfileInputDisabled || districtOptions.length === 0)} disabled={optionalProfileInputDisabled || districtOptions.length === 0} value={subRegion} onChange={e => { setSubRegion(e.target.value); setWard(""); }}>
                    <option value="">시/군/구 선택</option>
                    {districtOptions.map(d => <option key={d} value={d}>{d}</option>)}
                  </select>
                  {wardOptions.length > 0 && (
                    <select style={selCss(optionalProfileInputDisabled)} disabled={optionalProfileInputDisabled} value={ward} onChange={e => setWard(e.target.value)}>
                      <option value="">구 선택</option>
                      {wardOptions.map((value) => <option key={value} value={value}>{value}</option>)}
                    </select>
                  )}
                </div>
              </Field>

              <Field label="소득수준">
                <div style={{ border: `1px solid ${LINE}`, borderRadius: 8, overflow: "hidden" }}>
                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", background: BG, padding: "10px 16px", fontSize: 12, fontWeight: 700, color: INK2, borderBottom: `1px solid ${LINE}` }}>
                    <div>분위 기준</div>
                    <div style={{ borderLeft: `1px solid ${LINE}`, paddingLeft: 16 }}>보기 쉬운 기준</div>
                  </div>
                  {INCOME_ROWS.map(row => (
                    <label key={row.value} onClick={() => { if (!optionalProfileInputDisabled) setIncome(String(row.value)); }} style={{
                      display: "grid", gridTemplateColumns: "1fr 1fr", padding: "12px 16px",
                      borderBottom: `1px solid ${LINE2}`, cursor: optionalProfileInputDisabled ? "not-allowed" : "pointer", alignItems: "center",
                      background: income === String(row.value) ? AS : WHITE,
                      opacity: optionalProfileInputDisabled ? 0.6 : 1,
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

              <Field label="취업상태" hint="'해당 없음'은 현재 해당되는 상태가 없다는 뜻이고, '기타'는 위 분류 밖의 다른 상태가 있다는 뜻입니다.">
                <div style={{ display: "grid", gridTemplateColumns: "repeat(5, 1fr)", gap: 8 }}>
                  {EMPLOYMENT_STATUS_OPTIONS.map(v => {
                    const active = employ === v;
                    return (
                      <button key={v} disabled={optionalProfileInputDisabled} onClick={() => setEmploy(v)} style={{
                        padding: "11px 4px", borderRadius: 10, whiteSpace: "nowrap",
                        border: `1.5px solid ${active ? A : LINE}`,
                        background: active ? AS : WHITE, color: active ? AI : INK2,
                        fontSize: 13, fontWeight: 700, cursor: optionalProfileInputDisabled ? "not-allowed" : "pointer",
                        opacity: optionalProfileInputDisabled ? 0.6 : 1,
                      }}>
                        {v}
                      </button>
                    );
                  })}
                </div>
              </Field>

              <Field label="가구 형태" hint="'해당 없음'은 특화 가구 조건이 없다는 뜻이고, '기타'는 별도 특화 가구 조건이 있다는 뜻입니다.">
                <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 8 }}>
                  {HOUSEHOLD_TYPES.map(v => {
                    const active = householdType === v;
                    return (
                      <button key={v} disabled={optionalProfileInputDisabled} onClick={() => setHouseholdType(v)} style={{
                        padding: "11px 4px", borderRadius: 10, whiteSpace: "nowrap",
                        border: `1.5px solid ${active ? A : LINE}`,
                        background: active ? AS : WHITE, color: active ? AI : INK2,
                        fontSize: 13, fontWeight: 700, cursor: optionalProfileInputDisabled ? "not-allowed" : "pointer",
                        opacity: optionalProfileInputDisabled ? 0.6 : 1,
                      }}>
                        {v}
                      </button>
                    );
                  })}
                </div>
              </Field>

              <Field label="주거 및 생활 여건" hint="선택 입력이지만 주거·복지 자격조건 매칭 정확도를 높여요">
                <div style={{ padding: "12px 14px", borderRadius: 12, background: "#f8fafc", border: `1px solid ${LINE2}`, fontSize: 12, color: INK2, lineHeight: 1.6 }}>
                  입력한 선택 프로필은 회원가입 직후부터 추천 점수에 반영됩니다.
                  현재 <span style={{ color: AI, fontWeight: 800 }}>{selectedStandardCodeCount}/3개</span> 선택됨
                </div>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, marginTop: 8 }}>
                  <select style={selCss(optionalProfileInputDisabled)} disabled={optionalProfileInputDisabled} value={houseTenureCode} onChange={e => setHouseTenureCode(e.target.value)}>
                    <option value="">주거형태 선택</option>
                    {profileCodeOptions.houseTenure.map((option) => (
                      <option key={option.value} value={option.value}>{option.label}</option>
                    ))}
                  </select>
                  <select style={selCss(optionalProfileInputDisabled)} disabled={optionalProfileInputDisabled} value={housingTypeCode} onChange={e => setHousingTypeCode(e.target.value)}>
                    <option value="">주택유형 선택</option>
                    {profileCodeOptions.housingType.map((option) => (
                      <option key={option.value} value={option.value}>{option.label}</option>
                    ))}
                  </select>
                  <select style={selCss(optionalProfileInputDisabled)} disabled={optionalProfileInputDisabled} value={basicLivingRecipientTypeCode} onChange={e => setBasicLivingRecipientTypeCode(e.target.value)}>
                    <option value="">복지 수급 정보 선택 안 함</option>
                    {profileCodeOptions.basicLivingRecipientType.map((option) => (
                      <option key={option.value} value={option.value}>{option.label}</option>
                    ))}
                  </select>
                </div>
              </Field>

              <label style={{
                display: "flex", gap: 10, alignItems: "flex-start", padding: "13px 14px",
                border: `1px solid ${sensitiveInfoConsentAgreed ? A : LINE}`, borderRadius: 8,
                background: sensitiveInfoConsentAgreed ? AS : "#f8fafc", cursor: "pointer",
              }}>
                <input
                  type="checkbox"
                  checked={sensitiveInfoConsentAgreed}
                  onChange={e => handleSensitiveInfoConsentChange(e.target.checked)}
                  style={{ marginTop: 2, accentColor: A }}
                />
                <span style={{ fontSize: 13, color: INK2, lineHeight: 1.55 }}>
                  장애 관련 지원 정보 등 민감정보 수집·이용에 별도로 동의합니다.
                  <span style={{ display: "block", color: INK3, marginTop: 2 }}>
                    동의하지 않아도 가입할 수 있으며, 해당 정보는 저장하지 않습니다.
                  </span>
                </span>
              </label>

              <Field label="장애 관련 정보" hint="대상이 아니면 '해당 없음'을 선택하세요. 선택 입력이며, 민감정보 동의 시에만 저장됩니다">
                <select style={selCss(!sensitiveInfoConsentAgreed)} disabled={!sensitiveInfoConsentAgreed} value={disabilityGradeCode} onChange={e => setDisabilityGradeCode(e.target.value)}>
                  <option value="">장애 관련 지원 정보 선택 안 함</option>
                  {profileCodeOptions.disabilityGrade.map((option) => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
              </Field>

              <div style={{ display: "flex", gap: 10 }}>
                <button onClick={() => setStep(0)} style={{ flex: 1, padding: "13px 0", borderRadius: 10, border: `1.5px solid ${LINE}`, background: WHITE, color: INK2, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                  ← 이전
                </button>
                <button onClick={handleStep2Next} style={{ ...btnPrimary(false), flex: 2, width: "auto", whiteSpace: "nowrap" }}>
                  다음 →
                </button>
              </div>
            </div>
          )}

          {/* ── Step 3: 우선순위 ── */}
          {step === 2 && (
            <div style={{ display: "flex", flexDirection: "column", gap: 20 }}>
              <div>
                <div style={{ fontSize: 16, fontWeight: 800, color: INK, marginBottom: 4 }}>어떤 정책을 우선적으로 받고 싶으세요?</div>
                <div style={{ fontSize: 13, color: INK3 }}>최대 5개 선택 · 순서가 중요해요 (건너뛸 수 있어요)</div>
                {!optionalProfileConsentAgreed && (
                  <div style={{ marginTop: 8, fontSize: 12, color: INK3 }}>
                    우선순위를 선택하려면 이전 단계에서 선택 개인정보 수집·이용에 동의해주세요.
                  </div>
                )}
              </div>

              <div style={{ display: "grid", gridTemplateColumns: isMobile ? "repeat(2, 1fr)" : "repeat(4, 1fr)", gap: 10 }}>
                {PRIORITY_OPTIONS.map(c => {
                  const idx = priorities.indexOf(c.value);
                  const active = idx >= 0;
                  const disabled = optionalProfileInputDisabled || (!active && priorities.length >= 5);
                  return (
                    <button key={c.value} onClick={() => togglePriority(c.value)}
                      disabled={disabled}
                      style={{
                        position: "relative", padding: "14px 14px", borderRadius: 12,
                        border: `2px solid ${active ? A : LINE}`,
                        background: active ? AS : WHITE,
                        textAlign: "left", cursor: disabled ? "not-allowed" : "pointer",
                        opacity: disabled ? 0.5 : 1,
                        display: "flex", alignItems: "center", gap: 8,
                      }}>
                      <span style={{ width: 12, height: 12, borderRadius: "50%", background: c.fg, flexShrink: 0 }} />
                      <div style={{ fontSize: 13, fontWeight: 700, color: active ? AI : INK, letterSpacing: "-0.02em", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis", minWidth: 0 }}>{c.label}</div>
                      {active && (
                        <span style={{ position: "absolute", top: 6, right: 6, width: 18, height: 18, borderRadius: "50%", background: A, color: WHITE, fontSize: 10, fontWeight: 800, display: "flex", alignItems: "center", justifyContent: "center" }}>
                          {idx + 1}
                        </span>
                      )}
                    </button>
                  );
                })}
              </div>

              {priorities.length > 0 && (
                <div>
                  <div style={{ fontSize: 13, fontWeight: 700, color: INK2, marginBottom: 8 }}>선택한 우선순위 (끌어서 순서 조정 · 모바일은 길게 눌러 끌기)</div>
                  <PrioritySortableList
                    priorities={priorities}
                    onReorder={setPriorities}
                    onRemove={removePriority}
                    getMeta={(val) => {
                      const c = PRIORITY_OPTIONS.find(o => o.value === val);
                      return { label: c?.label, fg: c?.fg };
                    }}
                    compact
                  />
                </div>
              )}

              <div style={{ display: "flex", gap: 10 }}>
                <button onClick={() => setStep(1)} style={{ flex: 1, padding: "13px 0", borderRadius: 10, border: `1.5px solid ${LINE}`, background: WHITE, color: INK2, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                  ← 이전
                </button>
                <button disabled={loading || !step2Valid} onClick={handleSignup} style={{ ...btnPrimary(loading || !step2Valid), flex: 2, width: "auto", whiteSpace: "nowrap" }}>
                  {loading ? <CircularProgress size={20} sx={{ color: WHITE }} /> : "가입 완료"}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>

      <Snackbar open={toast.open} autoHideDuration={3000} onClose={() => setToast(t => ({ ...t, open: false }))} anchorOrigin={{ vertical: "bottom", horizontal: "center" }}>
        <Alert severity={toast.severity}>{toast.msg}</Alert>
      </Snackbar>

      <Dialog open={privacyModalOpen} onClose={() => setPrivacyModalOpen(false)} maxWidth="md" fullWidth scroll="paper">
        <IconButton
          aria-label="닫기"
          onClick={() => setPrivacyModalOpen(false)}
          sx={{ position: "absolute", right: 8, top: 8, zIndex: 1, color: "#6b7280" }}
        >
          <CloseIcon />
        </IconButton>
        <DialogContent dividers>
          <PrivacyPolicyContent />
        </DialogContent>
      </Dialog>
    </div>
  );
}
