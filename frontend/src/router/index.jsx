import { createBrowserRouter } from "react-router-dom";
import AuthExpiryHandler from "../components/AuthExpiryHandler.jsx";
import RequireLogin from "../components/RequireLogin.jsx";
import LazyRoute from "./LazyRoute.jsx";
import {
  AlertsPage,
  ChatPage,
  LoginPage,
  MainPage,
  MyPage,
  PoliciesPage,
  PolicyDetailPage,
  ResetPasswordPage,
  SignupPage,
} from "./lazy-pages.jsx";

const router = createBrowserRouter([
  { path: "/", element: <AuthExpiryHandler><LazyRoute><MainPage /></LazyRoute></AuthExpiryHandler> },
  { path: "/login", element: <AuthExpiryHandler><LazyRoute><LoginPage /></LazyRoute></AuthExpiryHandler> },
  { path: "/signup", element: <AuthExpiryHandler><LazyRoute><SignupPage /></LazyRoute></AuthExpiryHandler> },
  { path: "/reset-password", element: <AuthExpiryHandler><LazyRoute><ResetPasswordPage /></LazyRoute></AuthExpiryHandler> },
  { path: "/policies", element: <AuthExpiryHandler><LazyRoute><PoliciesPage /></LazyRoute></AuthExpiryHandler> },
  { path: "/policies/:id", element: <AuthExpiryHandler><LazyRoute><PolicyDetailPage /></LazyRoute></AuthExpiryHandler> },
  {
    path: "/alerts",
    element: (
      <AuthExpiryHandler>
        <LazyRoute>
          <RequireLogin>
            <AlertsPage />
          </RequireLogin>
        </LazyRoute>
      </AuthExpiryHandler>
    ),
  },
  {
    path: "/mypage",
    element: (
      <AuthExpiryHandler>
        <LazyRoute>
          <RequireLogin>
            <MyPage />
          </RequireLogin>
        </LazyRoute>
      </AuthExpiryHandler>
    ),
  },
  {
    path: "/chat",
    element: (
      <AuthExpiryHandler>
        <LazyRoute>
          <RequireLogin>
            <ChatPage />
          </RequireLogin>
        </LazyRoute>
      </AuthExpiryHandler>
    ),
  },
]);

export default router;
