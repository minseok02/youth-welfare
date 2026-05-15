import { useLocation, useNavigate } from "react-router-dom";
import { Badge, Box, Paper, Tooltip, Typography } from "@mui/material";
import HomeIcon from "@mui/icons-material/Home";
import SearchIcon from "@mui/icons-material/Search";
import PersonIcon from "@mui/icons-material/Person";
import ForumOutlinedIcon from "@mui/icons-material/ForumOutlined";
import { useAuthStore } from "../store/authStore";
import { useUnreadAlertCount } from "../lib/useUnreadAlertCount";

const NAV_ITEMS = [
  { label: "맞춤정책", path: "/", icon: HomeIcon, authRequired: false },
  { label: "정책검색", path: "/policies", icon: SearchIcon, authRequired: false },
  { label: "마이페이지", path: "/mypage", icon: PersonIcon, authRequired: true },
  { label: "AI 챗봇", path: "/chat", icon: ForumOutlinedIcon, authRequired: true },
];

const isActive = (path, location) => {
  if (path === "/") return location.pathname === "/";
  return location.pathname.startsWith(path);
};

export default function FloatingNav() {
  const location = useLocation();
  const navigate = useNavigate();
  const { isLoggedIn } = useAuthStore();
  const unreadAlertCount = useUnreadAlertCount(isLoggedIn);
  const unreadBadge = unreadAlertCount > 99 ? "99+" : unreadAlertCount;
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
  const policiesTarget = location.pathname === "/policies"
    ? {
        pathname: "/policies",
        search: location.search,
        state: location.state,
      }
    : location.state?.from?.pathname === "/policies"
      ? {
          pathname: "/policies",
          search: location.state.from.search ?? "",
          state: location.state.from.state,
        }
      : {
          pathname: "/policies",
          search: "",
        };
  const chatOriginTarget = location.state?.chatFrom?.pathname === "/chat"
    ? {
        pathname: "/chat",
        search: location.state.chatFrom.search ?? "",
        state: location.state.chatFrom.state,
      }
    : location.state?.from?.pathname === "/chat"
      ? {
          pathname: "/chat",
          search: location.state.from.search ?? "",
          state: location.state.from.state,
        }
      : null;
  const chatTarget = location.pathname === "/chat"
    ? {
        pathname: "/chat",
        search: location.search,
        state: location.state,
      }
    : {
        pathname: "/chat",
        search: chatOriginTarget?.search ?? "",
        state: {
          ...(chatOriginTarget?.state ?? {}),
          from: location,
          chatFrom: {
            pathname: "/chat",
            search: chatOriginTarget?.search ?? "",
            state: chatOriginTarget?.state,
          },
        },
      };

  const handleNavigate = (item) => {
    const target = item.path === "/chat"
      ? chatTarget
      : item.path === "/mypage"
        ? mypageTarget
        : item.path === "/policies"
          ? policiesTarget
          : { pathname: item.path, search: "" };
    if (item.authRequired && !isLoggedIn) {
      navigate("/login", { state: { from: target, reason: "login-required" } });
      return;
    }
    navigate(`${target.pathname}${target.search ?? ""}`, {
      state: target.state,
    });
  };

  const isChat = location.pathname.startsWith("/chat");
  const rightPos = isChat
    ? "max(8px, calc((100vw - 1536px) / 2 - 80px))"
    : "max(8px, calc((100vw - 1200px) / 2 - 110px))";

  return (
    <Box
      sx={{
        position: "fixed",
        top: "50%",
        right: rightPos,
        transform: "translateY(-50%)",
        zIndex: 1200,
        display: { xs: "none", lg: "flex" },
        flexDirection: "column",
        gap: 0,
      }}
    >
      <Paper
        elevation={3}
        sx={{
          borderRadius: 4,
          overflow: "hidden",
          boxShadow: "0 4px 20px rgba(2,128,144,0.15)",
        }}
      >
        {NAV_ITEMS.map((item, idx) => {
          const active = isActive(item.path, location);
          const Icon = item.icon;
          return (
            <Tooltip key={item.path} title={item.label} placement="left" arrow>
              <Box
                onClick={() => handleNavigate(item)}
                sx={{
                  display: "flex",
                  flexDirection: "column",
                  alignItems: "center",
                  justifyContent: "center",
                  gap: 0.5,
                  px: 1.5,
                  py: 1.4,
                  cursor: "pointer",
                  bgcolor: active ? "primary.main" : "white",
                  color: active ? "white" : "text.secondary",
                  borderBottom: idx < NAV_ITEMS.length - 1 ? "1px solid rgba(0,0,0,0.06)" : "none",
                  transition: "all 0.18s ease",
                  "&:hover": {
                    bgcolor: active ? "primary.dark" : "rgba(2,128,144,0.07)",
                    color: active ? "white" : "primary.main",
                  },
                  minWidth: 64,
                }}
              >
                <Badge
                  color="error"
                  badgeContent={item.path === "/mypage" ? unreadBadge : 0}
                  invisible={item.path !== "/mypage" || !isLoggedIn || unreadAlertCount <= 0}
                >
                  <Icon fontSize="small" />
                </Badge>
                <Typography
                  variant="caption"
                  fontWeight={active ? 700 : 400}
                  sx={{ fontSize: 10, lineHeight: 1.2, textAlign: "center", whiteSpace: "nowrap" }}
                >
                  {item.label}
                </Typography>
                {active && (
                  <Box
                    sx={{
                      position: "absolute",
                      left: 0,
                      width: 3,
                      height: 36,
                      borderRadius: "0 2px 2px 0",
                      bgcolor: "white",
                      opacity: 0.7,
                    }}
                  />
                )}
              </Box>
            </Tooltip>
          );
        })}
      </Paper>
    </Box>
  );
}
