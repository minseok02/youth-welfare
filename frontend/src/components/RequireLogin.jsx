import { useEffect } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { useAuthStore } from "../store/authStore";
import { buildSafeReturnLocation } from "../lib/safeNavigation";

export default function RequireLogin({ children }) {
  const location = useLocation();
  const navigate = useNavigate();
  const { isLoggedIn, clearSession } = useAuthStore();
  const returnLocation = buildSafeReturnLocation(location);

  useEffect(() => {
    if (!isLoggedIn) {
      return undefined;
    }

    const handleAuthExpired = () => {
      clearSession();
      navigate("/login", {
        replace: true,
        state: { from: returnLocation, reason: "expired" },
      });
    };

    window.__authExpired = handleAuthExpired;

    return () => {
      if (window.__authExpired === handleAuthExpired) {
        delete window.__authExpired;
      }
    };
  }, [clearSession, isLoggedIn, navigate, returnLocation]);

  if (!isLoggedIn) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: returnLocation, reason: "login-required" }}
      />
    );
  }

  return children;
}
