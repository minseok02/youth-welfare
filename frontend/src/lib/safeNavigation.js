const MAX_INTERNAL_PATH_LENGTH = 2048;

const hasControlCharacter = (value) => {
  for (let index = 0; index < value.length; index += 1) {
    const code = value.charCodeAt(index);
    if (code <= 31 || code === 127) {
      return true;
    }
  }
  return false;
};

export function resolveSafeInternalPath(value) {
  if (typeof value !== "string") {
    return null;
  }

  const trimmed = value.trim();
  if (!trimmed || trimmed.length > MAX_INTERNAL_PATH_LENGTH || hasControlCharacter(trimmed)) {
    return null;
  }

  if (trimmed.startsWith("//")) {
    return null;
  }

  const origin = typeof window !== "undefined" && window.location?.origin
    ? window.location.origin
    : "http://localhost";

  try {
    const parsed = new URL(trimmed, origin);
    if (parsed.origin !== origin) {
      return null;
    }
    return `${parsed.pathname}${parsed.search}${parsed.hash}`;
  } catch {
    return null;
  }
}
