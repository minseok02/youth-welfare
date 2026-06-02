import { useNetworkStore } from "../store/networkStore";
import api from "../lib/axios";
import { useState } from "react";

export default function ServerErrorBanner() {
  const serverDown = useNetworkStore((s) => s.serverDown);
  const setServerDown = useNetworkStore((s) => s.setServerDown);
  const [retrying, setRetrying] = useState(false);

  if (!serverDown) return null;

  const handleRetry = async () => {
    setRetrying(true);
    try {
      await api.get("/api/policies?size=1");
      setServerDown(false);
    } catch {
      // 여전히 실패면 배너 유지
    } finally {
      setRetrying(false);
    }
  };

  return (
    <div style={{
      position: "fixed", top: 0, left: 0, right: 0, zIndex: 9999,
      background: "#1e293b", color: "white",
      padding: "12px 20px",
      display: "flex", alignItems: "center", justifyContent: "center", gap: 16,
      fontSize: 14, fontWeight: 500,
      boxShadow: "0 2px 12px rgba(0,0,0,0.3)",
    }}>
      <span style={{ display: "flex", alignItems: "center", gap: 8 }}>
        <span style={{ width: 8, height: 8, borderRadius: "50%", background: "#ef4444", flexShrink: 0, display: "inline-block" }} />
        서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.
      </span>
      <button
        onClick={handleRetry}
        disabled={retrying}
        style={{
          padding: "6px 14px", background: "white", color: "#1e293b",
          border: 0, borderRadius: 8, fontSize: 13, fontWeight: 700,
          cursor: retrying ? "not-allowed" : "pointer", opacity: retrying ? 0.7 : 1,
          flexShrink: 0,
        }}
      >
        {retrying ? "연결 중..." : "다시 시도"}
      </button>
    </div>
  );
}
