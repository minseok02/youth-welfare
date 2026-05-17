import { create } from "zustand";
import { persist } from "zustand/middleware";

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
      user: null,
      isLoggedIn: false,
      filterSettings: {
        includeExpired: false,
      },
      login: (token, user) => {
        const roles = readTokenRoles(token);
        localStorage.setItem("token", token);
        set({
          user: {
            ...(user ?? {}),
            roles,
            isAdmin: roles.includes("ROLE_ADMIN"),
          },
          isLoggedIn: true,
        });
      },
      setUser: (nextUser) =>
        set((state) => ({
          user: {
            ...(state.user ?? {}),
            ...(nextUser ?? {}),
            isAdmin: Array.isArray(nextUser?.roles)
              ? nextUser.roles.includes("ROLE_ADMIN")
              : Boolean(nextUser?.isAdmin ?? state.user?.isAdmin),
          },
        })),
      logout: () => {
        localStorage.removeItem("token");
        set({ user: null, isLoggedIn: false, filterSettings: { includeExpired: false } });
      },
      setFilterSettings: (settings) =>
        set((state) => ({ filterSettings: { ...state.filterSettings, ...settings } })),
    }),
    { name: "auth-store" }
  )
);
