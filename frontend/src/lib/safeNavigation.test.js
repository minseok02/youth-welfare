import assert from "node:assert/strict";
import { beforeEach, describe, test } from "node:test";
import {
  buildSafeReturnLocation,
  resolveSafeInternalPath,
  resolveSafeRouteTarget,
  sanitizePostLoginAction,
  sanitizeTransientRouteState,
} from "./safeNavigation.js";

beforeEach(() => {
  globalThis.window = {
    location: {
      origin: "https://youthmoa.kr",
    },
  };
});

describe("resolveSafeInternalPath", () => {
  test("allows same-origin internal paths with query and hash", () => {
    assert.equal(
      resolveSafeInternalPath("/policies/10?from=alert#detail"),
      "/policies/10?from=alert#detail",
    );
    assert.equal(
      resolveSafeInternalPath("https://youthmoa.kr/mypage?tab=3"),
      "/mypage?tab=3",
    );
  });

  test("rejects external origins, protocol-relative paths, control characters, and oversized paths", () => {
    assert.equal(resolveSafeInternalPath("https://evil.example/policies"), null);
    assert.equal(resolveSafeInternalPath("//evil.example/policies"), null);
    assert.equal(resolveSafeInternalPath("/policies/1\nSet-Cookie:bad"), null);
    assert.equal(resolveSafeInternalPath(`/${"a".repeat(2048)}`), null);
  });
});

describe("sanitizePostLoginAction", () => {
  test("keeps only supported post-login actions with positive integer policy ids", () => {
    assert.deepEqual(
      sanitizePostLoginAction({ type: "toggle-bookmark", policyId: "42" }),
      { type: "toggle-bookmark", policyId: 42 },
    );
    assert.equal(sanitizePostLoginAction({ type: "delete-account", policyId: 42 }), undefined);
    assert.equal(sanitizePostLoginAction({ type: "toggle-bookmark", policyId: 0 }), undefined);
  });
});

describe("resolveSafeRouteTarget", () => {
  test("normalizes string and object route targets", () => {
    assert.deepEqual(resolveSafeRouteTarget("/chat?session=1#latest"), {
      path: "/chat?session=1#latest",
      pathname: "/chat",
      search: "?session=1",
      hash: "#latest",
      state: undefined,
    });

    assert.deepEqual(resolveSafeRouteTarget({
      pathname: "/policies/7",
      search: "?source=alert",
      hash: "#apply",
      state: { coachPolicyId: 7, keep: "yes" },
    }), {
      path: "/policies/7?source=alert#apply",
      pathname: "/policies/7",
      search: "?source=alert",
      hash: "#apply",
      state: { keep: "yes" },
    });

    assert.deepEqual(resolveSafeRouteTarget({
      pathname: "/chat",
      state: { coachPolicyId: "7", recommendationLogId: 11, keep: "yes" },
    }), {
      path: "/chat",
      pathname: "/chat",
      search: "",
      hash: "",
      state: { coachPolicyId: 7, keep: "yes" },
    });
  });

  test("rejects malformed route target objects", () => {
    assert.equal(resolveSafeRouteTarget({ pathname: "https://evil.example" }), null);
    assert.equal(resolveSafeRouteTarget({ pathname: "/chat", search: "session=1" }), null);
    assert.equal(resolveSafeRouteTarget({ pathname: "/chat", hash: "section" }), null);
  });
});

describe("sanitizeTransientRouteState", () => {
  test("removes transient keys and sanitizes nested return locations", () => {
    assert.deepEqual(sanitizeTransientRouteState({
      coachPolicyId: 10,
      recommendationLogId: 20,
      keep: "ok",
      from: {
        pathname: "/policies/10",
        search: "?q=rent",
        state: {
          chatFrom: {
            pathname: "/chat",
            state: {
              from: {
                pathname: "/too-deep",
                state: {
                  from: "/dropped",
                },
              },
            },
          },
        },
      },
      postLoginAction: { type: "toggle-bookmark", policyId: "10" },
    }), {
      keep: "ok",
      from: {
        pathname: "/policies/10",
        search: "?q=rent",
        hash: "",
        state: {
          chatFrom: {
            pathname: "/chat",
            search: "",
            hash: "",
            state: {
              from: {
                pathname: "/too-deep",
                search: "",
                hash: "",
                state: undefined,
              },
            },
          },
        },
      },
      postLoginAction: { type: "toggle-bookmark", policyId: 10 },
    });
  });

  test("drops unsafe nested targets and invalid post-login actions", () => {
    assert.deepEqual(sanitizeTransientRouteState({
      from: "https://evil.example/login",
      chatFrom: { pathname: "/chat", search: "bad" },
      postLoginAction: { type: "toggle-bookmark", policyId: -1 },
    }), undefined);
  });
});

describe("buildSafeReturnLocation", () => {
  test("builds a sanitized return location from a router location object", () => {
    assert.deepEqual(buildSafeReturnLocation({
      pathname: "/alerts",
      search: "?unread=1",
      hash: "#top",
      state: {
        coachPolicyId: 1,
        from: "/policies/2",
      },
    }), {
      pathname: "/alerts",
      search: "?unread=1",
      hash: "#top",
      state: {
        from: {
          pathname: "/policies/2",
          search: "",
          hash: "",
          state: undefined,
        },
      },
    });
  });

  test("keeps chat coaching policy id only for chat login returns", () => {
    assert.deepEqual(buildSafeReturnLocation({
      pathname: "/chat",
      state: {
        coachPolicyId: "12",
        recommendationLogId: 99,
        from: "/policies/12",
      },
    }), {
      pathname: "/chat",
      search: "",
      hash: "",
      state: {
        coachPolicyId: 12,
        from: {
          pathname: "/policies/12",
          search: "",
          hash: "",
          state: undefined,
        },
      },
    });
  });
});
