import { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Box, Paper, Typography, TextField, Button,
  Link, Snackbar, Alert, CircularProgress,
} from "@mui/material";
import HomeIcon from "@mui/icons-material/Home";
import { useAuthStore } from "../store/authStore";
import api from "../lib/axios";

export default function LoginPage() {
  const navigate = useNavigate();
  const { login } = useAuthStore();

  const [email, setEmail] = useState("");
  const [pw, setPw] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [toast, setToast] = useState({ open: false, msg: "" });

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!email || !pw) return;

    setLoading(true);
    setError("");

    try {
      const { data } = await api.post("/api/auth/login", { email, password: pw });
      const accessToken = data?.data?.accessToken;
      if (!accessToken) {
        throw new Error("로그인 응답에 accessToken이 없습니다");
      }

      login(accessToken, { email });

      try {
        const profileResponse = await api.get("/api/users/me");
        const profile = profileResponse?.data?.data;
        login(accessToken, {
          name: profile?.name ?? "",
          email: profile?.email ?? email,
        });
      } catch {
        login(accessToken, { email });
      }

      navigate("/");
    } catch (err) {
      setError(err.response?.data?.message ?? "로그인 중 오류가 발생했습니다");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box sx={{ minHeight: "100vh", bgcolor: "background.default", display: "flex", flexDirection: "column" }}>
      {/* 헤더 */}
      <Box
        sx={{ bgcolor: "primary.main", px: 4, py: 1.5, display: "flex", alignItems: "center", cursor: "pointer" }}
        onClick={() => navigate("/")}
      >
        <HomeIcon sx={{ color: "white", mr: 0.5 }} />
        <Typography variant="h6" fontWeight={700} color="white">청년복지플랫폼</Typography>
      </Box>

      {/* 로그인 카드 */}
      <Box sx={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", p: 2 }}>
        <Paper elevation={0} sx={{ width: "100%", maxWidth: 420, p: { xs: 3, sm: 5 }, borderRadius: 3, border: "1px solid #E8F4F5" }}>
          <Box sx={{ textAlign: "center", mb: 4 }}>
            <Box sx={{
              width: 52, height: 52, borderRadius: "50%", bgcolor: "primary.main",
              mx: "auto", mb: 1.5, display: "flex", alignItems: "center", justifyContent: "center",
            }}>
              <HomeIcon sx={{ color: "white", fontSize: 26 }} />
            </Box>
            <Typography variant="h5" fontWeight={700} mb={0.5}>로그인</Typography>
            <Typography variant="body2" color="text.secondary">
              나에게 맞는 청년 복지 정책을 찾아보세요
            </Typography>
          </Box>

          <Box component="form" onSubmit={handleSubmit} sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
            <TextField
              label="이메일"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="example@email.com"
              fullWidth
              size="medium"
            />
            <TextField
              label="비밀번호"
              type="password"
              value={pw}
              onChange={(e) => setPw(e.target.value)}
              placeholder="비밀번호를 입력하세요"
              fullWidth
              size="medium"
              error={!!error}
              helperText={error}
            />

            <Button
              type="submit"
              variant="contained"
              fullWidth
              size="large"
              disabled={!email || !pw || loading}
              sx={{ mt: 1, py: 1.5, fontWeight: 700 }}
            >
              {loading ? <CircularProgress size={22} color="inherit" /> : "로그인"}
            </Button>
          </Box>

          <Box sx={{ display: "flex", justifyContent: "center", gap: 2, mt: 2.5 }}>
            <Link
              component="button"
              variant="body2"
              onClick={() => navigate("/signup")}
              underline="hover"
              color="primary"
              fontWeight={600}
            >
              회원가입
            </Link>
            <Typography variant="body2" color="text.disabled">|</Typography>
            <Link
              component="button"
              variant="body2"
              color="text.secondary"
              underline="hover"
              onClick={() => setToast({ open: true, msg: "아이디는 가입한 이메일입니다. 비밀번호 재설정 메일 기능은 아직 준비 중입니다." })}
            >
              아이디/비밀번호 찾기
            </Link>
          </Box>
        </Paper>
      </Box>

      <Snackbar
        open={toast.open}
        autoHideDuration={2000}
        onClose={() => setToast({ ...toast, open: false })}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
      >
        <Alert severity="info">{toast.msg}</Alert>
      </Snackbar>
    </Box>
  );
}
