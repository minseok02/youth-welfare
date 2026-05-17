import { Navigate, useLocation } from "react-router-dom";
import { useAuthStore } from "../store/authStore";

export default function RequireAdmin({ children }) {
  const location = useLocation();
  const { isLoggedIn, user } = useAuthStore();

  if (!isLoggedIn) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: location, reason: "login-required" }}
      />
    );
  }

  if (!user?.isAdmin) {
    return <Navigate to="/" replace state={{ from: location, reason: "admin-required" }} />;
  }

  return children;
}
