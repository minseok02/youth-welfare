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

export async function validateWebPushPublicKey(publicKey) {
  try {
    const keyBytes = urlBase64ToUint8Array(publicKey);
    if (keyBytes.length !== 65 || keyBytes[0] !== 0x04) return false;
    if (window.crypto?.subtle?.importKey) {
      await window.crypto.subtle.importKey(
        "raw",
        keyBytes,
        { name: "ECDH", namedCurve: "P-256" },
        false,
        [],
      );
    }
    return true;
  } catch {
    return false;
  }
}

async function getReadyServiceWorkerRegistration() {
  const registration = await navigator.serviceWorker.register("/sw.js");
  const readyRegistration = await navigator.serviceWorker.ready;
  return readyRegistration ?? registration;
}

export async function getCurrentPushSubscription() {
  if (!isWebPushSupported()) return null;
  const registration = await navigator.serviceWorker.getRegistration()
    ?? await navigator.serviceWorker.ready.catch(() => null);
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
  const publicKeyValid = await validateWebPushPublicKey(publicKey);
  if (!publicKeyValid) {
    throw new Error("웹푸시 공개키 형식이 올바르지 않습니다.");
  }

  const permission = await Notification.requestPermission();
  if (permission !== "granted") {
    return { permission, subscription: null };
  }

  const registration = await getReadyServiceWorkerRegistration();
  let subscription = await registration.pushManager.getSubscription();
  if (!subscription) {
    subscription = await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(publicKey),
    });
  }
  if (!subscription?.endpoint) {
    throw new Error("브라우저 푸시 구독을 만들지 못했습니다.");
  }

  const payload = subscription.toJSON();
  if (!payload.keys?.p256dh || !payload.keys?.auth) {
    throw new Error("브라우저 푸시 구독 키를 읽지 못했습니다.");
  }
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
