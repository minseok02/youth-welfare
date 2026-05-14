import { create } from "zustand";
import { persist } from "zustand/middleware";

export const useAuthStore = create(
  persist(
    (set) => ({
      user: null,
      isLoggedIn: false,
      filterSettings: {
        includeExpired: false,
      },
      login: (token, user) => {
        localStorage.setItem("token", token);
        set({ user, isLoggedIn: true });
      },
      setUser: (nextUser) =>
        set((state) => ({ user: { ...(state.user ?? {}), ...(nextUser ?? {}) } })),
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
