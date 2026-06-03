import { useMemo, useState } from "react";
import { Alert, Snackbar, useMediaQuery } from "@mui/material";
import { useLocation, useNavigate } from "react-router-dom";
import Header from "../components/Header";
import FloatingNav from "../components/FloatingNav";
import api from "../lib/axios";
import { useAuthStore } from "../store/authStore";

const BG = "#f7f8fc";
const WHITE = "#ffffff";
const INK = "#11131a";
const INK2 = "#4a4f5c";
const INK3 = "#6b7280";
const LINE = "#e5e7eb";
const A = "#2563eb";
const A7 = "#1d4ed8";
const AS = "#e8efff";
const WARN_BG = "#fff7ed";
const WARN = "#c2410c";
const OK_BG = "#ecfdf5";
const OK = "#047857";

const SUPPORT_CATEGORIES = [
  { value: "ACCOUNT_LOGIN", label: "로그인/계정" },
  { value: "RECOMMENDATION_CHATBOT", label: "추천/챗봇" },
  { value: "ALERTS_BOOKMARKS", label: "알림/북마크" },
  { value: "SEARCH_FILTER", label: "정책 검색/필터" },
  { value: "GENERAL_FEEDBACK", label: "기타 의견/제안" },
];

function SectionCard({ title, body, tone = "default" }) {
  const styles = tone === "warn"
    ? { background: WARN_BG, border: "#fed7aa", titleColor: WARN }
    : tone === "ok"
      ? { background: OK_BG, border: "#a7f3d0", titleColor: OK }
      : { background: WHITE, border: LINE, titleColor: INK };

  return (
    <div
      style={{
        background: styles.background,
        border: `1px solid ${styles.border}`,
        borderRadius: 18,
        padding: 20,
      }}
    >
      <div style={{ fontSize: 17, fontWeight: 800, color: styles.titleColor }}>{title}</div>
      <div style={{ marginTop: 8, fontSize: 14, lineHeight: 1.7, color: INK2 }}>{body}</div>
    </div>
  );
}

export default function SupportPage() {
  const isMobile = useMediaQuery("(max-width: 1199px)");
  const location = useLocation();
  const navigate = useNavigate();
  const { isLoggedIn, user } = useAuthStore();
  const [category, setCategory] = useState("GENERAL_FEEDBACK");
  const [contactEmail, setContactEmail] = useState(user?.email ?? "");
  const [routePath, setRoutePath] = useState(location.state?.from?.pathname ?? "");
  const [message, setMessage] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [toast, setToast] = useState({ open: false, severity: "success", message: "" });

  const authState = useMemo(() => ({ from: location }), [location]);

  const showToast = (severity, nextMessage) => {
    setToast({ open: true, severity, message: nextMessage });
  };

  const handleSubmit = async () => {
    if (!contactEmail.trim() || !message.trim()) {
      showToast("error", "이메일과 문의 내용을 입력해주세요.");
      return;
    }

    setSubmitting(true);
    try {
      await api.post("/api/support/inquiries", {
        category,
        contactEmail: contactEmail.trim(),
        message: message.trim(),
        routePath: routePath.trim() || null,
      });
      setMessage("");
      showToast("success", "문의가 접수되었습니다. 확인 후 답변드리겠습니다.");
    } catch (error) {
      showToast("error", error?.response?.data?.message || "문의 접수에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ minHeight: "100vh", background: BG }}>
      <Header />
      <main style={{ maxWidth: 1080, margin: "0 auto", padding: isMobile ? "22px 16px 110px" : "28px 24px 120px" }}>
        <section
          style={{
            borderRadius: 28,
            background: "linear-gradient(135deg, #1e3a8a 0%, #2563eb 58%, #60a5fa 100%)",
            color: WHITE,
            padding: isMobile ? "28px 20px" : "40px 42px",
          }}
        >
          <div style={{ display: "inline-flex", padding: "7px 14px", borderRadius: 999, background: "rgba(255,255,255,0.14)", fontSize: 12, fontWeight: 800, letterSpacing: "0.05em" }}>
            SUPPORT
          </div>
          <h1 style={{ margin: "18px 0 10px", fontSize: isMobile ? 30 : 40, lineHeight: 1.18, letterSpacing: "-0.04em" }}>
            서비스 사용 문의 / 의견 제안
          </h1>
          <p style={{ margin: 0, maxWidth: 720, fontSize: 15, lineHeight: 1.75, color: "rgba(255,255,255,0.88)" }}>
            로그인이 안 되거나, 추천/챗봇/알림/검색을 쓰다가 막히는 경우 여기로 남겨주세요.
            정책 정보 자체가 틀린 경우는 정책 상세의 `정책 오류 제보`를 쓰는 게 더 빠릅니다.
          </p>
        </section>

        <section style={{ marginTop: 30, display: "grid", gap: 14, gridTemplateColumns: isMobile ? "1fr" : "1fr 1fr" }}>
          <SectionCard
            title="서비스 사용 문의"
            body="로그인, 추천, 챗봇, 검색, 알림, 북마크처럼 플랫폼을 쓰다가 막히거나 헷갈리는 경우에 남겨주세요."
            tone="ok"
          />
          <SectionCard
            title="정책 오류 제보와는 다릅니다"
            body="지역, 신청 기간, 자격조건, 링크 오류처럼 정책 데이터 자체가 잘못된 경우는 정책 상세 페이지의 `정책 오류 제보`가 운영 triage에 더 직접 연결됩니다."
            tone="warn"
          />
        </section>

        <section style={{ marginTop: 34, background: WHITE, border: `1px solid ${LINE}`, borderRadius: 24, padding: isMobile ? "22px 16px" : "28px 28px 24px" }}>
          <div style={{ fontSize: 24, fontWeight: 800, color: INK, letterSpacing: "-0.03em" }}>문의 남기기</div>
          <div style={{ marginTop: 8, fontSize: 14, lineHeight: 1.7, color: INK2 }}>
            운영자가 어떤 화면에서 무슨 문제가 있었는지 빠르게 볼 수 있도록, 문의 유형과 내용을 간단히 적어주세요.
          </div>

          <div style={{ display: "grid", gap: 16, marginTop: 22 }}>
            <label style={{ display: "grid", gap: 8 }}>
              <span style={{ fontSize: 13, fontWeight: 700, color: INK2 }}>문의 유형</span>
              <select
                value={category}
                onChange={(event) => setCategory(event.target.value)}
                style={{ height: 46, borderRadius: 12, border: `1px solid ${LINE}`, padding: "0 14px", fontSize: 14, color: INK, background: WHITE }}
              >
                {SUPPORT_CATEGORIES.map((item) => (
                  <option key={item.value} value={item.value}>{item.label}</option>
                ))}
              </select>
            </label>

            <label style={{ display: "grid", gap: 8 }}>
              <span style={{ fontSize: 13, fontWeight: 700, color: INK2 }}>답변 받을 이메일</span>
              <input
                type="email"
                value={contactEmail}
                onChange={(event) => setContactEmail(event.target.value)}
                placeholder="example@email.com"
                style={{ height: 46, borderRadius: 12, border: `1px solid ${LINE}`, padding: "0 14px", fontSize: 14, color: INK, background: WHITE }}
              />
            </label>

            <label style={{ display: "grid", gap: 8 }}>
              <span style={{ fontSize: 13, fontWeight: 700, color: INK2 }}>어느 화면에서 불편했나요? (선택)</span>
              <input
                value={routePath}
                onChange={(event) => setRoutePath(event.target.value)}
                placeholder="/chat, /policies, /mypage"
                style={{ height: 46, borderRadius: 12, border: `1px solid ${LINE}`, padding: "0 14px", fontSize: 14, color: INK, background: WHITE }}
              />
            </label>

            <label style={{ display: "grid", gap: 8 }}>
              <span style={{ fontSize: 13, fontWeight: 700, color: INK2 }}>문의 내용</span>
              <textarea
                value={message}
                onChange={(event) => setMessage(event.target.value)}
                placeholder="무엇이 기대와 달랐는지, 어떤 점이 불편했는지 적어주세요."
                rows={8}
                style={{ borderRadius: 14, border: `1px solid ${LINE}`, padding: "14px 16px", fontSize: 14, color: INK, background: WHITE, resize: "vertical", lineHeight: 1.65 }}
              />
            </label>

            <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
              <button
                onClick={handleSubmit}
                disabled={submitting}
                style={{
                  padding: "13px 18px",
                  borderRadius: 12,
                  border: 0,
                  background: submitting ? "#93c5fd" : A,
                  color: WHITE,
                  fontSize: 14,
                  fontWeight: 800,
                  cursor: submitting ? "wait" : "pointer",
                }}
              >
                {submitting ? "접수 중..." : "문의 보내기"}
              </button>
              <button
                onClick={() => navigate("/guide")}
                style={{
                  padding: "13px 18px",
                  borderRadius: 12,
                  border: `1px solid ${LINE}`,
                  background: WHITE,
                  color: INK,
                  fontSize: 14,
                  fontWeight: 700,
                  cursor: "pointer",
                }}
              >
                이용가이드 보기
              </button>
              {!isLoggedIn && (
                <button
                  onClick={() => navigate("/login", { state: authState })}
                  style={{
                    padding: "13px 18px",
                    borderRadius: 12,
                    border: `1px solid ${LINE}`,
                    background: AS,
                    color: A7,
                    fontSize: 14,
                    fontWeight: 700,
                    cursor: "pointer",
                  }}
                >
                  로그인하기
                </button>
              )}
            </div>
          </div>
        </section>
      </main>

      <FloatingNav />

      <Snackbar open={toast.open} autoHideDuration={3200} onClose={() => setToast((prev) => ({ ...prev, open: false }))}>
        <Alert severity={toast.severity} onClose={() => setToast((prev) => ({ ...prev, open: false }))} sx={{ width: "100%" }}>
          {toast.message}
        </Alert>
      </Snackbar>
    </div>
  );
}
