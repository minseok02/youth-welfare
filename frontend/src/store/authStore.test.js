import assert from "node:assert/strict";
import { beforeEach, describe, test } from "node:test";

const installBrowserGlobals = () => {
  const storage = {
    getItem: () => null,
    setItem: () => {},
    removeItem: () => {},
  };
  globalThis.localStorage = storage;
  globalThis.window = {
    localStorage: storage,
  };
};

installBrowserGlobals();

const { useAuthStore } = await import("./authStore.js");

const resetStore = () => {
  useAuthStore.setState({
    accessToken: null,
    user: null,
    isLoggedIn: false,
    authReady: false,
    profileHydration: {
      status: "idle",
      lastError: null,
      lastFailedAt: null,
    },
    filterSettings: { includeExpired: false },
  });
};

describe("auth store profile hydration observation", () => {
  beforeEach(() => {
    installBrowserGlobals();
    resetStore();
  });

  test("tracks sanitized profile hydration failures without clearing the session", () => {
    useAuthStore.getState().login("token", { email: "user@example.com" });
    useAuthStore.getState().markProfileHydrationStarted();
    useAuthStore.getState().markProfileHydrationFailed({
      code: "ERR_BAD_RESPONSE",
      message: "x".repeat(200),
      response: { status: 503 },
    });

    const state = useAuthStore.getState();
    assert.equal(state.isLoggedIn, true);
    assert.equal(state.accessToken, "token");
    assert.equal(state.profileHydration.status, "failed");
    assert.equal(state.profileHydration.lastError.status, 503);
    assert.equal(state.profileHydration.lastError.code, "ERR_BAD_RESPONSE");
    assert.equal(state.profileHydration.lastError.message.length, 160);
    assert.match(state.profileHydration.lastFailedAt, /^\d{4}-\d{2}-\d{2}T/);
  });

  test("clearSession resets profile hydration observation", () => {
    useAuthStore.getState().markProfileHydrationFailed(new Error("profile failed"));

    useAuthStore.getState().clearSession();

    assert.deepEqual(useAuthStore.getState().profileHydration, {
      status: "idle",
      lastError: null,
      lastFailedAt: null,
    });
  });
});
