import { useState, useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Snackbar, Alert, CircularProgress } from "@mui/material";
import HomeIcon from "@mui/icons-material/Home";
import { useAuthStore } from "../store/authStore";
import api from "../lib/axios";

const A = "#2563eb", A7 = "#1d4ed8";
const BG = "#f7f8fc", WHITE = "#fff";
const INK = "#11131a", INK2 = "#4a4f5c", INK3 = "#6b7280";
const LINE = "#e5e7eb", LINE2 = "#f3f4f6";
const WARN = "#ef4444";

const iCss = (err) => ({
  width: "100%", padding: "12px 14px", borderRadius: 10,
  border: `1.5px solid ${err ? WARN : LINE}`,
  background: WHITE, fontSize: 15, fontFamily: "inherit", color: INK,
  outline: "none", boxSizing: "border-box", transition: "border-color 0.15s",
});

const GUIDE_NUDGE_STORAGE_KEY = "yw-guide-nudge-seen-v1";

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login, setUser } = useAuthStore();

  const [email, setEmail] = useState("");
  const [pw, setPw] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [toast, setToast] = useState({ open: false, msg: "" });

  const buildPostLoginRecommendationNudge = (profile, effectiveHasPriorities) => {
    const standardCodeKeys = [
      "houseTenureCode",
      "housingTypeCode",
      "basicLivingRecipientTypeCode",
      "disabilityGradeCode",
    ];
    const missingCount = standardCodeKeys.filter((key) => !profile?.[key]).length;

    if (effectiveHasPriorities && missingCount === 0) {
      return null;
    }

    return {
      kind: !effectiveHasPriorities && missingCount > 0
        ? "both"
        : !effectiveHasPriorities
          ? "priorities"
          : "standardCodes",
      filledCount: standardCodeKeys.length - missingCount,
    };
  };

  useEffect(() => {
    const reason = location.state?.reason;
    const signupEmail = location.state?.email;
    if (reason === "login-required") {
      const fromPath = location.state?.from?.pathname;
      const loginRequiredMessage = fromPath === "/chat"
        ? "챗봇은 로그인 후 이용 가능합니다."
        : fromPath === "/mypage"
          ? "마이페이지는 로그인 후 이용 가능합니다."
          : "로그인 후 이용 가능한 기능입니다.";
      setToast({ open: true, msg: loginRequiredMessage });
    }
    if (reason === "expired") setToast({ open: true, msg: "로그인 상태가 만료되어 다시 로그인해야 합니다." });
    if (reason === "password-reset-complete") setToast({ open: true, msg: "비밀번호가 변경되었습니다. 새 비밀번호로 로그인해주세요." });
    if (reason === "signup-complete") {
      setToast({ open: true, msg: "가입 요청이 처리되었습니다. 로그인하거나 비밀번호 재설정을 이용해주세요." });
      if (typeof signupEmail === "string" && signupEmail.trim()) setEmail(signupEmail);
    }
  }, [location.state]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!email || !pw) return;
    setLoading(true);
    setError("");
    try {
      const { data } = await api.post("/api/auth/login", { email, password: pw });
      const accessToken = data?.data?.accessToken;
      if (!accessToken) throw new Error("로그인 응답에 accessToken이 없습니다");
      login(accessToken, { email });
      let profileForNudge = null;
      let effectiveHasPriorities = false;
      try {
        const profileResponse = await api.get("/api/users/me");
        const profile = profileResponse?.data?.data;
        profileForNudge = profile;
        effectiveHasPriorities = Array.isArray(profile?.priorities) && profile.priorities.length > 0;
        login(accessToken, {
          name: profile?.name ?? "",
          email: profile?.email ?? email,
          hasPriorities: effectiveHasPriorities,
        });
      } catch {
        login(accessToken, { email });
      }
      const signupPriorities = Array.isArray(location.state?.signupPriorities) ? location.state.signupPriorities : [];
      if (signupPriorities.length > 0) {
        try {
          await api.put("/api/users/me/priorities", { priorityCodes: signupPriorities });
          setUser({ hasPriorities: true });
          effectiveHasPriorities = true;
        } catch {
          // Priority save is best-effort here; login completion should still continue.
        }
      }
      const from = location.state?.from;
      const nestedFromState = from?.state ?? {};
      const postLoginAction = location.state?.postLoginAction ?? nestedFromState.postLoginAction;
      const chatFrom = location.state?.chatFrom ?? nestedFromState.chatFrom;
      const restoredState = {
        ...nestedFromState,
        ...(postLoginAction ? { postLoginAction } : {}),
        ...(chatFrom ? { chatFrom } : {}),
      };
      const postLoginRecommendationNudge = buildPostLoginRecommendationNudge(profileForNudge, effectiveHasPriorities);
      const targetPathname = from?.pathname || "/";
      if (targetPathname === "/" && postLoginRecommendationNudge) {
        restoredState.postLoginRecommendationNudge = postLoginRecommendationNudge;
      }
      if (
        targetPathname === "/"
        && typeof window !== "undefined"
        && !window.localStorage.getItem(GUIDE_NUDGE_STORAGE_KEY)
      ) {
        restoredState.postLoginGuideNudge = {
          source: location.state?.reason === "signup-complete" ? "signup" : "login",
        };
      }
      navigate(from?.pathname ? `${from.pathname}${from.search ?? ""}` : "/", {
        replace: true,
        state: Object.keys(restoredState).length ? restoredState : undefined,
      });
    } catch (err) {
      setError(err.response?.data?.message ?? "로그인 중 오류가 발생했습니다");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ minHeight: "100vh", background: BG, display: "flex", flexDirection: "column" }}>
      {/* brand bar */}
      <div style={{ background: A, padding: "14px 24px", display: "flex", alignItems: "center", cursor: "pointer" }} onClick={() => navigate("/")}>
        <div style={{ width: 28, height: 28, borderRadius: 8, background: "rgba(255,255,255,0.2)", display: "flex", alignItems: "center", justifyContent: "center", color: WHITE, marginRight: 8 }}><HomeIcon sx={{ fontSize: 18 }} /></div>
        <span style={{ fontSize: 16, fontWeight: 700, color: WHITE }}>청년복지플랫폼</span>
      </div>

      <div style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", padding: "32px 16px" }}>
        <div style={{ width: "100%", maxWidth: 420, background: WHITE, borderRadius: 20, border: `1px solid ${LINE}`, padding: "48px 40px", boxShadow: "0 4px 24px rgba(37,99,235,0.06)" }}>
          {/* logo */}
          <div style={{ textAlign: "center", marginBottom: 36 }}>
            <div style={{ width: 56, height: 56, borderRadius: 16, background: `linear-gradient(135deg,${A},#1e3a8a)`, margin: "0 auto 16px", display: "flex", alignItems: "center", justifyContent: "center", color: WHITE }}>
              <HomeIcon sx={{ fontSize: 30 }} />
            </div>
            <div style={{ fontSize: 24, fontWeight: 800, color: INK, letterSpacing: "-0.02em", marginBottom: 6 }}>로그인</div>
            <div style={{ fontSize: 14, color: INK3 }}>나에게 맞는 청년 복지 정책을 찾아보세요</div>
          </div>

          <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: 16 }}>
            <div>
              <label style={{ fontSize: 13, fontWeight: 700, color: INK2, display: "block", marginBottom: 6 }}>이메일</label>
              <input
                type="email"
                value={email}
                onChange={e => setEmail(e.target.value)}
                placeholder="example@email.com"
                style={iCss(false)}
                onFocus={e => { e.currentTarget.style.borderColor = A; }}
                onBlur={e => { e.currentTarget.style.borderColor = LINE; }}
              />
            </div>
            <div>
              <label style={{ fontSize: 13, fontWeight: 700, color: INK2, display: "block", marginBottom: 6 }}>비밀번호</label>
              <input
                type="password"
                value={pw}
                onChange={e => { setPw(e.target.value); if (error) setError(""); }}
                placeholder="비밀번호를 입력하세요"
                style={iCss(!!error)}
                onFocus={e => { e.currentTarget.style.borderColor = error ? WARN : A; }}
                onBlur={e => { e.currentTarget.style.borderColor = error ? WARN : LINE; }}
              />
              {error && <div style={{ fontSize: 12, color: WARN, marginTop: 5 }}>⚠ {error}</div>}
            </div>

            <button
              type="submit"
              disabled={!email || !pw || loading}
              style={{
                marginTop: 8, width: "100%", padding: "14px 0", borderRadius: 10, border: 0,
                background: !email || !pw ? LINE2 : A, color: !email || !pw ? INK3 : WHITE,
                fontSize: 15, fontWeight: 700, cursor: !email || !pw ? "not-allowed" : "pointer",
                display: "flex", alignItems: "center", justifyContent: "center", gap: 8,
              }}
              onMouseEnter={e => { if (email && pw && !loading) e.currentTarget.style.background = A7; }}
              onMouseLeave={e => { if (email && pw && !loading) e.currentTarget.style.background = A; }}
            >
              {loading ? <CircularProgress size={20} sx={{ color: WHITE }} /> : "로그인"}
            </button>
          </form>

          <div style={{ display: "flex", justifyContent: "center", alignItems: "center", gap: 16, marginTop: 24 }}>
            <button onClick={() => navigate("/signup", {
              state: {
                from: location.state?.from,
                chatFrom: location.state?.chatFrom ?? location.state?.from?.state?.chatFrom,
                postLoginAction: location.state?.postLoginAction ?? location.state?.from?.state?.postLoginAction,
              },
            })} style={{ background: "transparent", border: 0, color: A, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
              회원가입
            </button>
            <div style={{ width: 1, height: 12, background: LINE }} />
            <button onClick={() => navigate("/reset-password", {
              state: {
                from: location.state?.from,
                chatFrom: location.state?.chatFrom ?? location.state?.from?.state?.chatFrom,
                email,
                postLoginAction: location.state?.postLoginAction ?? location.state?.from?.state?.postLoginAction,
              },
            })} style={{ background: "transparent", border: 0, color: INK3, fontSize: 14, cursor: "pointer" }}>
              비밀번호 찾기
            </button>
          </div>
        </div>
      </div>

      <Snackbar open={toast.open} autoHideDuration={2000} onClose={() => setToast(t => ({ ...t, open: false }))} anchorOrigin={{ vertical: "bottom", horizontal: "center" }} sx={{ bottom: { xs: 72, sm: 0 } }}>
        <Alert severity="info">{toast.msg}</Alert>
      </Snackbar>
    </div>
  );
}
