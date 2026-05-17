import { lazy } from "react";

export const MainPage = lazy(() => import("../pages/MainPage.jsx"));
export const AlertsPage = lazy(() => import("../pages/AlertsPage.jsx"));
export const LoginPage = lazy(() => import("../pages/LoginPage.jsx"));
export const SignupPage = lazy(() => import("../pages/SignupPage.jsx"));
export const ResetPasswordPage = lazy(() => import("../pages/ResetPasswordPage.jsx"));
export const PoliciesPage = lazy(() => import("../pages/PoliciesPage.jsx"));
export const PolicyDetailPage = lazy(() => import("../pages/PolicyDetailPage.jsx"));
export const MyPage = lazy(() => import("../pages/MyPage.jsx"));
export const ChatPage = lazy(() => import("../pages/ChatPage.jsx"));
export const AdminDashboardPage = lazy(() => import("../pages/AdminDashboardPage.jsx"));
