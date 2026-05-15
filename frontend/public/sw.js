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
    url: "/mypage?tab=3",
  };

  let payload = fallback;
  try {
    if (event.data) {
      payload = { ...fallback, ...event.data.json() };
    }
  } catch {
    payload = fallback;
  }

  event.waitUntil(
    self.registration.showNotification(payload.title, {
      body: payload.body,
      icon: "/favicon.svg",
      badge: "/favicon.svg",
      data: {
        url: payload.url,
      },
    })
  );
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const targetUrl = event.notification.data?.url || "/mypage?tab=3";

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
