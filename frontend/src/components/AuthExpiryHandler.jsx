import { useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { subscribeAuthExpired } from "../lib/authSessionEvents";
import { useAuthStore } from "../store/authStore";
import { buildSafeReturnLocation } from "../lib/safeNavigation";

export default function AuthExpiryHandler({ children }) {
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
        state: {
          from: returnLocation,
          reason: "expired",
        },
      });
    };

    return subscribeAuthExpired(handleAuthExpired);
  }, [clearSession, isLoggedIn, navigate, returnLocation]);

  return children;
}
