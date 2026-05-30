import axios from "axios";
import { useAuthStore } from "../store/authStore";

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim() || "";
const REFRESH_PATH = "/api/auth/refresh";
const AUTH_PATH_PREFIX = "/api/auth/";

const api = axios.create({
  baseURL: apiBaseUrl,
  withCredentials: true,
});

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  } else if (config.headers?.Authorization) {
    delete config.headers.Authorization;
  }
  return config;
});

let isRefreshing = false;
let failedQueue = [];

const processQueue = (error, token = null) => {
  failedQueue.forEach((prom) => {
    if (error) prom.reject(error);
    else prom.resolve(token);
  });
  failedQueue = [];
};

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    const requestUrl = originalRequest?.url ?? "";
    const isRefreshRequest = requestUrl.includes(REFRESH_PATH);
    const isAuthRequest = requestUrl.includes(AUTH_PATH_PREFIX);

    if (error.response?.status === 401 && !originalRequest?._retry && !isRefreshRequest && !isAuthRequest) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            originalRequest.headers.Authorization = `Bearer ${token}`;
            return api(originalRequest);
          })
          .catch((err) => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const { data } = await api.post(REFRESH_PATH);
        const newToken = data?.data?.accessToken;
        if (!newToken) {
          throw new Error("refresh token rotation response missing accessToken");
        }
        useAuthStore.getState().login(newToken, useAuthStore.getState().user);
        processQueue(null, newToken);
        return api(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        useAuthStore.getState().clearSession();
        window.__authExpired?.();
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);

export default api;
