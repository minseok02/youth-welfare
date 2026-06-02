import { create } from "zustand";

export const useNetworkStore = create((set) => ({
  serverDown: false,
  setServerDown: (v) => set({ serverDown: v }),
}));
