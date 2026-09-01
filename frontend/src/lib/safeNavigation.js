const MAX_INTERNAL_PATH_LENGTH = 2048;
const TRANSIENT_ROUTE_STATE_KEYS = new Set([
  "coachPolicyId",
  "recommendationLogId",
]);
const CHAT_RETURN_TRANSIENT_ROUTE_STATE_KEYS = new Set(["coachPolicyId"]);
const POST_LOGIN_ACTION_TYPES = new Set(["toggle-bookmark"]);

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

export function resolveSafeRouteTarget(value, depth = 0) {
  const origin = typeof window !== "undefined" && window.location?.origin
    ? window.location.origin
    : "http://localhost";

  if (typeof value === "string") {
    const path = resolveSafeInternalPath(value);
    if (!path) {
      return null;
    }
    const parsed = new URL(path, origin);
    return {
      path: `${parsed.pathname}${parsed.search}${parsed.hash}`,
      pathname: parsed.pathname,
      search: parsed.search,
      hash: parsed.hash,
      state: undefined,
    };
  }

  if (!value || typeof value !== "object" || typeof value.pathname !== "string") {
    return null;
  }

  const search = typeof value.search === "string" ? value.search : "";
  const hash = typeof value.hash === "string" ? value.hash : "";
  if ((search && !search.startsWith("?")) || (hash && !hash.startsWith("#"))) {
    return null;
  }

  const path = resolveSafeInternalPath(`${value.pathname}${search}${hash}`);
  if (!path) {
    return null;
  }

  const parsed = new URL(path, origin);
  return {
    path: `${parsed.pathname}${parsed.search}${parsed.hash}`,
    pathname: parsed.pathname,
    search: parsed.search,
    hash: parsed.hash,
    state: sanitizeTransientRouteState(value.state, depth, resolveStateSanitizeOptionsForPath(parsed.pathname)),
  };
}

export function sanitizePostLoginAction(action) {
  if (!action || typeof action !== "object") {
    return undefined;
  }

  const type = typeof action.type === "string" ? action.type.trim() : "";
  const policyId = Number(action.policyId);
  if (!POST_LOGIN_ACTION_TYPES.has(type) || !Number.isSafeInteger(policyId) || policyId <= 0) {
    return undefined;
  }

  return { type, policyId };
}

const sanitizeCoachPolicyId = (value) => {
  const policyId = Number(value);
  return Number.isSafeInteger(policyId) && policyId > 0 ? policyId : undefined;
};

const resolveStateSanitizeOptionsForPath = (pathname) => {
  return pathname === "/chat"
    ? { preserveTransientKeys: CHAT_RETURN_TRANSIENT_ROUTE_STATE_KEYS }
    : {};
};

export function sanitizeTransientRouteState(state, depth = 0, options = {}) {
  if (!state || typeof state !== "object") {
    return undefined;
  }

  const nextState = { ...state };
  TRANSIENT_ROUTE_STATE_KEYS.forEach((key) => {
    if (options.preserveTransientKeys?.has(key)) {
      if (key === "coachPolicyId") {
        const coachPolicyId = sanitizeCoachPolicyId(nextState[key]);
        if (coachPolicyId) {
          nextState[key] = coachPolicyId;
          return;
        }
      }
    }
    delete nextState[key];
  });

  if ("postLoginAction" in nextState) {
    const postLoginAction = sanitizePostLoginAction(nextState.postLoginAction);
    if (postLoginAction) {
      nextState.postLoginAction = postLoginAction;
    } else {
      delete nextState.postLoginAction;
    }
  }

  ["from", "chatFrom"].forEach((key) => {
    if (depth >= 3) {
      delete nextState[key];
      return;
    }
    const target = resolveSafeRouteTarget(nextState[key], depth + 1);
    if (target) {
      nextState[key] = {
        pathname: target.pathname,
        search: target.search,
        hash: target.hash,
        state: target.state,
      };
    } else {
      delete nextState[key];
    }
  });
  return Object.keys(nextState).length ? nextState : undefined;
}

export function buildSafeReturnLocation(location) {
  if (!location || typeof location !== "object" || typeof location.pathname !== "string") {
    return undefined;
  }

  return {
    pathname: location.pathname,
    search: typeof location.search === "string" ? location.search : "",
    hash: typeof location.hash === "string" ? location.hash : "",
    state: sanitizeTransientRouteState(
      location.state,
      0,
      resolveStateSanitizeOptionsForPath(location.pathname),
    ),
  };
}
