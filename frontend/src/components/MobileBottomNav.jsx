import { useLocation, useNavigate } from "react-router-dom";
import { Badge, BottomNavigation, BottomNavigationAction, Paper } from "@mui/material";
import HomeIcon from "@mui/icons-material/Home";
import SearchIcon from "@mui/icons-material/Search";
import PersonIcon from "@mui/icons-material/Person";
import ForumOutlinedIcon from "@mui/icons-material/ForumOutlined";
import HelpOutlineIcon from "@mui/icons-material/HelpOutline";
import { useAuthStore } from "../store/authStore";
import { useUnreadAlertCount } from "../lib/useUnreadAlertCount";

const NAV_ITEMS = [
  { label: "맞춤정책", path: "/", icon: HomeIcon, authRequired: false },
  { label: "정책검색", path: "/policies", icon: SearchIcon, authRequired: false },
  { label: "가이드", path: "/guide", icon: HelpOutlineIcon, authRequired: false },
  { label: "마이페이지", path: "/mypage", icon: PersonIcon, authRequired: true },
  { label: "AI 챗봇", path: "/chat", icon: ForumOutlinedIcon, authRequired: true },
];

function getCurrentValue(pathname) {
  if (pathname === "/") return "/";
  if (pathname.startsWith("/policies")) return "/policies";
  if (pathname.startsWith("/guide")) return "/guide";
  if (pathname.startsWith("/mypage")) return "/mypage";
  if (pathname.startsWith("/chat")) return "/chat";
  return false;
}

export default function MobileBottomNav() {
  const location = useLocation();
  const navigate = useNavigate();
  const { isLoggedIn } = useAuthStore();
  const unreadAlertCount = useUnreadAlertCount(isLoggedIn);
  const unreadBadge = unreadAlertCount > 99 ? "99+" : unreadAlertCount;

  const mypageTarget =
    location.pathname === "/mypage"
      ? { pathname: "/mypage", search: location.search, state: location.state }
      : {
          pathname: "/mypage",
          search:
            location.state?.from?.pathname === "/mypage"
              ? (location.state.from.search ?? "")
              : "",
          state: {
            ...(location.state?.from?.pathname === "/mypage"
              ? (location.state.from.state ?? {})
              : {}),
            from: location,
          },
        };

  const policiesTarget =
    location.pathname === "/policies"
      ? { pathname: "/policies", search: location.search, state: location.state }
      : location.state?.from?.pathname === "/policies"
        ? {
            pathname: "/policies",
            search: location.state.from.search ?? "",
            state: location.state.from.state,
          }
        : { pathname: "/policies", search: "" };

  const chatOriginTarget =
    location.state?.chatFrom?.pathname === "/chat"
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

  const chatTarget =
    location.pathname === "/chat"
      ? { pathname: "/chat", search: location.search, state: location.state }
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
    const target =
      item.path === "/chat"
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
    navigate(`${target.pathname}${target.search ?? ""}`, { state: target.state });
  };

  return (
    <Paper
      sx={{
        position: "fixed",
        bottom: 0,
        left: 0,
        right: 0,
        zIndex: 1200,
        display: { xs: "block", lg: "none" },
        borderTop: "1px solid rgba(0,0,0,0.08)",
      }}
      elevation={3}
    >
      <BottomNavigation
        value={getCurrentValue(location.pathname)}
        showLabels
        sx={{ height: 56 }}
      >
        {NAV_ITEMS.map((item) => {
          const Icon = item.icon;
          return (
            <BottomNavigationAction
              key={item.path}
              label={item.label}
              value={item.path}
              icon={
                <Badge
                  color="error"
                  badgeContent={item.path === "/mypage" ? unreadBadge : 0}
                  invisible={
                    item.path !== "/mypage" || !isLoggedIn || unreadAlertCount <= 0
                  }
                >
                  <Icon />
                </Badge>
              }
              onClick={() => handleNavigate(item)}
              sx={{ minWidth: 0, "&.Mui-selected": { color: "primary.main" } }}
            />
          );
        })}
      </BottomNavigation>
    </Paper>
  );
}
