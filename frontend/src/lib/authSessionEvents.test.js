import assert from "node:assert/strict";
import { beforeEach, describe, test } from "node:test";

import {
  clearAuthSessionEventListenersForTest,
  notifyAuthExpired,
  subscribeAuthExpired,
} from "./authSessionEvents.js";

describe("auth session events", () => {
  beforeEach(() => {
    clearAuthSessionEventListenersForTest();
  });

  test("notifies subscribers and stops after unsubscribe", () => {
    const events = [];
    const unsubscribe = subscribeAuthExpired((event) => {
      events.push(event);
    });

    notifyAuthExpired({ reason: "refresh-failed", from: { pathname: "/my" } });
    unsubscribe();
    notifyAuthExpired({ reason: "ignored" });

    assert.deepEqual(events, [
      {
        reason: "refresh-failed",
        from: { pathname: "/my" },
      },
    ]);
  });

  test("uses expired as the default reason", () => {
    const events = [];
    subscribeAuthExpired((event) => {
      events.push(event);
    });

    notifyAuthExpired();

    assert.equal(events[0].reason, "expired");
  });
});
