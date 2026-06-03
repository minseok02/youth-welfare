import { useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import {
  AppBar, Toolbar, Typography, Button, IconButton, Box,
  Menu, MenuItem, Badge, CircularProgress,
} from "@mui/material";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import HomeIcon from "@mui/icons-material/Home";
import NotificationsNoneOutlinedIcon from "@mui/icons-material/NotificationsNoneOutlined";
import AdminPanelSettingsOutlinedIcon from "@mui/icons-material/AdminPanelSettingsOutlined";
import { useAuthStore } from "../store/authStore";
import { useUnreadAlertCount } from "../lib/useUnreadAlertCount";
import api from "../lib/axios";

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

export default function Header() {
  const location = useLocation();
  const navigate = useNavigate();
  const { isLoggedIn, user } = useAuthStore();
  const queryClient = useQueryClient();
  const unreadAlertCount = useUnreadAlertCount(isLoggedIn);
  const [alertAnchorEl, setAlertAnchorEl] = useState(null);

  const handleAlertMenu = (e) => setAlertAnchorEl(e.currentTarget);
  const handleAlertClose = () => setAlertAnchorEl(null);
  const authFromState = {
    from: location,
  };
  const unreadBadge = unreadAlertCount > 99 ? "99+" : unreadAlertCount;
  const isAdmin = Boolean(user?.isAdmin);
  const alertMenuOpen = Boolean(alertAnchorEl);
  const mypageTarget = location.pathname === "/mypage"
    ? {
        pathname: "/mypage",
        search: location.search,
        state: location.state,
      }
    : {
        pathname: "/mypage",
        search: location.state?.from?.pathname === "/mypage" ? (location.state.from.search ?? "") : "",
        state: {
          ...(location.state?.from?.pathname === "/mypage" ? (location.state.from.state ?? {}) : {}),
          from: location,
        },
      };
  const alertInboxTarget = useMemo(() => ({
    pathname: "/alerts",
    search: "",
    state: {
      ...(location.state ?? {}),
      from: location,
    },
  }), [location]);

  const {
    data: headerAlerts = [],
    isFetching: alertMenuLoading,
  } = useQuery({
    queryKey: ["header-alerts", isLoggedIn],
    enabled: isLoggedIn && alertMenuOpen,
    queryFn: async () => {
      const { data } = await api.get("/api/notifications/me");
      return Array.isArray(data?.data) ? data.data.slice(0, 5) : [];
    },
    staleTime: 30_000,
  });

  const openAlert = async (alert) => {
    handleAlertClose();
    try {
      if (alert?.status === "UNREAD") {
        await api.patch(`/api/notifications/${alert.id}/read`);
        await Promise.all([
          queryClient.invalidateQueries({ queryKey: ["notification-unread-count", true] }),
          queryClient.invalidateQueries({ queryKey: ["header-alerts", true] }),
        ]);
      }
    } catch {
      // Keep navigation best-effort even if read-sync fails.
    }

    const target = alert?.deeplinkUrl
      ? { pathname: alert.deeplinkUrl, state: { from: location } }
      : alertInboxTarget;
    navigate(`${target.pathname}${target.search ?? ""}`, { state: target.state });
  };

  return (
    <>
      <AppBar position="sticky" elevation={2} sx={{ bgcolor: "primary.main" }}>
        <Toolbar
          sx={{
            justifyContent: "space-between",
            alignItems: "center",
            minHeight: 56,
            px: { xs: 1.5, sm: 4 },
            gap: { xs: 1, sm: 1.5 },
          }}
        >
          <Typography
            variant="h6"
            fontWeight={700}
            sx={{
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              gap: 0.5,
              flexShrink: 0,
              whiteSpace: "nowrap",
              overflow: "hidden",
              textOverflow: "ellipsis",
              fontSize: { xs: 17, sm: 20 },
              lineHeight: 1.2,
            }}
            onClick={() => navigate("/")}
          >
            <HomeIcon fontSize="small" />
            청년복지플랫폼
          </Typography>

          <Box
            sx={{
              display: "flex",
              alignItems: "center",
              justifyContent: "flex-end",
              gap: 0.75,
            }}
          >
            {isLoggedIn ? (
              <>
                <IconButton
                  color="inherit"
                  size="small"
                  onClick={handleAlertMenu}
                  sx={{
                    border: "1px solid rgba(255,255,255,0.35)",
                    bgcolor: "rgba(255,255,255,0.08)",
                    "&:hover": { bgcolor: "rgba(255,255,255,0.15)" },
                  }}
                >
                  <Badge color="error" badgeContent={unreadBadge} invisible={unreadAlertCount <= 0}>
                    <NotificationsNoneOutlinedIcon fontSize="small" />
                  </Badge>
                </IconButton>
                <Menu
                  anchorEl={alertAnchorEl}
                  open={alertMenuOpen}
                  onClose={handleAlertClose}
                  PaperProps={{ sx: { width: 340, maxWidth: "calc(100vw - 24px)", mt: 0.75, borderRadius: 2.5, overflow: "hidden" } }}
                >
                  <div style={{ padding: "14px 16px 10px", background: "#f8fbff", borderBottom: "1px solid #e5e7eb" }}>
                    <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12 }}>
                      <div>
                        <div style={{ fontSize: 14, fontWeight: 800, color: "#11131a" }}>알림함</div>
                        <div style={{ fontSize: 12, color: "#6b7280", marginTop: 2 }}>
                          읽지 않은 알림 {unreadAlertCount}개
                        </div>
                      </div>
                      <Button
                        size="small"
                        onClick={() => {
                          handleAlertClose();
                          navigate(`${alertInboxTarget.pathname}${alertInboxTarget.search}`, {
                            state: alertInboxTarget.state,
                          });
                        }}
                        sx={{ fontSize: 12, fontWeight: 700, minWidth: "auto" }}
                      >
                        전체 보기
                      </Button>
                    </div>
                  </div>
                  {alertMenuLoading ? (
                    <div style={{ display: "flex", justifyContent: "center", padding: "20px 0" }}>
                      <CircularProgress size={22} />
                    </div>
                  ) : headerAlerts.length > 0 ? (
                    headerAlerts.map((alert, index) => (
                      <MenuItem
                        key={alert.id}
                        onClick={() => openAlert(alert)}
                        sx={{
                          alignItems: "flex-start",
                          whiteSpace: "normal",
                          py: 1.4,
                          px: 2,
                          borderBottom: index < headerAlerts.length - 1 ? "1px solid #f3f4f6" : "none",
                          bgcolor: alert.status === "UNREAD" ? "rgba(37,99,235,0.04)" : "white",
                        }}
                      >
                        <div style={{ width: "100%" }}>
                          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12, marginBottom: 6 }}>
                            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                              <span style={{
                                fontSize: 11,
                                fontWeight: 800,
                                padding: "3px 8px",
                                borderRadius: 999,
                                background: alert.status === "UNREAD" ? "#dbeafe" : "#f3f4f6",
                                color: alert.status === "UNREAD" ? "#1e3a8a" : "#4a4f5c",
                              }}>
                                {alert.kind === "RECOMMENDATION_DIGEST" ? "추천 알림" : alert.kind}
                              </span>
                              {alert.status === "UNREAD" && (
                                <span style={{ fontSize: 11, fontWeight: 800, color: "#ef4444" }}>NEW</span>
                              )}
                            </div>
                            <span style={{ fontSize: 11, color: "#6b7280", flexShrink: 0 }}>
                              {formatAlertTime(alert.createdAt)}
                            </span>
                          </div>
                          <div style={{ fontSize: 13, fontWeight: 800, color: "#11131a", lineHeight: 1.45, marginBottom: alert.body ? 4 : 0 }}>
                            {alert.title}
                          </div>
                          {alert.body && (
                            <div style={{ fontSize: 12, color: "#4a4f5c", lineHeight: 1.55 }}>
                              {alert.body}
                            </div>
                          )}
                        </div>
                      </MenuItem>
                    ))
                  ) : (
                    <div style={{ padding: "18px 16px", fontSize: 13, color: "#6b7280" }}>
                      도착한 알림이 아직 없어요
                    </div>
                  )}
                </Menu>
                {isAdmin && (
                  <Button
                    color="inherit"
                    size="small"
                    startIcon={<AdminPanelSettingsOutlinedIcon fontSize="small" />}
                    onClick={() => navigate("/admin/dashboard")}
                    sx={{
                      display: { xs: "none", lg: "inline-flex" },
                      fontSize: { xs: 12, sm: 13 },
                      color: "white",
                      bgcolor: "rgba(255,255,255,0.12)",
                      border: "1px solid rgba(255,255,255,0.2)",
                      minWidth: "auto",
                      px: { xs: 1, sm: 1.25 },
                      "&:hover": { bgcolor: "rgba(255,255,255,0.18)" },
                    }}
                  >
                    <Box component="span" sx={{ display: { xs: "none", sm: "inline" } }}>
                      운영 대시보드
                    </Box>
                    <Box component="span" sx={{ display: { xs: "inline", sm: "none" } }}>
                      운영
                    </Box>
                  </Button>
                )}
                <Button
                  color="inherit"
                  size="small"
                  onClick={() => navigate("/guide")}
                  sx={{
                    display: "inline-flex",
                    fontSize: { xs: 12, sm: 13 },
                    color: "white",
                    bgcolor: "rgba(255,255,255,0.08)",
                    border: "1px solid rgba(255,255,255,0.18)",
                    minWidth: "auto",
                    px: { xs: 1, sm: 1.25 },
                    "&:hover": { bgcolor: "rgba(255,255,255,0.14)" },
                  }}
                >
                  <Box component="span" sx={{ display: { xs: "inline", sm: "none" } }}>
                    가이드
                  </Box>
                  <Box component="span" sx={{ display: { xs: "none", sm: "inline" } }}>
                    이용가이드
                  </Box>
                </Button>
                <Button
                  variant="outlined"
                  color="inherit"
                  size="small"
                  onClick={() => navigate(`${mypageTarget.pathname}${mypageTarget.search ?? ""}`, {
                    state: mypageTarget.state,
                  })}
                  sx={{
                    display: { xs: "none", lg: "inline-flex" },
                    borderColor: "rgba(255,255,255,0.6)",
                    borderRadius: 2,
                    fontSize: { xs: 12, sm: 13 },
                    minWidth: "auto",
                    px: { xs: 1, sm: 1.25 },
                  }}
                >
                  <Badge color="error" badgeContent={unreadBadge} invisible={unreadAlertCount <= 0} overlap="rectangular">
                    <span>마이페이지</span>
                  </Badge>
                </Button>
              </>
            ) : (
              <>
                <Button
                  color="inherit"
                  size="small"
                  onClick={() => navigate("/guide")}
                  sx={{
                    display: "inline-flex",
                    fontSize: { xs: 12, sm: 13 },
                    color: "white",
                    bgcolor: "rgba(255,255,255,0.08)",
                    border: "1px solid rgba(255,255,255,0.18)",
                    minWidth: "auto",
                    px: { xs: 1, sm: 1.25 },
                    "&:hover": { bgcolor: "rgba(255,255,255,0.14)" },
                  }}
                >
                  <Box component="span" sx={{ display: { xs: "inline", sm: "none" } }}>
                    가이드
                  </Box>
                  <Box component="span" sx={{ display: { xs: "none", sm: "inline" } }}>
                    이용가이드
                  </Box>
                </Button>
                <Button
                  variant="contained"
                  size="small"
                  onClick={() => navigate("/login", { state: authFromState })}
                  sx={{
                    bgcolor: "white",
                    color: "primary.main",
                    fontWeight: 700,
                    fontSize: { xs: 12, sm: 13 },
                    minWidth: "auto",
                    px: { xs: 1.1, sm: 1.5 },
                    "&:hover": { bgcolor: "rgba(255,255,255,0.9)" },
                  }}
                >
                  로그인
                </Button>
              </>
            )}
          </Box>
        </Toolbar>
      </AppBar>

    </>
  );
}
