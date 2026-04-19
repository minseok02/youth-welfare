import React from "react";
import { createBrowserRouter } from "react-router-dom";
import MainPage from "../pages/MainPage.jsx";
import LoginPage from "../pages/LoginPage.jsx";
import SignupPage from "../pages/SignupPage.jsx";
import PoliciesPage from "../pages/PoliciesPage.jsx";
import PolicyDetailPage from "../pages/PolicyDetailPage.jsx";
import MyPage from "../pages/MyPage.jsx";

const router = createBrowserRouter([
  { path: "/", element: <MainPage /> },
  { path: "/login", element: <LoginPage /> },
  { path: "/signup", element: <SignupPage /> },
  { path: "/policies", element: <PoliciesPage /> },
  { path: "/policies/:id", element: <PolicyDetailPage /> },
  { path: "/mypage", element: <MyPage /> },
  { path: "/chat", element: <div>챗봇 (Phase 4 예정)</div> },
]);

export default router;
