import { useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useAuthStore } from "../store/authStore";

export default function AuthExpiryHandler({ children }) {
  const location = useLocation();
  const navigate = useNavigate();
  const { isLoggedIn, clearSession } = useAuthStore();

  useEffect(() => {
    if (!isLoggedIn) {
      if (window.__authExpired) {
        delete window.__authExpired;
      }
      return undefined;
    }

    const handleAuthExpired = () => {
      clearSession();
      navigate("/login", {
        replace: true,
        state: {
          from: location,
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
  }, [clearSession, isLoggedIn, location, navigate]);

  return children;
}
