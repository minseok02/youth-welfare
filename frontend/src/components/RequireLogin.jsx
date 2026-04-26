import { useEffect } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { useAuthStore } from "../store/authStore";

export default function RequireLogin({ children }) {
  const location = useLocation();
  const navigate = useNavigate();
  const { isLoggedIn, logout } = useAuthStore();

  useEffect(() => {
    if (!isLoggedIn) {
      return undefined;
    }

    const handleAuthExpired = () => {
      logout();
      navigate("/login", {
        replace: true,
        state: { from: location, reason: "expired" },
      });
    };

    window.__authExpired = handleAuthExpired;

    return () => {
      if (window.__authExpired === handleAuthExpired) {
        delete window.__authExpired;
      }
    };
  }, [isLoggedIn, location, logout, navigate]);

  if (!isLoggedIn) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: location, reason: "login-required" }}
      />
    );
  }

  return children;
}
