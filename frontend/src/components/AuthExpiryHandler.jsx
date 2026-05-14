import { useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useAuthStore } from "../store/authStore";

export default function AuthExpiryHandler({ children }) {
  const location = useLocation();
  const navigate = useNavigate();
  const { isLoggedIn, logout } = useAuthStore();

  useEffect(() => {
    if (!isLoggedIn) {
      if (window.__authExpired) {
        delete window.__authExpired;
      }
      return undefined;
    }

    const handleAuthExpired = () => {
      logout();
      navigate("/login", {
        replace: true,
        state: {
          from: {
            pathname: location.pathname,
            search: location.search,
          },
          reason: "expired",
        },
      });
    };

    window.__authExpired = handleAuthExpired;

    return () => {
      if (window.__authExpired === handleAuthExpired) {
        delete window.__authExpired;
      }
    };
  }, [isLoggedIn, location.pathname, location.search, logout, navigate]);

  return children;
}
