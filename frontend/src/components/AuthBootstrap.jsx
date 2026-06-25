import { useEffect, useState } from "react";
import api from "../lib/axios";
import { useAuthStore } from "../store/authStore";

const BG = "#f7f8fc";
const A = "#2563eb";
const AS = "#e8efff";
const INK3 = "#6b7280";

function FullScreenSpinner() {
  return (
    <div
      style={{
        minHeight: "100vh",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: 14,
        background: BG,
      }}
    >
      <div
        style={{
          width: 36,
          height: 36,
          borderRadius: "50%",
          border: `3px solid ${AS}`,
          borderTopColor: A,
          animation: "spin 0.8s linear infinite",
        }}
      />
      <div style={{ fontSize: 13, color: INK3 }}>로그인 상태 확인 중...</div>
      <style>{`@keyframes spin{to{transform:rotate(360deg)}}`}</style>
    </div>
  );
}

export default function AuthBootstrap({ children }) {
  const {
    authReady,
    login,
    clearSession,
    setUser,
    markProfileHydrationStarted,
    markProfileHydrationSucceeded,
    markProfileHydrationFailed,
  } = useAuthStore();
  const [bootstrapping, setBootstrapping] = useState(!authReady);

  useEffect(() => {
    if (authReady) {
      setBootstrapping(false);
      return;
    }

    let cancelled = false;

    const bootstrap = async () => {
      try {
        const { data } = await api.post("/api/auth/refresh");
        const accessToken = data?.data?.accessToken;

        if (!accessToken) {
          throw new Error("refresh token rotation response missing accessToken");
        }

        login(accessToken);
        markProfileHydrationStarted();

        try {
          const profileResponse = await api.get("/api/users/me");
          const profile = profileResponse?.data?.data;
          if (!profile) {
            throw new Error("profile hydration response missing data");
          }
          if (!cancelled) {
            setUser({
              ...(profile.name ? { name: profile.name } : {}),
              ...(profile.email ? { email: profile.email } : {}),
              hasPriorities: Array.isArray(profile.priorities) && profile.priorities.length > 0,
            });
            markProfileHydrationSucceeded();
          }
        } catch (profileError) {
          if (!cancelled) {
            markProfileHydrationFailed(profileError);
          }
        }
      } catch {
        if (!cancelled) {
          clearSession();
        }
      } finally {
        if (!cancelled) {
          setBootstrapping(false);
        }
      }
    };

    void bootstrap();

    return () => {
      cancelled = true;
    };
  }, [
    authReady,
    clearSession,
    login,
    markProfileHydrationFailed,
    markProfileHydrationStarted,
    markProfileHydrationSucceeded,
    setUser,
  ]);

  if (!authReady || bootstrapping) {
    return <FullScreenSpinner />;
  }

  return children;
}
