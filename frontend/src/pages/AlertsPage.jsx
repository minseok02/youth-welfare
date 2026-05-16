import { useCallback, useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Alert, CircularProgress, Snackbar } from "@mui/material";
import Header from "../components/Header";
import FloatingNav from "../components/FloatingNav";
import api from "../lib/axios";

const A = "#2563eb", A7 = "#1d4ed8", BG = "#f7f8fc", WHITE = "#fff";
const WARN = "#ef4444";
const INK = "#11131a", INK2 = "#4a4f5c", INK3 = "#6b7280";
const LINE = "#e5e7eb", LINE2 = "#f3f4f6";
const AI = "#1e3a8a";

const ALERT_KIND_META = {
  ALL: { label: "전체" },
  RECOMMENDATION_DIGEST: { label: "추천 알림" },
  DEADLINE_REMINDER: { label: "마감 임박" },
};

function SectionCard({ title, desc, actions, children }) {
  return (
    <div style={{ background: WHITE, border: `1px solid ${LINE}`, borderRadius: 18, padding: 28, marginBottom: 16 }}>
      {(title || actions) && (
        <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: 16, marginBottom: desc ? 16 : 20 }}>
          <div>
            {title && <div style={{ fontSize: 18, fontWeight: 800, letterSpacing: "-0.01em", color: INK }}>{title}</div>}
            {desc && <div style={{ fontSize: 13, color: INK3, marginTop: 4 }}>{desc}</div>}
          </div>
          {actions}
        </div>
      )}
      {children}
    </div>
  );
}

const formatAlertTime = (value) => {
  if (!value) return "";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return "";
  const diffMinutes = Math.floor((Date.now() - parsed.getTime()) / 60000);
  if (diffMinutes < 1) return "방금 전";
  if (diffMinutes < 60) return `${diffMinutes}분 전`;
  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours}시간 전`;
  const diffDays = Math.floor(diffHours / 24);
  if (diffDays < 7) return `${diffDays}일 전`;
  return new Intl.DateTimeFormat("ko-KR", {
    month: "numeric",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(parsed);
};

export default function AlertsPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [alerts, setAlerts] = useState([]);
  const [alertsLoading, setAlertsLoading] = useState(false);
  const [alertUnreadCount, setAlertUnreadCount] = useState(0);
  const [alertActionLoadingId, setAlertActionLoadingId] = useState(null);
  const [kindFilter, setKindFilter] = useState("ALL");
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [toast, setToast] = useState({ open: false, msg: "", severity: "success" });

  const showToast = useCallback((msg, severity = "success") => {
    setToast({ open: true, msg, severity });
  }, []);

  const fetchAlertUnreadCount = useCallback(async (signal) => {
    const { data } = await api.get("/api/notifications/me/unread-count", { signal });
    setAlertUnreadCount(Number(data?.data?.unreadCount ?? 0));
  }, []);

  const fetchAlerts = useCallback(async (signal) => {
    setAlertsLoading(true);
    try {
      const { data } = await api.get("/api/notifications/me", { signal });
      setAlerts(Array.isArray(data?.data) ? data.data : []);
    } finally {
      if (!signal?.aborted) setAlertsLoading(false);
    }
  }, []);

  const syncAlerts = useCallback(async () => {
    const controller = new AbortController();
    try {
      await Promise.all([
        fetchAlerts(controller.signal),
        fetchAlertUnreadCount(controller.signal),
      ]);
    } finally {
      controller.abort();
    }
  }, [fetchAlertUnreadCount, fetchAlerts]);

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([
      fetchAlerts(controller.signal),
      fetchAlertUnreadCount(controller.signal),
    ]).catch(() => {
      if (!controller.signal.aborted) {
        showToast("알림함을 불러오지 못했습니다", "error");
      }
    });
    return () => controller.abort();
  }, [fetchAlertUnreadCount, fetchAlerts, showToast]);

  const markAlertRead = useCallback(async (alertId, silent = false) => {
    setAlertActionLoadingId(alertId);
    try {
      await api.patch(`/api/notifications/${alertId}/read`);
      setAlerts((prev) => prev.map((alert) => (
        alert.id === alertId
          ? { ...alert, status: "READ", readAt: alert.readAt ?? new Date().toISOString() }
          : alert
      )));
      setAlertUnreadCount((prev) => Math.max(prev - 1, 0));
      if (!silent) showToast("읽음 처리했습니다");
    } catch {
      showToast("알림 읽음 처리에 실패했습니다", "error");
      throw new Error("alert read failed");
    } finally {
      setAlertActionLoadingId(null);
    }
  }, [showToast]);

  const hideAlert = useCallback(async (alertId) => {
    setAlertActionLoadingId(alertId);
    try {
      await api.patch(`/api/notifications/${alertId}/hide`);
      setAlerts((prev) => prev.filter((alert) => alert.id !== alertId));
      setAlertUnreadCount((prev) => {
        const target = alerts.find((alert) => alert.id === alertId);
        return target?.status === "UNREAD" ? Math.max(prev - 1, 0) : prev;
      });
      showToast("알림을 숨겼습니다");
    } catch {
      showToast("알림 숨김 처리에 실패했습니다", "error");
    } finally {
      setAlertActionLoadingId(null);
    }
  }, [alerts, showToast]);

  const openAlert = useCallback(async (alert) => {
    try {
      if (alert.status === "UNREAD") {
        await markAlertRead(alert.id, true);
      }
      if (alert.deeplinkUrl) {
        navigate(alert.deeplinkUrl, {
          state: {
            from: location,
          },
        });
      } else {
        showToast("연결된 화면이 없는 알림입니다", "info");
      }
    } catch {
      // markAlertRead already surfaced the error.
    }
  }, [location, markAlertRead, navigate, showToast]);

  const filteredAlerts = alerts.filter((alert) => {
    if (unreadOnly && alert.status !== "UNREAD") {
      return false;
    }
    if (kindFilter !== "ALL" && alert.kind !== kindFilter) {
      return false;
    }
    return true;
  });

  return (
    <div style={{ minHeight: "100vh", background: BG }}>
      <Header />
      <div style={{ maxWidth: 980, margin: "0 auto", padding: "0 24px 64px" }}>
        <nav style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12, color: INK3, padding: "20px 0 8px" }}>
          <span style={{ cursor: "pointer" }} onClick={() => navigate("/")}>홈</span>
          <span>›</span>
          <span style={{ color: INK2, fontWeight: 600 }}>알림함</span>
        </nav>

        <div style={{ display: "flex", alignItems: "flex-end", justifyContent: "space-between", gap: 16, flexWrap: "wrap", marginBottom: 20 }}>
          <div>
            <div style={{ fontSize: 28, fontWeight: 800, letterSpacing: "-0.02em", color: INK, marginBottom: 6 }}>알림함</div>
            <div style={{ fontSize: 13, color: INK3 }}>
              추천 digest와 마감 임박 알림을 앱 안에서 다시 확인할 수 있어요.
            </div>
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
            <div style={{ fontSize: 13, color: INK3 }}>
              읽지 않은 알림 <strong style={{ color: alertUnreadCount > 0 ? WARN : INK2 }}>{alertUnreadCount}</strong>개
            </div>
            <button
              onClick={() => navigate("/mypage?tab=3", { state: { from: location } })}
              style={{ padding: "10px 14px", borderRadius: 10, border: `1px solid ${LINE}`, background: WHITE, color: INK2, fontSize: 12, fontWeight: 700, cursor: "pointer" }}
            >
              알림 설정 열기
            </button>
          </div>
        </div>

        <SectionCard
          title="최근 알림"
          desc="읽음 처리, 숨기기, deep link 이동을 이 화면에서 바로 처리할 수 있어요."
          actions={(
            <div style={{ display: "flex", gap: 8 }}>
              <button
                onClick={() => setUnreadOnly((prev) => !prev)}
                style={{
                  padding: "8px 12px",
                  borderRadius: 10,
                  border: `1px solid ${unreadOnly ? "#bfdbfe" : LINE}`,
                  background: unreadOnly ? "#eff6ff" : WHITE,
                  color: unreadOnly ? AI : INK2,
                  fontSize: 12,
                  fontWeight: 700,
                  cursor: "pointer",
                }}
              >
                읽지 않은 알림만
              </button>
              <button
                onClick={() => syncAlerts().catch(() => showToast("알림함 새로고침에 실패했습니다", "error"))}
                style={{ padding: "8px 12px", borderRadius: 10, border: `1px solid ${LINE}`, background: WHITE, color: INK2, fontSize: 12, fontWeight: 700, cursor: "pointer" }}
              >
                새로고침
              </button>
            </div>
          )}
        >
          <div style={{ display: "flex", flexWrap: "wrap", gap: 8, marginBottom: 16 }}>
            {Object.entries(ALERT_KIND_META).map(([value, meta]) => {
              const active = kindFilter === value;
              return (
                <button
                  key={value}
                  onClick={() => setKindFilter(value)}
                  style={{
                    padding: "8px 12px",
                    borderRadius: 999,
                    border: `1px solid ${active ? "#bfdbfe" : LINE}`,
                    background: active ? "#eff6ff" : WHITE,
                    color: active ? AI : INK2,
                    fontSize: 12,
                    fontWeight: 700,
                    cursor: "pointer",
                  }}
                >
                  {meta.label}
                </button>
              );
            })}
            <div style={{ marginLeft: "auto", fontSize: 12, color: INK3, alignSelf: "center" }}>
              현재 {filteredAlerts.length}개 표시
            </div>
          </div>

          {alertsLoading ? (
            <div style={{ display: "flex", justifyContent: "center", padding: "34px 0" }}>
              <CircularProgress size={24} />
            </div>
          ) : filteredAlerts.length > 0 ? (
            <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
              {filteredAlerts.map((alert) => {
                const unread = alert.status === "UNREAD";
                const loading = alertActionLoadingId === alert.id;
                return (
                  <div key={alert.id} style={{
                    border: `1px solid ${unread ? "#bfdbfe" : LINE}`,
                    background: unread ? "#f8fbff" : WHITE,
                    borderRadius: 16,
                    padding: 18,
                    boxShadow: unread ? "0 8px 18px rgba(37,99,235,0.08)" : "0 1px 2px rgba(20,30,80,0.03)",
                  }}>
                    <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12, marginBottom: 8 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
                        <span style={{
                          padding: "4px 10px",
                          borderRadius: 99,
                          fontSize: 11,
                          fontWeight: 700,
                          background: unread ? "#dbeafe" : BG,
                          color: unread ? AI : INK2,
                        }}>
                          {ALERT_KIND_META[alert.kind]?.label ?? alert.kind}
                        </span>
                        {unread && (
                          <span style={{ color: WARN, fontSize: 11, fontWeight: 800 }}>NEW</span>
                        )}
                      </div>
                      <div style={{ fontSize: 12, color: INK3 }}>{formatAlertTime(alert.createdAt)}</div>
                    </div>

                    <div style={{ fontSize: 16, fontWeight: 800, color: INK, marginBottom: 6 }}>{alert.title}</div>
                    {alert.body && (
                      <div style={{ fontSize: 13, color: INK2, lineHeight: 1.7, marginBottom: 14 }}>{alert.body}</div>
                    )}

                    <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
                      <button
                        onClick={() => openAlert(alert)}
                        disabled={loading}
                        style={{
                          padding: "9px 14px",
                          borderRadius: 10,
                          border: 0,
                          background: A,
                          color: WHITE,
                          fontSize: 12,
                          fontWeight: 700,
                          cursor: loading ? "wait" : "pointer",
                          opacity: loading ? 0.75 : 1,
                        }}
                        onMouseEnter={(e) => { if (!loading) e.currentTarget.style.background = A7; }}
                        onMouseLeave={(e) => { e.currentTarget.style.background = A; }}
                      >
                        열기
                      </button>
                      {unread && (
                        <button
                          onClick={() => markAlertRead(alert.id)}
                          disabled={loading}
                          style={{
                            padding: "9px 14px",
                            borderRadius: 10,
                            border: `1px solid ${LINE}`,
                            background: WHITE,
                            color: INK2,
                            fontSize: 12,
                            fontWeight: 700,
                            cursor: loading ? "wait" : "pointer",
                            opacity: loading ? 0.75 : 1,
                          }}
                        >
                          읽음
                        </button>
                      )}
                      <button
                        onClick={() => hideAlert(alert.id)}
                        disabled={loading}
                        style={{
                          padding: "9px 14px",
                          borderRadius: 10,
                          border: `1px solid ${LINE}`,
                          background: WHITE,
                          color: INK3,
                          fontSize: 12,
                          fontWeight: 700,
                          cursor: loading ? "wait" : "pointer",
                          opacity: loading ? 0.75 : 1,
                        }}
                      >
                        숨기기
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          ) : (
            <div style={{ textAlign: "center", padding: "36px 0 20px", color: INK3 }}>
              <div style={{ fontSize: 16, fontWeight: 700, color: INK, marginBottom: 6 }}>
                {alerts.length > 0 ? "필터에 맞는 알림이 없어요" : "도착한 알림이 아직 없어요"}
              </div>
              <div style={{ fontSize: 13 }}>
                {alerts.length > 0
                  ? "읽지 않은 알림 필터나 종류 필터를 다시 조정해보세요."
                  : "추천 digest나 마감 임박 알림이 생성되면 이곳에서 다시 확인할 수 있어요."}
              </div>
            </div>
          )}
        </SectionCard>
      </div>
      <FloatingNav />
      <Snackbar
        open={toast.open}
        autoHideDuration={2200}
        onClose={() => setToast((prev) => ({ ...prev, open: false }))}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
      >
        <Alert severity={toast.severity}>{toast.msg}</Alert>
      </Snackbar>
    </div>
  );
}
