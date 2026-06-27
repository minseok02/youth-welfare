import axios from "axios";
import { notifyAuthExpired } from "./authSessionEvents.js";
import { useAuthStore } from "../store/authStore.js";
import { useNetworkStore } from "../store/networkStore.js";

const viteEnv = import.meta.env ?? {};
const apiBaseUrl = viteEnv.VITE_API_BASE_URL?.trim() || "";
const DEFAULT_API_TIMEOUT_MS = 45000;
const REFRESH_PATH = "/api/auth/refresh";
const AUTH_PATH_PREFIX = "/api/auth/";
const API_PATH_PREFIX = "/api/";

const resolveTimeoutMillis = () => {
  const rawValue = viteEnv.VITE_API_TIMEOUT_MS?.trim();
  const parsedValue = Number.parseInt(rawValue ?? "", 10);
  return Number.isFinite(parsedValue) && parsedValue > 0
    ? parsedValue
    : DEFAULT_API_TIMEOUT_MS;
};

const runtimeOrigin = () => (
  typeof window !== "undefined" && window.location?.origin
    ? window.location.origin
    : "http://localhost"
);

const configuredApiOrigin = () => {
  try {
    return new URL(apiBaseUrl || runtimeOrigin(), runtimeOrigin()).origin;
  } catch {
    return runtimeOrigin();
  }
};

const api = axios.create({
  baseURL: apiBaseUrl,
  withCredentials: true,
  timeout: resolveTimeoutMillis(),
  timeoutErrorMessage: "서버 응답 시간이 초과되었습니다.",
});

const resolveRequestUrl = (config) => {
  const rawUrl = config?.url ?? "";
  try {
    return new URL(rawUrl, config?.baseURL || apiBaseUrl || runtimeOrigin());
  } catch {
    return null;
  }
};

const isTrustedApiRequest = (config) => {
  const parsed = resolveRequestUrl(config);
  return Boolean(parsed)
    && parsed.origin === configuredApiOrigin()
    && parsed.pathname.startsWith(API_PATH_PREFIX);
};

const isPathRequest = (config, pathOrPrefix, { prefix = false } = {}) => {
  const parsed = resolveRequestUrl(config);
  if (!parsed) return false;
  return prefix
    ? parsed.pathname.startsWith(pathOrPrefix)
    : parsed.pathname === pathOrPrefix;
};

const clearAuthorizationHeader = (headers) => {
  if (!headers) return;
  delete headers.Authorization;
  delete headers.authorization;
};

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  config.headers = config.headers ?? {};
  if (token && isTrustedApiRequest(config) && !config.skipAuth && !isPathRequest(config, REFRESH_PATH)) {
    config.headers.Authorization = `Bearer ${token}`;
  } else {
    clearAuthorizationHeader(config.headers);
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
  (response) => {
    useNetworkStore.getState().setServerDown(false);
    return response;
  },
  async (error) => {
    const isCanceled = error.name === "CanceledError" || error.code === "ERR_CANCELED";
    if (!isCanceled && !error.response) {
      useNetworkStore.getState().setServerDown(true);
    }
    const originalRequest = error.config;
    const isRefreshRequest = isPathRequest(originalRequest, REFRESH_PATH);
    const isAuthRequest = isPathRequest(originalRequest, AUTH_PATH_PREFIX, { prefix: true });

    if (error.response?.status === 401 && !originalRequest?._retry && !isRefreshRequest && !isAuthRequest) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            originalRequest.headers = originalRequest.headers ?? {};
            originalRequest.headers.Authorization = `Bearer ${token}`;
            return api(originalRequest);
          })
          .catch((err) => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const { data } = await api.post(REFRESH_PATH, null, { skipAuth: true });
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
        notifyAuthExpired({ reason: "expired" });
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);

export default api;
