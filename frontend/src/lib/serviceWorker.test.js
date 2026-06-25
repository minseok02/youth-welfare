import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { describe, test } from "node:test";
import vm from "node:vm";

const loadServiceWorker = () => {
  const source = readFileSync(new URL("../../public/sw.js", import.meta.url), "utf8");
  const listeners = new Map();
  const notifications = [];
  const openedWindows = [];

  const self = {
    location: { origin: "https://youthmoa.kr" },
    addEventListener: (type, listener) => {
      listeners.set(type, listener);
    },
    skipWaiting: () => Promise.resolve(),
    registration: {
      showNotification: (title, options) => {
        notifications.push({ title, options });
        return Promise.resolve();
      },
    },
    clients: {
      claim: () => Promise.resolve(),
      matchAll: () => Promise.resolve([]),
      openWindow: (url) => {
        openedWindows.push(url);
        return Promise.resolve();
      },
    },
  };

  vm.runInNewContext(source, { self, URL }, { filename: "public/sw.js" });

  const dispatch = async (type, event) => {
    const waitPromises = [];
    listeners.get(type)({
      ...event,
      waitUntil: (promise) => {
        waitPromises.push(Promise.resolve(promise));
      },
    });
    await Promise.all(waitPromises);
  };

  return {
    dispatch,
    notifications,
    openedWindows,
    self,
  };
};

const pushEvent = (payload) => ({
  data: {
    json: () => payload,
  },
});

describe("service worker push notifications", () => {
  test("keeps same-origin notification URLs as internal paths", async () => {
    const worker = loadServiceWorker();

    await worker.dispatch("push", pushEvent({
      title: " 정책 알림 ",
      body: "신청 마감이 가까워요.",
      url: "https://youthmoa.kr/policies/42?source=push#apply",
    }));

    assert.equal(worker.notifications[0].title, "정책 알림");
    assert.equal(
      worker.notifications[0].options.data.url,
      "/policies/42?source=push#apply",
    );
  });

  test("falls back for external, protocol-relative, and malformed notification URLs", async () => {
    const cases = [
      "https://evil.example/policies/42",
      "//evil.example/policies/42",
      "http://[broken",
      "javascript:alert(1)",
    ];

    for (const rawUrl of cases) {
      const worker = loadServiceWorker();

      await worker.dispatch("push", pushEvent({
        title: "정책 알림",
        body: "본문",
        url: rawUrl,
      }));

      assert.equal(worker.notifications[0].options.data.url, worker.self.FALLBACK_NOTIFICATION_URL);
    }
  });

  test("uses safe text fallbacks and truncates long notification body", async () => {
    const worker = loadServiceWorker();

    await worker.dispatch("push", pushEvent({
      title: "   ",
      body: "가".repeat(600),
      url: "/mypage?tab=3",
    }));

    assert.equal(worker.notifications[0].title, "청년복지플랫폼");
    assert.equal(worker.notifications[0].options.body.length, 500);
  });
});

describe("service worker notification click", () => {
  test("opens only a sanitized internal target", async () => {
    const worker = loadServiceWorker();

    await worker.dispatch("notificationclick", {
      notification: {
        data: { url: "https://evil.example/phishing" },
        close: () => {},
      },
    });

    assert.deepEqual(worker.openedWindows, [worker.self.FALLBACK_NOTIFICATION_URL]);
  });
});
