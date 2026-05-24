import { create } from "zustand";
import { persist } from "zustand/middleware";

const LEGACY_TOKEN_STORAGE_KEY = "token";
const LEGACY_AUTH_STORE_KEY = "auth-store";

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

export const useAuthStore = create(
  persist(
    (set) => ({
      accessToken: null,
      user: null,
      isLoggedIn: false,
      authReady: false,
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
      clearSession: () => {
        set({
          accessToken: null,
          user: null,
          isLoggedIn: false,
          authReady: true,
        });
      },
      logout: () => {
        set({
          accessToken: null,
          user: null,
          isLoggedIn: false,
          authReady: true,
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
