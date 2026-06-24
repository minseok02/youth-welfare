export const STANDARD_PROFILE_CODE_ENTRIES = [
  { key: "houseTenureCode", label: "주거형태" },
  { key: "housingTypeCode", label: "주택유형" },
  { key: "basicLivingRecipientTypeCode", label: "복지 수급 정보" },
];

export const SENSITIVE_PROFILE_CODE_ENTRY = {
  key: "disabilityGradeCode",
  label: "장애 관련 지원 정보",
};

export function resolveStandardProfileCodeCompletion(profile = {}) {
  const includeSensitive = Boolean(
    profile?.sensitiveInfoConsentAgreed
    || profile?.disabilityGradeCode
  );
  const entries = includeSensitive
    ? [...STANDARD_PROFILE_CODE_ENTRIES, SENSITIVE_PROFILE_CODE_ENTRY]
    : STANDARD_PROFILE_CODE_ENTRIES;
  const missingLabels = entries
    .filter((entry) => !profile?.[entry.key])
    .map((entry) => entry.label);

  return {
    totalCount: entries.length,
    missingCount: missingLabels.length,
    filledCount: entries.length - missingLabels.length,
    missingLabels,
    includesSensitive: includeSensitive,
  };
}
