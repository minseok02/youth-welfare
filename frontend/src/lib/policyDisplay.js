import { POLICY_SOURCE_TYPE_MAP } from "./policyFilterOptions.js";

const HTML_ENTITIES = {
  "&amp;": "&",
  "&lt;": "<",
  "&gt;": ">",
  "&quot;": '"',
  "&#39;": "'",
  "&nbsp;": " ",
  "&middot;": "·",
  "&bull;": "•",
  "&ndash;": "–",
  "&mdash;": "—",
  "&laquo;": "«",
  "&raquo;": "»",
  "&times;": "×",
};

const EXTERNAL_URL_PROTOCOLS = new Set(["http:", "https:"]);
const SOURCE_LABEL_BY_TYPE = Object.fromEntries(
  Object.entries(POLICY_SOURCE_TYPE_MAP).map(([label, type]) => [type, label]),
);

export function uniqueNonBlank(values) {
  const seen = new Set();
  return (values ?? [])
    .map((value) => (typeof value === "string" ? value.trim() : ""))
    .filter((value) => {
      if (!value || seen.has(value)) return false;
      seen.add(value);
      return true;
    });
}

export function splitMultiValue(value) {
  const normalized = typeof value === "string" ? value.trim() : "";
  return normalized ? uniqueNonBlank(normalized.split("||")) : [];
}

export function formatMultiValueText(value, separator = " · ") {
  const parts = splitMultiValue(value);
  return parts.length > 0 ? parts.join(separator) : null;
}

export function splitGov24MultiLabel(label) {
  return splitMultiValue(label);
}

export function decodeHtml(text) {
  if (!text) return text;
  return text.replace(/&[a-zA-Z0-9#]+;/g, (entity) => HTML_ENTITIES[entity] ?? entity);
}

export function decodeDisplayText(text) {
  const decoded = decodeHtml(text);
  if (typeof decoded !== "string") return decoded;
  return decoded.replace(/\s*\|\|\s*/g, " · ");
}

export function normalizeSafeExternalUrl(value) {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  if (!trimmed) return null;
  const candidate = /^https?:\/\//i.test(trimmed)
    ? trimmed
    : trimmed.startsWith("www.")
      ? `https://${trimmed}`
      : trimmed;

  try {
    const parsed = new URL(candidate);
    return EXTERNAL_URL_PROTOCOLS.has(parsed.protocol) ? parsed.toString() : null;
  } catch {
    return null;
  }
}

export function formatPolicyDate(value) {
  if (!value) return null;
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return null;
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, "0")}.${String(date.getDate()).padStart(2, "0")}`;
}

export function formatPolicyPeriod(start, end) {
  const formattedStart = formatPolicyDate(start);
  const formattedEnd = formatPolicyDate(end);
  if (formattedStart && formattedEnd) return `${formattedStart} ~ ${formattedEnd}`;
  if (formattedStart) return `${formattedStart} ~`;
  if (formattedEnd) return `~ ${formattedEnd}`;
  return null;
}

export function formatPolicyAgeRange(min, max) {
  if (min && max) return `만 ${min}~${max}세`;
  if (min) return `만 ${min}세 이상`;
  if (max) return `만 ${max}세 이하`;
  return null;
}

export function formatPolicyIncomeRange(min, max) {
  if (min && max) return `소득 ${min}~${max}분위`;
  if (min) return `소득 ${min}분위 이상`;
  if (max) return `소득 ${max}분위 이하`;
  return null;
}

export function joinMetaParts(parts, separator = " · ") {
  const seen = new Set();
  return parts
    .map((part) => (typeof part === "string" ? part.trim() : part))
    .filter((part) => {
      if (!part || seen.has(part)) return false;
      seen.add(part);
      return true;
    })
    .join(separator);
}

export function formatPolicySource(sourceType) {
  return SOURCE_LABEL_BY_TYPE[sourceType] || sourceType || "출처 정보 없음";
}

export function formatPolicyStatusLabel(status, applyEndDate, now = new Date()) {
  if (status === "CLOSED") return "종료";
  if (applyEndDate) {
    const today = new Date(now);
    today.setHours(0, 0, 0, 0);
    const endDate = new Date(`${applyEndDate}T00:00:00`);
    if (!Number.isNaN(endDate.getTime()) && endDate < today) return "종료";
  }
  if (status === "ACTIVE") return "진행중";
  if (status === "UPCOMING") return "예정";
  return "상태 정보 없음";
}

export function formatPolicyListStatusLabel(status) {
  if (status === "ACTIVE") return "진행중";
  if (status === "UPCOMING") return "예정";
  if (status === "CLOSED") return "종료";
  return "상시";
}

export function formatPolicyDday(endDate, status, now = new Date()) {
  if (status === "CLOSED") return "종료";
  if (!endDate) return status === "UPCOMING" ? "예정" : "상시/문의";
  const today = new Date(now);
  today.setHours(0, 0, 0, 0);
  const target = new Date(`${endDate}T00:00:00`);
  if (Number.isNaN(target.getTime())) return "상시/문의";
  const diff = Math.ceil((target - today) / 86400000);
  if (diff < 0) return "종료";
  if (diff === 0) return "D-Day";
  return `D-${diff}`;
}

export function resolveGov24FallbackLabels(primaryLabel, candidates, allowedTokens) {
  const normalizedPrimary = typeof primaryLabel === "string" ? primaryLabel.trim() : "";
  if (normalizedPrimary) return splitGov24MultiLabel(normalizedPrimary);

  const allowed = new Set(allowedTokens);
  const labels = [];
  for (const candidate of candidates) {
    const normalizedCandidate = typeof candidate === "string" ? candidate.trim() : "";
    if (allowed.has(normalizedCandidate) && !labels.includes(normalizedCandidate)) {
      labels.push(normalizedCandidate);
    }
  }

  return labels;
}

export function formatGov24LabelText(labels) {
  return labels.length > 0 ? labels.join(" · ") : null;
}
