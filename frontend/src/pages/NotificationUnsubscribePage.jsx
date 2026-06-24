import { useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Alert, Box, Button, CircularProgress, Paper, Typography } from "@mui/material";
import api from "../lib/axios";

const readTokenFromHash = (hash) => {
  if (!hash) return "";
  const normalizedHash = hash.startsWith("#") ? hash.slice(1) : hash;
  const params = new URLSearchParams(normalizedHash);
  return params.get("token")?.trim() ?? "";
};

export default function NotificationUnsubscribePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [status, setStatus] = useState("loading");
  const [message, setMessage] = useState("");
  const token = useMemo(() => {
    const hashToken = readTokenFromHash(window.location.hash);
    const queryToken = searchParams.get("token")?.trim() ?? "";
    return hashToken || queryToken;
  }, [searchParams]);
  const effectiveStatus = token ? status : "error";
  const effectiveMessage = token ? message : "수신 거부 링크가 올바르지 않습니다.";

  useEffect(() => {
    if (!token) {
      return;
    }

    if (searchParams.get("token") || readTokenFromHash(window.location.hash)) {
      window.history.replaceState(window.history.state, document.title, window.location.pathname);
    }

    let mounted = true;
    api.post("/api/notifications/unsubscribe", { token }, { skipAuth: true })
      .then(() => {
        if (!mounted) return;
        setStatus("success");
        setMessage("알림 수신이 해제되었습니다.");
      })
      .catch((error) => {
        if (!mounted) return;
        setStatus("error");
        setMessage(error.response?.data?.message ?? "수신 거부 처리에 실패했습니다.");
      });

    return () => {
      mounted = false;
    };
  }, [searchParams, token]);

  return (
    <Box
      sx={{
        minHeight: "100vh",
        bgcolor: "background.default",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        px: 2,
      }}
    >
      <Paper
        elevation={0}
        sx={{
          width: "100%",
          maxWidth: 440,
          p: { xs: 3, sm: 4 },
          border: "1px solid",
          borderColor: "divider",
          borderRadius: 2,
        }}
      >
        <Typography variant="h5" fontWeight={800} color="text.primary" sx={{ mb: 1 }}>
          알림 수신 거부
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          이메일 알림 설정을 변경하고 있습니다.
        </Typography>

        {effectiveStatus === "loading" ? (
          <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
            <CircularProgress size={22} />
            <Typography variant="body2" color="text.secondary">
              처리 중입니다.
            </Typography>
          </Box>
        ) : (
          <Alert severity={effectiveStatus === "success" ? "success" : "error"} sx={{ mb: 3 }}>
            {effectiveMessage}
          </Alert>
        )}

        <Box sx={{ display: "flex", justifyContent: "flex-end" }}>
          <Button variant="contained" onClick={() => navigate("/")}>
            홈으로
          </Button>
        </Box>
      </Paper>
    </Box>
  );
}
