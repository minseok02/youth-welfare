import { create } from "zustand";
import { persist } from "zustand/middleware";

const LEGACY_TOKEN_STORAGE_KEY = "token";
const LEGACY_AUTH_STORE_KEY = "auth-store";
const PROFILE_HYDRATION_ERROR_MESSAGE_MAX = 160;

const idleProfileHydration = () => ({
  status: "idle",
  lastError: null,
  lastFailedAt: null,
});

if (typeof window !== "undefined") {
  window.localStorage.removeItem(LEGACY_TOKEN_STORAGE_KEY);
  window.localStorage.removeItem(LEGACY_AUTH_STORE_KEY);
}

const readTokenRoles = (token) => {
  if (!token) return [];

  try {
    const [, payloadPart] = token.split(".");
    if (!payloadPart) return [];
    const normalized = payloadPart.replace(/-/g, "+").replace(/_/g, "/");
    const decoded = JSON.parse(atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=")));
    return Array.isArray(decoded?.roles)
      ? decoded.roles.filter((role) => typeof role === "string" && role)
      : [];
  } catch {
    return [];
  }
};

const sanitizeProfileHydrationError = (error) => {
  const rawMessage = typeof error?.message === "string" && error.message.trim()
    ? error.message.trim()
    : "profile hydration failed";

  return {
    status: Number.isInteger(error?.response?.status) ? error.response.status : null,
    code: typeof error?.code === "string" && error.code.trim() ? error.code.trim().slice(0, 64) : null,
    message: rawMessage.slice(0, PROFILE_HYDRATION_ERROR_MESSAGE_MAX),
  };
};

export const useAuthStore = create(
  persist(
    (set) => ({
      accessToken: null,
      user: null,
      isLoggedIn: false,
      authReady: false,
      profileHydration: idleProfileHydration(),
      filterSettings: {
        includeExpired: false,
      },
      login: (token, user) => {
        const roles = readTokenRoles(token);
        set((state) => ({
          accessToken: token,
          user: {
            ...(state.user ?? {}),
            ...(user ?? {}),
            roles,
            isAdmin: roles.includes("ROLE_ADMIN"),
          },
          isLoggedIn: true,
          authReady: true,
        }));
      },
      setUser: (nextUser) =>
        set((state) => ({
          user: {
            ...(state.user ?? {}),
            ...(nextUser ?? {}),
            roles: Array.isArray(nextUser?.roles)
              ? nextUser.roles
              : (state.user?.roles ?? []),
            isAdmin: Array.isArray(nextUser?.roles)
              ? nextUser.roles.includes("ROLE_ADMIN")
              : Boolean(nextUser?.isAdmin ?? state.user?.isAdmin),
          },
        })),
      markProfileHydrationStarted: () => {
        set({
          profileHydration: {
            status: "loading",
            lastError: null,
            lastFailedAt: null,
          },
        });
      },
      markProfileHydrationSucceeded: () => {
        set({
          profileHydration: {
            status: "succeeded",
            lastError: null,
            lastFailedAt: null,
          },
        });
      },
      markProfileHydrationFailed: (error) => {
        set({
          profileHydration: {
            status: "failed",
            lastError: sanitizeProfileHydrationError(error),
            lastFailedAt: new Date().toISOString(),
          },
        });
      },
      clearSession: () => {
        set({
          accessToken: null,
          user: null,
          isLoggedIn: false,
          authReady: true,
          profileHydration: idleProfileHydration(),
        });
      },
      logout: () => {
        set({
          accessToken: null,
          user: null,
          isLoggedIn: false,
          authReady: true,
          profileHydration: idleProfileHydration(),
          filterSettings: { includeExpired: false },
        });
      },
      setFilterSettings: (settings) =>
        set((state) => ({ filterSettings: { ...state.filterSettings, ...settings } })),
    }),
    {
      name: "auth-ui-store",
      partialize: (state) => ({
        filterSettings: state.filterSettings,
      }),
    }
  )
);
