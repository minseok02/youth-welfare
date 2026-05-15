import api from "./axios";

function withTimeout(promise, ms, message) {
  return Promise.race([
    promise,
    new Promise((_, reject) => {
      window.setTimeout(() => reject(new Error(message)), ms);
    }),
  ]);
}

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
  const registration = await withTimeout(
    navigator.serviceWorker.register("/sw.js"),
    5000,
    "service worker 등록이 지연되고 있습니다. 잠시 후 다시 시도해주세요.",
  );
  if (registration.active) {
    return registration;
  }

  const candidate = registration.installing ?? registration.waiting;
  if (!candidate) {
    return registration;
  }

  await withTimeout(new Promise((resolve, reject) => {
    const timeoutId = window.setTimeout(() => {
      reject(new Error("service worker 준비가 지연되고 있습니다. 잠시 후 다시 시도해주세요."));
    }, 4000);

    const handleStateChange = () => {
      if (candidate.state === "activated") {
        window.clearTimeout(timeoutId);
        candidate.removeEventListener("statechange", handleStateChange);
        resolve();
      }
      if (candidate.state === "redundant") {
        window.clearTimeout(timeoutId);
        candidate.removeEventListener("statechange", handleStateChange);
        reject(new Error("service worker 활성화에 실패했습니다."));
      }
    };

    candidate.addEventListener("statechange", handleStateChange);
    handleStateChange();
  }), 5000, "service worker 준비가 지연되고 있습니다. 잠시 후 다시 시도해주세요.");

  return registration;
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
  return data?.data?.publicKey?.trim?.() ?? "";
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
  let subscription = await withTimeout(
    registration.pushManager.getSubscription(),
    5000,
    "브라우저 푸시 구독 상태 확인이 지연되고 있습니다. 잠시 후 다시 시도해주세요.",
  );
  if (!subscription) {
    subscription = await withTimeout(
      registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(publicKey),
      }),
      5000,
      "브라우저 푸시 구독 생성이 지연되고 있습니다. 새로고침 후 다시 시도해주세요.",
    );
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
