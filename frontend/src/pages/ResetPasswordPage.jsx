import { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { Alert, CircularProgress, Snackbar } from "@mui/material";
import api from "../lib/axios";

const A = "#2563eb", A7 = "#1d4ed8";
const BG = "#f7f8fc", WHITE = "#fff";
const INK = "#11131a", INK2 = "#4a4f5c", INK3 = "#6b7280";
const LINE = "#e5e7eb", LINE2 = "#f3f4f6";
const WARN = "#ef4444";

const readTokenFromHash = (hash) => {
  if (!hash) return "";
  const normalizedHash = hash.startsWith("#") ? hash.slice(1) : hash;
  const params = new URLSearchParams(normalizedHash);
  return params.get("token")?.trim() ?? "";
};

const iCss = (err) => ({
  width: "100%", padding: "12px 14px", borderRadius: 10,
  border: `1.5px solid ${err ? WARN : LINE}`,
  background: WHITE, fontSize: 15, fontFamily: "inherit", color: INK,
  outline: "none", boxSizing: "border-box",
});

export default function ResetPasswordPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const nestedFromState = location.state?.from?.state ?? {};
  const chatFrom = location.state?.chatFrom ?? nestedFromState.chatFrom;
  const postLoginAction = location.state?.postLoginAction ?? nestedFromState.postLoginAction;
  const [searchParams] = useSearchParams();
  const [token, setToken] = useState(() => {
    const hashToken = readTokenFromHash(window.location.hash);
    const queryToken = searchParams.get("token")?.trim() ?? "";
    return hashToken || queryToken;
  });
  const hasToken = useMemo(() => token.trim().length > 0, [token]);

  const [email, setEmail] = useState(location.state?.email ?? "");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [toast, setToast] = useState({ open: false, msg: "", severity: "info" });

  const showToast = (msg, severity = "info") => setToast({ open: true, msg, severity });

  useEffect(() => {
    const hashToken = readTokenFromHash(window.location.hash);
    const queryToken = searchParams.get("token")?.trim() ?? "";
    const resolvedToken = hashToken || queryToken;

    if (resolvedToken) {
      if (resolvedToken !== token) {
        setToken(resolvedToken);
      }
    }

    if (queryToken && !hashToken) {
      const nextHash = `#token=${encodeURIComponent(queryToken)}`;
      window.history.replaceState(window.history.state, document.title, `${window.location.pathname}${nextHash}`);
    }
  }, [searchParams, token]);

  const handleRequest = async (e) => {
    e.preventDefault();
    if (!email.trim()) return;
    setLoading(true);
    setError("");
    try {
      await api.post("/api/auth/password-reset/request", { email: email.trim() });
      showToast("입력한 이메일로 재설정 안내 메일을 보냈습니다. 메일함을 확인해주세요.");
      setEmail("");
    } catch (err) {
      setError(err.response?.data?.message ?? "재설정 메일 요청에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const handleConfirm = async (e) => {
    e.preventDefault();
    if (!newPassword || !confirmPassword) return;
    if (newPassword !== confirmPassword) { setError("새 비밀번호 확인이 일치하지 않습니다."); return; }
    if (newPassword.length < 8 || newPassword.length > 100) {
      setError("새 비밀번호는 8자 이상 100자 이하여야 합니다.");
      return;
    }
    setLoading(true);
    setError("");
    try {
      await api.post("/api/auth/password-reset/confirm", { token, newPassword });
      showToast("비밀번호가 변경되었습니다. 새 비밀번호로 다시 로그인해주세요.", "success");
      setTimeout(() => navigate("/login", {
        state: {
          from: location.state?.from,
          chatFrom,
          reason: "password-reset-complete",
          email: location.state?.email,
          postLoginAction,
        },
      }), 1200);
    } catch (err) {
      const code = err.response?.data?.errorCode;
      setError(code === "A008"
        ? "링크가 만료되었거나 이미 사용되었습니다. 다시 재설정 메일을 요청해주세요."
        : err.response?.data?.message ?? "비밀번호 재설정에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ minHeight: "100vh", background: BG, display: "flex", flexDirection: "column" }}>
      <div style={{ background: A, padding: "14px 24px", display: "flex", alignItems: "center", cursor: "pointer" }} onClick={() => navigate("/")}>
        <div style={{ width: 28, height: 28, borderRadius: 8, background: "rgba(255,255,255,0.2)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 14, marginRight: 8 }}>🏠</div>
        <span style={{ fontSize: 16, fontWeight: 700, color: WHITE }}>청년복지플랫폼</span>
      </div>

      <div style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", padding: "32px 16px" }}>
        <div style={{ width: "100%", maxWidth: 460, background: WHITE, borderRadius: 20, border: `1px solid ${LINE}`, padding: "48px 40px", boxShadow: "0 4px 24px rgba(37,99,235,0.06)" }}>
          <div style={{ textAlign: "center", marginBottom: 36 }}>
            <div style={{ width: 56, height: 56, borderRadius: 16, background: `linear-gradient(135deg,${A},#1e3a8a)`, margin: "0 auto 16px", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 24 }}>
              🔑
            </div>
            <div style={{ fontSize: 22, fontWeight: 800, color: INK, letterSpacing: "-0.02em", marginBottom: 6 }}>
              {hasToken ? "새 비밀번호 설정" : "비밀번호 재설정"}
            </div>
            <div style={{ fontSize: 14, color: INK3, lineHeight: 1.6 }}>
              {hasToken
                ? "새 비밀번호를 입력해주세요."
                : "가입한 이메일을 입력하면 비밀번호 재설정 링크를 보내드립니다."}
            </div>
          </div>

          <form onSubmit={hasToken ? handleConfirm : handleRequest} style={{ display: "flex", flexDirection: "column", gap: 16 }}>
            {!hasToken ? (
              <div>
                <label style={{ fontSize: 13, fontWeight: 700, color: INK2, display: "block", marginBottom: 6 }}>가입한 이메일</label>
                <input
                  type="email"
                  value={email}
                  onChange={e => { setEmail(e.target.value); setError(""); }}
                  placeholder="example@email.com"
                  style={iCss(!!error)}
                />
                {error && <div style={{ fontSize: 12, color: WARN, marginTop: 5 }}>⚠ {error}</div>}
              </div>
            ) : (
              <>
                <div>
                  <label style={{ fontSize: 13, fontWeight: 700, color: INK2, display: "block", marginBottom: 6 }}>새 비밀번호</label>
                  <input type="password" value={newPassword} onChange={e => { setNewPassword(e.target.value); setError(""); }} placeholder="8자 이상 입력" style={iCss(!!error)} />
                  {error && <div style={{ fontSize: 12, color: WARN, marginTop: 5 }}>⚠ {error}</div>}
                </div>
                <div>
                  <label style={{ fontSize: 13, fontWeight: 700, color: INK2, display: "block", marginBottom: 6 }}>새 비밀번호 확인</label>
                  <input type="password" value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} placeholder="비밀번호를 다시 입력" style={iCss(false)} />
                </div>
              </>
            )}

            <button
              type="submit"
              disabled={loading || (!hasToken && !email.trim()) || (hasToken && (!newPassword || !confirmPassword))}
              style={{
                marginTop: 8, width: "100%", padding: "14px 0", borderRadius: 10, border: 0,
                background: A, color: WHITE, fontSize: 15, fontWeight: 700, cursor: "pointer",
                display: "flex", alignItems: "center", justifyContent: "center", gap: 8,
              }}
              onMouseEnter={e => { if (!loading) e.currentTarget.style.background = A7; }}
              onMouseLeave={e => { if (!loading) e.currentTarget.style.background = A; }}
            >
              {loading ? <CircularProgress size={20} sx={{ color: WHITE }} /> : (hasToken ? "비밀번호 변경" : "재설정 메일 보내기")}
            </button>
          </form>

          <div style={{ textAlign: "center", marginTop: 24 }}>
            <button onClick={() => navigate("/login", {
              state: {
                from: location.state?.from,
                email,
                chatFrom,
                postLoginAction,
              },
            })} style={{ background: "transparent", border: 0, color: A, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
              ← 로그인으로 돌아가기
            </button>
          </div>
        </div>
      </div>

      <Snackbar open={toast.open} autoHideDuration={2500} onClose={() => setToast(t => ({ ...t, open: false }))} anchorOrigin={{ vertical: "bottom", horizontal: "center" }}>
        <Alert severity={toast.severity} onClose={() => setToast(t => ({ ...t, open: false }))}>{toast.msg}</Alert>
      </Snackbar>
    </div>
  );
}
