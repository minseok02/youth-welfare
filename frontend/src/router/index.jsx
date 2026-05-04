import React from "react";
import { createBrowserRouter } from "react-router-dom";
import MainPage from "../pages/MainPage.jsx";
import LoginPage from "../pages/LoginPage.jsx";
import SignupPage from "../pages/SignupPage.jsx";
import ResetPasswordPage from "../pages/ResetPasswordPage.jsx";
import PoliciesPage from "../pages/PoliciesPage.jsx";
import PolicyDetailPage from "../pages/PolicyDetailPage.jsx";
import MyPage from "../pages/MyPage.jsx";
import ChatPage from "../pages/ChatPage.jsx";
import RequireLogin from "../components/RequireLogin.jsx";

const router = createBrowserRouter([
  { path: "/", element: <MainPage /> },
  { path: "/login", element: <LoginPage /> },
  { path: "/signup", element: <SignupPage /> },
  { path: "/reset-password", element: <ResetPasswordPage /> },
  { path: "/policies", element: <PoliciesPage /> },
  { path: "/policies/:id", element: <PolicyDetailPage /> },
  {
    path: "/mypage",
    element: (
      <RequireLogin>
        <MyPage />
      </RequireLogin>
    ),
  },
  {
    path: "/chat",
    element: (
      <RequireLogin>
        <ChatPage />
      </RequireLogin>
    ),
  },
]);

export default router;
