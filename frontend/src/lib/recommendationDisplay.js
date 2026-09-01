const nonBlank = (value) => (value || "").trim();

const SOURCE_LABEL_BY_TYPE = {
  YOUTH: "온통청년",
  BOKJIRO_CENTRAL: "복지로 중앙",
  BOKJIRO_LOCAL: "복지로 지자체",
  GOV24: "정부24",
};

const LOW_CONFIDENCE_REASON_TOKENS = [
  "관련성 낮",
  "적합도 낮",
  "낮은 적합",
  "미흡",
  "맞지",
  "불일치",
  "제한적",
  "대상 아님",
  "조건 아님",
];

const pushUnique = (items, label, value) => {
  const normalizedValue = nonBlank(value);
  if (!normalizedValue) return;
  const key = `${label}:${normalizedValue}`;
  if (items.some((item) => item.key === key)) return;
  items.push({ key, label, value: normalizedValue });
};

const normalizeReasonFactorValue = (label, value) => {
  const normalizedValue = nonBlank(value);
  if (label === "소득" && /^(?:소득\s*)?0(?:[-~]0)?분위(?:\s*(?:이상|이하))?$/.test(normalizedValue)) {
    return "";
  }
  if (label === "출처") {
    return SOURCE_LABEL_BY_TYPE[normalizedValue] || normalizedValue;
  }
  return normalizedValue;
};

const normalizeRecommendationMemoTone = (value) => nonBlank(value)
  .replace(/보여드렸어요/g, "추천 이유입니다")
  .replace(/추천했어요/g, "추천 이유입니다")
  .replace(/했어요/g, "했습니다")
  .replace(/맞아요/g, "맞습니다");

const mapServerReasonFactors = (reasonFactors, limit) => {
  const items = [];
  (reasonFactors ?? []).forEach((factor) => {
    const label = nonBlank(factor?.label);
    const value = normalizeReasonFactorValue(label, factor?.value);
    if (!label || !value) return;
    pushUnique(items, label, value);
  });
  return items.slice(0, limit);
};

const reasonFactorValue = (rec, label) => {
  const factor = (rec?.reasonFactors ?? []).find((item) => nonBlank(item?.label) === label);
  return normalizeReasonFactorValue(label, factor?.value);
};

const compactJoin = (values, limit = 2) => {
  const unique = [];
  values.forEach((value) => {
    const trimmed = nonBlank(value);
    if (!trimmed || unique.includes(trimmed)) {
      return;
    }
    unique.push(trimmed);
  });
  return unique.slice(0, limit);
};

const isLowConfidenceReason = (reason) => {
  const normalizedReason = nonBlank(reason);
  if (!normalizedReason) return false;
  return LOW_CONFIDENCE_REASON_TOKENS.some((token) => normalizedReason.includes(token));
};

const hasSpecificEvidence = (rec, aiReason) => {
  const normalizedReason = nonBlank(aiReason);
  if (!normalizedReason) return false;
  return ["지역", "연령", "소득", "대상"].some((label) => {
    const value = reasonFactorValue(rec, label);
    return value && normalizedReason.includes(value);
  });
};

export const buildEvidenceRecommendationMemo = (rec) => {
  const region = reasonFactorValue(rec, "지역");
  const category = reasonFactorValue(rec, "분류") || (rec?.category !== "기타" ? rec?.category : "");
  const age = reasonFactorValue(rec, "연령");
  const income = reasonFactorValue(rec, "소득");
  const target = reasonFactorValue(rec, "대상");
  const detail = reasonFactorValue(rec, "세부") || rec?.youthMidLabel || rec?.gov24ServiceFieldLabel;

  if (region && category) {
    return `${region} 지역과 ${category} 조건이 주요 추천 이유입니다.`;
  }
  if (region && age) {
    return `${region} 지역과 ${age} 조건이 주요 추천 이유입니다.`;
  }
  if (category && age) {
    return `${category} 분야와 ${age} 조건이 주요 추천 이유입니다.`;
  }
  if (category && income) {
    return `${category} 분야와 ${income} 조건이 주요 추천 이유입니다.`;
  }
  if (target && category) {
    return `${target} 대상과 ${category} 분야가 주요 추천 이유입니다.`;
  }
  if (detail && category) {
    return `${category} 분야와 ${detail} 세부 조건이 주요 추천 이유입니다.`;
  }

  const firstBasis = compactJoin([region, age, income, target, category, detail], 1)[0];
  if (firstBasis) {
    return `${firstBasis} 조건이 주요 추천 이유입니다.`;
  }
  return null;
};

export const buildFallbackRecommendationMemo = (rec) => {
  const evidenceMemo = buildEvidenceRecommendationMemo(rec);
  if (evidenceMemo) {
    return evidenceMemo;
  }

  const supportLabels = compactJoin([
    rec?.youthMidLabel,
    rec?.provisionMethodLabel,
    rec?.gov24BenefitTypeLabel,
    rec?.gov24ServiceFieldLabel,
    rec?.youthMajorLabel,
    rec?.category !== "기타" ? rec?.category : "",
  ]);

  if (supportLabels.length >= 2) {
    return `${supportLabels[0]} · ${supportLabels[1]} 기준이 주요 추천 이유입니다.`;
  }
  if (supportLabels.length === 1) {
    return `${supportLabels[0]} 기준이 주요 추천 이유입니다.`;
  }

  const audienceLabels = compactJoin([
    rec?.gov24UserTypeLabel,
    rec?.source,
    rec?.sourceTypeLabel ? `${rec.sourceTypeLabel} 정책` : "",
  ], 1);

  if (audienceLabels.length === 1) {
    return `${audienceLabels[0]} 분류가 주요 추천 이유입니다.`;
  }

  return "정책 분류와 기본 자격 신호가 주요 추천 이유입니다.";
};

export function buildRecommendationMemo(rec) {
  const aiReason = nonBlank(rec?.aiReason);
  if (aiReason && !isLowConfidenceReason(aiReason) && hasSpecificEvidence(rec, aiReason)) {
    return {
      heading: "추천 이유",
      body: normalizeRecommendationMemoTone(aiReason),
      tone: "ai",
    };
  }

  return {
    heading: "추천 이유",
    body: normalizeRecommendationMemoTone(buildFallbackRecommendationMemo(rec)),
    tone: "fallback",
  };
}

export function buildRecommendationEvidenceItems(rec, limit = 4) {
  if (!rec) return [];

  const serverItems = mapServerReasonFactors(rec.reasonFactors, limit);
  if (serverItems.length > 0) {
    return serverItems;
  }

  const items = [];
  pushUnique(items, "분류", rec.category !== "기타" ? rec.category : "");
  pushUnique(items, "세부", rec.youthMidLabel || rec.gov24ServiceFieldLabel);
  pushUnique(items, "지원", rec.provisionMethodLabel || rec.gov24BenefitTypeLabel);
  pushUnique(items, "대상", rec.gov24UserTypeLabel);
  pushUnique(items, "출처", rec.sourceTypeLabel);
  pushUnique(items, "기관", rec.source);

  if (rec.aiStatus === "NOT_REQUESTED") {
    pushUnique(items, "평가", "규칙 기반");
  }

  return items.slice(0, limit);
}
