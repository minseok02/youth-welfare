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
  const keyBytes = urlBase64ToUint8Array(publicKey);
  if (keyBytes.length !== 65 || keyBytes[0] !== 0x04) return false;
  if (window.crypto?.subtle?.importKey) {
    await withTimeout(
      window.crypto.subtle.importKey(
        "raw",
        keyBytes,
        { name: "ECDH", namedCurve: "P-256" },
        false,
        [],
      ),
      3000,
      "웹푸시 공개키 검증이 지연되고 있습니다.",
    );
  }
  return true;
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
  const registration = await withTimeout(
    navigator.serviceWorker.getRegistration(),
    3000,
    "현재 브라우저 service worker 조회가 지연되고 있습니다.",
  );
  if (!registration) return null;
  return withTimeout(
    registration.pushManager.getSubscription(),
    3000,
    "현재 브라우저 푸시 구독 조회가 지연되고 있습니다.",
  );
}

export async function fetchPushPublicKey() {
  const { data } = await api.get("/api/notifications/push-public-key");
  return data?.data?.publicKey?.trim?.() ?? "";
}

export async function fetchMyPushSubscriptions() {
  const { data } = await api.get("/api/notifications/push-subscriptions/me");
  return Array.isArray(data?.data) ? data.data : [];
}

export async function registerCurrentBrowserPush({ publicKey, deviceLabel, onStep }) {
  onStep?.("공개키 검증 중");
  const publicKeyValid = await validateWebPushPublicKey(publicKey);
  if (!publicKeyValid) {
    throw new Error("웹푸시 공개키 형식이 올바르지 않습니다.");
  }

  onStep?.("브라우저 권한 확인 중");
  const permission = await withTimeout(
    Notification.requestPermission(),
    3000,
    "브라우저 알림 권한 확인이 지연되고 있습니다.",
  );
  if (permission !== "granted") {
    return { permission, subscription: null };
  }

  onStep?.("service worker 등록 중");
  const registration = await getReadyServiceWorkerRegistration();
  onStep?.("기존 구독 조회 중");
  let subscription = await withTimeout(
    registration.pushManager.getSubscription(),
    5000,
    "브라우저 푸시 구독 상태 확인이 지연되고 있습니다. 잠시 후 다시 시도해주세요.",
  );
  if (!subscription) {
    onStep?.("브라우저 구독 생성 중");
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
  onStep?.("서버 구독 저장 중");
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
