import { Navigate, useLocation } from "react-router-dom";
import { useAuthStore } from "../store/authStore";
import { buildSafeReturnLocation } from "../lib/safeNavigation";

export default function RequireAdmin({ children }) {
  const location = useLocation();
  const { isLoggedIn, user } = useAuthStore();
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

  if (!user?.isAdmin) {
    return <Navigate to="/" replace state={{ from: returnLocation, reason: "admin-required" }} />;
  }

  return children;
}
