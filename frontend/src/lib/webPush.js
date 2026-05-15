import api from "./axios";

function urlBase64ToUint8Array(base64String) {
  const padding = "=".repeat((4 - (base64String.length % 4)) % 4);
  const base64 = `${base64String}${padding}`.replace(/-/g, "+").replace(/_/g, "/");
  const rawData = window.atob(base64);
  return Uint8Array.from([...rawData].map((char) => char.charCodeAt(0)));
}

export function isWebPushSupported() {
  return (
    typeof window !== "undefined"
    && "Notification" in window
    && "serviceWorker" in navigator
    && "PushManager" in window
    && window.isSecureContext
  );
}

export async function getCurrentPushSubscription() {
  if (!isWebPushSupported()) return null;
  const registration = await navigator.serviceWorker.getRegistration("/sw.js")
    ?? await navigator.serviceWorker.getRegistration();
  if (!registration) return null;
  return registration.pushManager.getSubscription();
}

export async function fetchPushPublicKey() {
  const { data } = await api.get("/api/notifications/push-public-key");
  return data?.data?.publicKey ?? "";
}

export async function fetchMyPushSubscriptions() {
  const { data } = await api.get("/api/notifications/push-subscriptions/me");
  return Array.isArray(data?.data) ? data.data : [];
}

export async function registerCurrentBrowserPush({ publicKey, deviceLabel }) {
  const permission = await Notification.requestPermission();
  if (permission !== "granted") {
    return { permission, subscription: null };
  }

  const registration = await navigator.serviceWorker.register("/sw.js");
  let subscription = await registration.pushManager.getSubscription();
  if (!subscription) {
    subscription = await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(publicKey),
    });
  }

  const payload = subscription.toJSON();
  await api.post("/api/notifications/push-subscriptions", {
    endpoint: subscription.endpoint,
    p256dh: payload.keys?.p256dh,
    auth: payload.keys?.auth,
    userAgent: navigator.userAgent,
    deviceLabel,
  });

  return { permission, subscription };
}

export async function deletePushSubscription(subscriptionId) {
  await api.delete(`/api/notifications/push-subscriptions/${subscriptionId}`);
}

export async function unsubscribeCurrentBrowserPush() {
  const subscription = await getCurrentPushSubscription();
  if (subscription) {
    await subscription.unsubscribe();
  }
  return subscription;
}
