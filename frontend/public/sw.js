self.FALLBACK_NOTIFICATION_URL = "/mypage?tab=3";

const sanitizeNotificationText = (value, fallback, maxLength) => {
  if (typeof value !== "string") {
    return fallback;
  }
  const trimmed = value.trim();
  if (!trimmed) {
    return fallback;
  }
  return trimmed.length > maxLength ? trimmed.slice(0, maxLength) : trimmed;
};

const resolveSafeNotificationUrl = (rawUrl) => {
  if (typeof rawUrl !== "string") {
    return self.FALLBACK_NOTIFICATION_URL;
  }

  const trimmed = rawUrl.trim();
  if (!trimmed) {
    return self.FALLBACK_NOTIFICATION_URL;
  }

  const isPath = trimmed.startsWith("/") && !trimmed.startsWith("//");
  const isAbsolute = /^https?:\/\//i.test(trimmed);
  if (!isPath && !isAbsolute) {
    return self.FALLBACK_NOTIFICATION_URL;
  }

  try {
    const parsed = new URL(trimmed, self.location.origin);
    if (parsed.origin !== self.location.origin) {
      return self.FALLBACK_NOTIFICATION_URL;
    }
    if (!/^https?:$/i.test(parsed.protocol)) {
      return self.FALLBACK_NOTIFICATION_URL;
    }
    return `${parsed.pathname}${parsed.search}${parsed.hash}`;
  } catch {
    return self.FALLBACK_NOTIFICATION_URL;
  }
};

self.addEventListener("install", (event) => {
  event.waitUntil(self.skipWaiting());
});

self.addEventListener("activate", (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener("push", (event) => {
  const fallback = {
    title: "청년복지플랫폼",
    body: "새로운 알림이 도착했습니다.",
    url: self.FALLBACK_NOTIFICATION_URL,
  };

  let payload = fallback;
  try {
    if (event.data) {
      payload = { ...fallback, ...event.data.json() };
    }
  } catch {
    payload = fallback;
  }

  const title = sanitizeNotificationText(payload.title, fallback.title, 100);
  const body = sanitizeNotificationText(payload.body, fallback.body, 500);

  event.waitUntil(
    self.registration.showNotification(title, {
      body,
      icon: "/favicon.svg",
      badge: "/favicon.svg",
      data: {
        url: resolveSafeNotificationUrl(payload.url),
      },
    })
  );
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const targetUrl = resolveSafeNotificationUrl(event.notification.data?.url);

  event.waitUntil((async () => {
    const allClients = await self.clients.matchAll({ type: "window", includeUncontrolled: true });
    const matched = allClients.find((client) => "focus" in client);
    if (matched) {
      await matched.focus();
      if ("navigate" in matched) {
        await matched.navigate(targetUrl);
      }
      return;
    }
    await self.clients.openWindow(targetUrl);
  })());
});
