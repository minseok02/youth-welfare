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
    location: {
      origin: "https://youthmoa.kr",
    },
    localStorage: storage,
  };
};

installBrowserGlobals();

const { default: api } = await import("./axios.js");
const { clearAuthSessionEventListenersForTest, subscribeAuthExpired } = await import("./authSessionEvents.js");
const { useAuthStore } = await import("../store/authStore.js");
const { useNetworkStore } = await import("../store/networkStore.js");

const resetStores = () => {
  useAuthStore.setState({
    accessToken: null,
    user: null,
    isLoggedIn: false,
    authReady: true,
    filterSettings: { includeExpired: false },
  });
  useNetworkStore.setState({ serverDown: false });
};

const responseFor = (config, data = { ok: true }) => Promise.resolve({
  data,
  status: 200,
  statusText: "OK",
  headers: {},
  config,
});

const unauthorizedFor = (config) => Promise.reject({
  name: "AxiosError",
  isAxiosError: true,
  config,
  response: {
    status: 401,
    data: { message: "unauthorized" },
  },
});

const readAuthorizationHeader = (headers) => {
  if (!headers) return undefined;
  if (typeof headers.get === "function") {
    return headers.get("Authorization") ?? headers.get("authorization");
  }
  return headers.Authorization ?? headers.authorization;
};

const delay = (ms) => new Promise((resolve) => {
  setTimeout(resolve, ms);
});

beforeEach(() => {
  installBrowserGlobals();
  clearAuthSessionEventListenersForTest();
  resetStores();
  api.defaults.adapter = undefined;
});

describe("api request auth headers", () => {
  test("adds bearer token only for trusted API requests", async () => {
    useAuthStore.setState({
      accessToken: "access-token",
      user: { email: "user@example.com" },
      isLoggedIn: true,
      authReady: true,
    });

    const seen = [];
    api.defaults.adapter = (config) => {
      seen.push({
        url: config.url,
        auth: readAuthorizationHeader(config.headers),
      });
      return responseFor(config);
    };

    await api.get("/api/policies");
    await api.get("https://evil.example/api/policies");
    await api.post("/api/auth/refresh", null, { skipAuth: true });

    assert.deepEqual(seen, [
      { url: "/api/policies", auth: "Bearer access-token" },
      { url: "https://evil.example/api/policies", auth: undefined },
      { url: "/api/auth/refresh", auth: undefined },
    ]);
  });
});

describe("api refresh queue", () => {
  test("refreshes once and replays concurrent 401 requests with the new token", async () => {
    useAuthStore.setState({
      accessToken: "old-token",
      user: { email: "user@example.com" },
      isLoggedIn: true,
      authReady: true,
    });

    let refreshCount = 0;
    const requestCounts = new Map();
    const retriedAuthorization = new Map();

    api.defaults.adapter = async (config) => {
      if (config.url === "/api/auth/refresh") {
        refreshCount += 1;
        await delay(10);
        return responseFor(config, { data: { accessToken: "new-token" } });
      }

      const count = requestCounts.get(config.url) ?? 0;
      requestCounts.set(config.url, count + 1);
      if (count === 0) {
        return unauthorizedFor(config);
      }

      retriedAuthorization.set(config.url, readAuthorizationHeader(config.headers));
      return responseFor(config, { ok: true, url: config.url });
    };

    const [first, second] = await Promise.all([
      api.get("/api/protected-a"),
      api.get("/api/protected-b"),
    ]);

    assert.equal(refreshCount, 1);
    assert.equal(first.data.url, "/api/protected-a");
    assert.equal(second.data.url, "/api/protected-b");
    assert.equal(useAuthStore.getState().accessToken, "new-token");
    assert.equal(retriedAuthorization.get("/api/protected-a"), "Bearer new-token");
    assert.equal(retriedAuthorization.get("/api/protected-b"), "Bearer new-token");
  });

  test("clears the session and notifies once when refresh fails", async () => {
    useAuthStore.setState({
      accessToken: "expired-token",
      user: { email: "user@example.com" },
      isLoggedIn: true,
      authReady: true,
    });

    let authExpiredCalls = 0;
    subscribeAuthExpired(() => {
      authExpiredCalls += 1;
    });

    api.defaults.adapter = (config) => {
      if (config.url === "/api/auth/refresh") {
        return unauthorizedFor(config);
      }
      return unauthorizedFor(config);
    };

    await assert.rejects(() => api.get("/api/protected"));

    assert.equal(authExpiredCalls, 1);
    assert.equal(globalThis.window.__authExpired, undefined);
    assert.equal(useAuthStore.getState().accessToken, null);
    assert.equal(useAuthStore.getState().isLoggedIn, false);
  });

  test("does not attempt refresh for auth endpoint failures", async () => {
    useAuthStore.setState({
      accessToken: "expired-token",
      user: { email: "user@example.com" },
      isLoggedIn: true,
      authReady: true,
    });

    let refreshCount = 0;
    api.defaults.adapter = (config) => {
      if (config.url === "/api/auth/refresh") {
        refreshCount += 1;
      }
      return unauthorizedFor(config);
    };

    await assert.rejects(() => api.post("/api/auth/login", { email: "user@example.com" }));

    assert.equal(refreshCount, 0);
    assert.equal(useAuthStore.getState().accessToken, "expired-token");
  });
});
