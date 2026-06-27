import { Navigate, useLocation } from "react-router-dom";
import { useAuthStore } from "../store/authStore";
import { buildSafeReturnLocation } from "../lib/safeNavigation";

export default function RequireLogin({ children }) {
  const location = useLocation();
  const { isLoggedIn } = useAuthStore();
  const returnLocation = buildSafeReturnLocation(location);

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
