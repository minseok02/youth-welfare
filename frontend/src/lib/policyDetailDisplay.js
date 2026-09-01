import { normalizeSafeExternalUrl } from "./policyDisplay.js";

const BROAD_REGION_MIN_COUNT = 8;
const REGION_DETAIL_PREVIEW_COUNT = 8;

export const normalizePolicyRegionLabel = (value) => {
  const normalized = (value ?? "").trim();
  if (!normalized || /^\d+$/.test(normalized)) {
    return "";
  }
  return normalized;
};

export const resolvePolicyTopLevelRegion = (label) => normalizePolicyRegionLabel(label).split(/\s+/)[0] ?? "";

export const buildPolicyRegionDisplay = (policy) => {
  const labels = [...new Set((policy?.regions ?? []).map(normalizePolicyRegionLabel).filter(Boolean))];
  const topLevelLabels = [...new Set(labels.map(resolvePolicyTopLevelRegion).filter(Boolean))];
  const fallback = normalizePolicyRegionLabel(policy?.regionLabel)
    || normalizePolicyRegionLabel(policy?.sido)
    || "";

  if (!labels.length) {
    return {
      compactLabel: fallback,
      detailLabels: [],
      hiddenDetailCount: 0,
      broadRegion: false,
    };
  }

  if (topLevelLabels.length === 1 && labels.length >= BROAD_REGION_MIN_COUNT) {
    return {
      compactLabel: topLevelLabels[0],
      detailLabels: labels,
      hiddenDetailCount: Math.max(0, labels.length - REGION_DETAIL_PREVIEW_COUNT),
      broadRegion: true,
    };
  }

  if (labels.length === 1) {
    return {
      compactLabel: labels[0],
      detailLabels: labels,
      hiddenDetailCount: 0,
      broadRegion: false,
    };
  }

  return {
    compactLabel: fallback || labels.slice(0, 2).join(", "),
    detailLabels: labels,
    hiddenDetailCount: Math.max(0, labels.length - REGION_DETAIL_PREVIEW_COUNT),
    broadRegion: false,
  };
};

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
