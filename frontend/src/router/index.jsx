import { createBrowserRouter } from "react-router-dom";
import AuthExpiryHandler from "../components/AuthExpiryHandler.jsx";
import NavLayout from "../components/NavLayout.jsx";
import RequireAdmin from "../components/RequireAdmin.jsx";
import RequireLogin from "../components/RequireLogin.jsx";
import LazyRoute from "./LazyRoute.jsx";
import {
  AdminDashboardPage,
  AlertsPage,
  ChatPage,
  GuidePage,
  LoginPage,
  MainPage,
  MyPage,
  NotFoundPage,
  NotificationUnsubscribePage,
  PoliciesPage,
  PolicyDetailPage,
  PrivacyPolicyPage,
  ResetPasswordPage,
  SignupPage,
  SupportPage,
  TermsPage,
} from "./lazy-pages.jsx";

const router = createBrowserRouter([
  { path: "/", element: <AuthExpiryHandler><LazyRoute><NavLayout><MainPage /></NavLayout></LazyRoute></AuthExpiryHandler> },
  { path: "/guide", element: <AuthExpiryHandler><LazyRoute><NavLayout><GuidePage /></NavLayout></LazyRoute></AuthExpiryHandler> },
  { path: "/support", element: <AuthExpiryHandler><LazyRoute><NavLayout><SupportPage /></NavLayout></LazyRoute></AuthExpiryHandler> },
  { path: "/login", element: <AuthExpiryHandler><LazyRoute><NavLayout><LoginPage /></NavLayout></LazyRoute></AuthExpiryHandler> },
  { path: "/signup", element: <AuthExpiryHandler><LazyRoute><NavLayout><SignupPage /></NavLayout></LazyRoute></AuthExpiryHandler> },
  { path: "/terms", element: <AuthExpiryHandler><LazyRoute><NavLayout><TermsPage /></NavLayout></LazyRoute></AuthExpiryHandler> },
  { path: "/privacy", element: <AuthExpiryHandler><LazyRoute><NavLayout><PrivacyPolicyPage /></NavLayout></LazyRoute></AuthExpiryHandler> },
  { path: "/reset-password", element: <AuthExpiryHandler><LazyRoute><NavLayout><ResetPasswordPage /></NavLayout></LazyRoute></AuthExpiryHandler> },
  { path: "/notifications/unsubscribe", element: <LazyRoute><NotificationUnsubscribePage /></LazyRoute> },
  { path: "/policies", element: <AuthExpiryHandler><LazyRoute><NavLayout><PoliciesPage /></NavLayout></LazyRoute></AuthExpiryHandler> },
  { path: "/policies/:id", element: <AuthExpiryHandler><LazyRoute><NavLayout><PolicyDetailPage /></NavLayout></LazyRoute></AuthExpiryHandler> },
  {
    path: "/alerts",
    element: (
      <AuthExpiryHandler>
        <LazyRoute>
          <RequireLogin>
            <NavLayout>
              <AlertsPage />
            </NavLayout>
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
            <NavLayout>
              <MyPage />
            </NavLayout>
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
            <NavLayout>
              <ChatPage />
            </NavLayout>
          </RequireLogin>
        </LazyRoute>
      </AuthExpiryHandler>
    ),
  },
  {
    path: "/admin/dashboard",
    element: (
      <AuthExpiryHandler>
        <LazyRoute>
          <RequireAdmin>
            <NavLayout>
              <AdminDashboardPage />
            </NavLayout>
          </RequireAdmin>
        </LazyRoute>
      </AuthExpiryHandler>
    ),
  },
  { path: "*", element: <AuthExpiryHandler><LazyRoute><NavLayout><NotFoundPage /></NavLayout></LazyRoute></AuthExpiryHandler> },
]);

export default router;
