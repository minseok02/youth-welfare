import { useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Link,
  Paper,
  Snackbar,
  TextField,
  Typography,
} from "@mui/material";
import HomeIcon from "@mui/icons-material/Home";
import api from "../lib/axios";

export default function ResetPasswordPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token") ?? "";
  const hasToken = useMemo(() => token.trim().length > 0, [token]);

  const [email, setEmail] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [toast, setToast] = useState({ open: false, msg: "", severity: "info" });

  const showToast = (msg, severity = "info") => {
    setToast({ open: true, msg, severity });
  };

  const handleRequest = async (event) => {
    event.preventDefault();
    if (!email.trim()) {
      return;
    }

    setLoading(true);
    setError("");
    try {
      await api.post("/api/auth/password-reset/request", { email: email.trim() });
      showToast("입력한 이메일로 재설정 안내 메일을 보냈습니다. 메일함을 확인해주세요.");
      setEmail("");
    } catch (requestError) {
      setError(requestError.response?.data?.message ?? "재설정 메일 요청에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const handleConfirm = async (event) => {
    event.preventDefault();
    if (!newPassword || !confirmPassword) {
      return;
    }
    if (newPassword !== confirmPassword) {
      setError("새 비밀번호 확인이 일치하지 않습니다.");
      return;
    }

    setLoading(true);
    setError("");
    try {
      await api.post("/api/auth/password-reset/confirm", {
        token,
        newPassword,
      });
      showToast("비밀번호가 변경되었습니다. 새 비밀번호로 다시 로그인해주세요.", "success");
      setTimeout(() => navigate("/login"), 1200);
    } catch (confirmError) {
      const code = confirmError.response?.data?.errorCode;
      if (code === "A008") {
        setError("링크가 만료되었거나 이미 사용되었습니다. 다시 재설정 메일을 요청해주세요.");
      } else {
        setError(confirmError.response?.data?.message ?? "비밀번호 재설정에 실패했습니다.");
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box sx={{ minHeight: "100vh", bgcolor: "background.default", display: "flex", flexDirection: "column" }}>
      <Box
        sx={{ bgcolor: "primary.main", px: 4, py: 1.5, display: "flex", alignItems: "center", cursor: "pointer" }}
        onClick={() => navigate("/")}
      >
        <HomeIcon sx={{ color: "white", mr: 0.5 }} />
        <Typography variant="h6" fontWeight={700} color="white">청년복지플랫폼</Typography>
      </Box>

      <Box sx={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", p: 2 }}>
        <Paper
          elevation={0}
          sx={{ width: "100%", maxWidth: 460, p: { xs: 3, sm: 5 }, borderRadius: 3, border: "1px solid #E8F4F5" }}
        >
          <Typography variant="h5" fontWeight={700} mb={1}>
            {hasToken ? "새 비밀번호 설정" : "비밀번호 재설정 메일 요청"}
          </Typography>
          <Typography variant="body2" color="text.secondary" mb={3}>
            {hasToken
              ? "메일로 받은 링크의 토큰을 확인한 뒤 새 비밀번호를 저장합니다."
              : "가입한 이메일을 입력하면 비밀번호 재설정 링크를 보내드립니다."}
          </Typography>

          <Box
            component="form"
            onSubmit={hasToken ? handleConfirm : handleRequest}
            sx={{ display: "flex", flexDirection: "column", gap: 2 }}
          >
            {!hasToken ? (
              <TextField
                label="가입한 이메일"
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                placeholder="example@email.com"
                error={!!error}
                helperText={error}
                fullWidth
              />
            ) : (
              <>
                <TextField
                  label="새 비밀번호"
                  type="password"
                  value={newPassword}
                  onChange={(event) => setNewPassword(event.target.value)}
                  placeholder="8자 이상 입력"
                  error={!!error}
                  helperText={error}
                  fullWidth
                />
                <TextField
                  label="새 비밀번호 확인"
                  type="password"
                  value={confirmPassword}
                  onChange={(event) => setConfirmPassword(event.target.value)}
                  placeholder="비밀번호를 다시 입력"
                  fullWidth
                />
              </>
            )}

            <Button
              type="submit"
              variant="contained"
              disabled={loading || (!hasToken && !email.trim()) || (hasToken && (!newPassword || !confirmPassword))}
              sx={{ py: 1.4, fontWeight: 700 }}
            >
              {loading ? <CircularProgress size={22} color="inherit" /> : (hasToken ? "비밀번호 변경" : "재설정 메일 보내기")}
            </Button>
          </Box>

          <Box sx={{ display: "flex", justifyContent: "center", gap: 2, mt: 2.5 }}>
            <Link
              component="button"
              variant="body2"
              onClick={() => navigate("/login")}
              underline="hover"
              color="primary"
              fontWeight={600}
            >
              로그인으로 돌아가기
            </Link>
          </Box>
        </Paper>
      </Box>

      <Snackbar
        open={toast.open}
        autoHideDuration={2500}
        onClose={() => setToast((prev) => ({ ...prev, open: false }))}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
      >
        <Alert severity={toast.severity} onClose={() => setToast((prev) => ({ ...prev, open: false }))}>
          {toast.msg}
        </Alert>
      </Snackbar>
    </Box>
  );
}
