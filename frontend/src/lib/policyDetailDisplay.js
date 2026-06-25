import { normalizeSafeExternalUrl } from "./policyDisplay.js";

export const parsePolicyContacts = (raw) => {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) {
      return parsed
        .map((item) => ({
          name: item?.name || item?.deptNm || item?.orgNm || "",
          phone: item?.phone || item?.telNo || item?.contact || "",
        }))
        .filter((item) => item.name || item.phone);
    }
  } catch {
    return raw
      .split(/\n+/)
      .map((line) => line.trim())
      .filter(Boolean)
      .map((line) => ({ name: line, phone: "" }));
  }
  return [];
};

export const parsePolicyReferenceUrls = (raw) => {
  if (!raw) return [];

  try {
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];

    const seen = new Set();
    return parsed
      .map((item) => {
        const displayUrl = typeof item?.url === "string" ? item.url.trim() : "";
        const normalizedUrl = normalizeSafeExternalUrl(displayUrl);
        if (!normalizedUrl) return null;
        return {
          url: normalizedUrl,
          displayUrl,
          type: item?.type || "REFERENCE",
          label: item?.label || "추가 링크",
          sourceField: item?.sourceField || "",
          confidence: item?.confidence ?? null,
        };
      })
      .filter((item) => {
        if (!item || seen.has(item.url)) return false;
        seen.add(item.url);
        return true;
      });
  } catch {
    return [];
  }
};

export const appendRelatedPolicyCandidates = (bucket, items, currentId, relationLabel, limit = 3) => {
  for (const item of items ?? []) {
    const itemId = item?.id;
    if (!item || String(itemId) === String(currentId) || bucket.some((candidate) => String(candidate.id) === String(itemId))) {
      continue;
    }
    bucket.push({
      ...item,
      relationLabel,
    });
    if (bucket.length >= limit) {
      break;
    }
  }
  return bucket;
};
