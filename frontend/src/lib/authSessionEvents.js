const authExpiredListeners = new Set();

export const subscribeAuthExpired = (listener) => {
  if (typeof listener !== "function") {
    return () => {};
  }

  authExpiredListeners.add(listener);
  return () => {
    authExpiredListeners.delete(listener);
  };
};

export const notifyAuthExpired = (event = {}) => {
  const payload = {
    reason: event.reason || "expired",
    from: event.from,
  };

  for (const listener of [...authExpiredListeners]) {
    listener(payload);
  }
};

export const clearAuthSessionEventListenersForTest = () => {
  authExpiredListeners.clear();
};
