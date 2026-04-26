import { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  AppBar, Toolbar, Typography, Button, IconButton,
  Menu, MenuItem, Divider, Snackbar, Alert,
} from "@mui/material";
import HomeIcon from "@mui/icons-material/Home";
import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import ForumOutlinedIcon from "@mui/icons-material/ForumOutlined";
import { useAuthStore } from "../store/authStore";
import { performServerLogout } from "../lib/session";

export default function Header() {
  const navigate = useNavigate();
  const { isLoggedIn, user, logout } = useAuthStore();
  const [anchorEl, setAnchorEl] = useState(null);
  const [toast, setToast] = useState(false);

  const handleUserMenu = (e) => setAnchorEl(e.currentTarget);
  const handleClose = () => setAnchorEl(null);

  const handleMypage = () => {
    if (!isLoggedIn) {
      setToast(true);
      setTimeout(() => navigate("/login"), 1500);
    } else {
      navigate("/mypage");
    }
  };

  const handleChat = () => {
    if (!isLoggedIn) {
      setToast(true);
      setTimeout(() => navigate("/login", { state: { from: { pathname: "/chat" }, reason: "login-required" } }), 1500);
      return;
    }
    navigate("/chat");
  };

  const handleLogout = async () => {
    handleClose();
    await performServerLogout(logout);
    navigate("/");
  };

  return (
    <>
      <AppBar position="sticky" elevation={2} sx={{ bgcolor: "primary.main" }}>
        <Toolbar sx={{ justifyContent: "space-between", minHeight: 56, px: { xs: 2, sm: 4 } }}>
          <Typography
            variant="h6"
            fontWeight={700}
            sx={{ cursor: "pointer", display: "flex", alignItems: "center", gap: 0.5 }}
            onClick={() => navigate("/")}
          >
            <HomeIcon fontSize="small" />
            청년복지플랫폼
          </Typography>

          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <Button
              variant={isLoggedIn ? "contained" : "outlined"}
              color={isLoggedIn ? "secondary" : "inherit"}
              size="small"
              startIcon={<ForumOutlinedIcon />}
              onClick={handleChat}
              sx={{
                borderRadius: 2,
                fontSize: 13,
                borderColor: "rgba(255,255,255,0.45)",
                bgcolor: isLoggedIn ? "secondary.main" : "transparent",
                color: "white",
                "&:hover": {
                  bgcolor: isLoggedIn ? "secondary.main" : "rgba(255,255,255,0.08)",
                  borderColor: "rgba(255,255,255,0.7)",
                },
              }}
            >
              챗봇
            </Button>
            {isLoggedIn ? (
              <>
                <Button
                  color="inherit"
                  endIcon={<KeyboardArrowDownIcon />}
                  onClick={handleUserMenu}
                  sx={{
                    bgcolor: "rgba(255,255,255,0.15)",
                    border: "1px solid rgba(255,255,255,0.4)",
                    borderRadius: 2,
                    px: 1.5,
                    fontSize: 13,
                  }}
                >
                  {user?.name}님
                </Button>
                <Menu
                  anchorEl={anchorEl}
                  open={Boolean(anchorEl)}
                  onClose={handleClose}
                  PaperProps={{ sx: { minWidth: 140, mt: 0.5, borderRadius: 2 } }}
                >
                  <MenuItem onClick={() => { handleClose(); navigate("/mypage?tab=0"); }}>
                    회원정보
                  </MenuItem>
                  <MenuItem onClick={() => { handleClose(); navigate("/mypage?tab=5"); }}>
                    비밀번호 변경
                  </MenuItem>
                  <Divider />
                  <MenuItem onClick={handleLogout} sx={{ color: "error.main" }}>
                    로그아웃
                  </MenuItem>
                </Menu>
                <Button
                  variant="outlined"
                  color="inherit"
                  size="small"
                  onClick={() => navigate("/mypage")}
                  sx={{ borderColor: "rgba(255,255,255,0.6)", borderRadius: 2, fontSize: 13 }}
                >
                  마이페이지
                </Button>
              </>
            ) : (
              <>
                <Button
                  variant="contained"
                  size="small"
                  onClick={() => navigate("/login")}
                  sx={{
                    bgcolor: "white",
                    color: "primary.main",
                    fontWeight: 700,
                    fontSize: 13,
                    "&:hover": { bgcolor: "rgba(255,255,255,0.9)" },
                  }}
                >
                  로그인
                </Button>
                <Button
                  variant="outlined"
                  color="inherit"
                  size="small"
                  onClick={handleMypage}
                  sx={{ borderColor: "rgba(255,255,255,0.6)", borderRadius: 2, fontSize: 13 }}
                >
                  마이페이지
                </Button>
              </>
            )}
          </div>
        </Toolbar>
      </AppBar>

      <Snackbar
        open={toast}
        autoHideDuration={2000}
        onClose={() => setToast(false)}
        anchorOrigin={{ vertical: "top", horizontal: "center" }}
      >
        <Alert severity="info" onClose={() => setToast(false)}>
          로그인 후 이용 가능한 기능이에요
        </Alert>
      </Snackbar>
    </>
  );
}
