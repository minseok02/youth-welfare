const MAX_UNSUBSCRIBE_TOKEN_LENGTH = 2048;

const hasControlCharacter = (value) => {
  for (let index = 0; index < value.length; index += 1) {
    const code = value.charCodeAt(index);
    if (code <= 31 || code === 127) {
      return true;
    }
  }
  return false;
};

export function readNotificationUnsubscribeTokenFromHash(hash) {
  if (typeof hash !== "string" || !hash) {
    return "";
  }

  const normalizedHash = hash.startsWith("#") ? hash.slice(1) : hash;
  if (!normalizedHash || normalizedHash.startsWith("?")) {
    return "";
  }

  const token = new URLSearchParams(normalizedHash).get("token")?.trim() ?? "";
  if (!token || token.length > MAX_UNSUBSCRIBE_TOKEN_LENGTH || hasControlCharacter(token)) {
    return "";
  }

  return token;
}

export function hasNotificationUnsubscribeTokenInUrl({ search = "", hash = "" } = {}) {
  if (typeof hash === "string" && new URLSearchParams(hash.startsWith("#") ? hash.slice(1) : hash).has("token")) {
    return true;
  }
  if (typeof search === "string" && new URLSearchParams(search.startsWith("?") ? search.slice(1) : search).has("token")) {
    return true;
  }
  return false;
}
