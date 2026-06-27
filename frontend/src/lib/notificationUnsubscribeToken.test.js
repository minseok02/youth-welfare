import assert from "node:assert/strict";
import { describe, test } from "node:test";
import {
  hasNotificationUnsubscribeTokenInUrl,
  readNotificationUnsubscribeTokenFromHash,
} from "./notificationUnsubscribeToken.js";

describe("readNotificationUnsubscribeTokenFromHash", () => {
  test("reads trimmed token from URL hash parameters", () => {
    assert.equal(readNotificationUnsubscribeTokenFromHash("#token= unsubscribe-token "), "unsubscribe-token");
    assert.equal(readNotificationUnsubscribeTokenFromHash("token=abc123&source=email"), "abc123");
  });

  test("rejects missing, oversized, and control-character tokens", () => {
    assert.equal(readNotificationUnsubscribeTokenFromHash("?token=query-token"), "");
    assert.equal(readNotificationUnsubscribeTokenFromHash("#source=email"), "");
    assert.equal(readNotificationUnsubscribeTokenFromHash(`#token=${"a".repeat(2049)}`), "");
    assert.equal(readNotificationUnsubscribeTokenFromHash("#token=abc%0Adef"), "");
  });
});

describe("hasNotificationUnsubscribeTokenInUrl", () => {
  test("detects token parameters in hash or query for URL cleanup", () => {
    assert.equal(hasNotificationUnsubscribeTokenInUrl({ hash: "#token=hash-token" }), true);
    assert.equal(hasNotificationUnsubscribeTokenInUrl({ search: "?token=query-token" }), true);
    assert.equal(hasNotificationUnsubscribeTokenInUrl({ search: "?source=email", hash: "#done" }), false);
  });
});
