import { buildPolicyRegionDisplay } from "./policyDetailDisplay.js";
import { formatPolicyStatusLabel } from "./policyDisplay.js";

const hasValue = (value) => value !== null && value !== undefined && String(value).trim() !== "";

const parseDate = (value) => {
  if (!value) return null;
  const date = new Date(`${value}T00:00:00`);
  return Number.isNaN(date.getTime()) ? null : date;
};

const calculateAge = (birthDate, now = new Date()) => {
  const birth = parseDate(birthDate);
  if (!birth) return null;
  let age = now.getFullYear() - birth.getFullYear();
  const monthDiff = now.getMonth() - birth.getMonth();
  if (monthDiff < 0 || (monthDiff === 0 && now.getDate() < birth.getDate())) {
    age -= 1;
  }
  return age;
};

const hasConcreteIncomeCondition = (policy) => {
  const min = Number(policy?.minIncome ?? 0);
  const max = Number(policy?.maxIncome ?? 0);
  return min > 0 || max > 0;
};

const buildPolicyConditionText = (policy) => [
  policy?.title,
  policy?.description,
  policy?.supportContent,
  policy?.targetDetail,
  policy?.selectionCriteria,
  policy?.supportDetail,
  policy?.youthMaritalStatusLabel,
  policy?.youthIncomeConditionTypeLabel,
  policy?.youthMidLabel,
  ...(policy?.tags ?? []).map((tag) => tag?.tagValue),
]
  .filter(Boolean)
  .join(" ")
  .replace(/\s+/g, " ");

const hasAnyTextSignal = (text, signals) => signals.some((signal) => text.includes(signal));

const policyRegionMatchesProfile = (policy, profile) => {
  const profileSido = profile?.sido?.trim();
  if (!profileSido) return null;

  const regionDisplay = buildPolicyRegionDisplay(policy);
  const labels = regionDisplay.detailLabels.length
    ? regionDisplay.detailLabels
    : [regionDisplay.compactLabel].filter(Boolean);
  if (!labels.length) return true;

  const profileSgg = profile?.sgg?.trim();
  const matched = labels.some((label) => {
    if (!label.startsWith(profileSido)) return false;
    if (!profileSgg) return true;
    return label === profileSido || label.startsWith(`${profileSido} ${profileSgg}`);
  });
  if (matched) return true;
  if (regionDisplay.broadRegion && regionDisplay.compactLabel === profileSido) {
    return "partial";
  }
  return false;
};

const createItem = (key, label, status, message) => ({ key, label, status, message });

export function resolvePolicyEligibilityPrecheck(policy, profile, now = new Date()) {
  if (!policy) {
    return null;
  }

  const items = [];
  const missingProfileFields = [];

  const statusLabel = formatPolicyStatusLabel(policy.status, policy.applyEndDate, now);
  if (statusLabel === "종료") {
    items.push(createItem("period", "신청기간", "error", "현재 기준 종료된 정책으로 보입니다."));
  } else if (statusLabel === "예정") {
    items.push(createItem("period", "신청기간", "warning", "아직 신청 예정 상태입니다. 시작일을 원문에서 확인하세요."));
  } else {
    items.push(createItem("period", "신청기간", "success", "현재 진행 중이거나 상시/문의 정책으로 보입니다."));
  }

  const age = calculateAge(profile?.birthDate, now);
  if (policy.minAge || policy.maxAge) {
    if (age === null) {
      missingProfileFields.push("생년월일");
      items.push(createItem("age", "나이", "warning", "생년월일을 입력하면 연령 조건을 비교할 수 있습니다."));
    } else if ((policy.minAge && age < policy.minAge) || (policy.maxAge && age > policy.maxAge)) {
      items.push(createItem("age", "나이", "error", `현재 만 ${age}세로 정책 연령 조건과 다를 수 있습니다.`));
    } else {
      items.push(createItem("age", "나이", "success", `현재 만 ${age}세로 연령 조건 범위에 들어갑니다.`));
    }
  } else {
    items.push(createItem("age", "나이", "info", "명확한 연령 조건이 없어 원문 확인이 필요합니다."));
  }

  const regionMatch = policyRegionMatchesProfile(policy, profile);
  if (regionMatch === null && buildPolicyRegionDisplay(policy).compactLabel) {
    missingProfileFields.push("거주지역");
    items.push(createItem("region", "지역", "warning", "거주지역을 입력하면 지원 지역과 비교할 수 있습니다."));
  } else if (regionMatch === false) {
    items.push(createItem("region", "지역", "error", "내 거주지역과 정책 지원지역이 다를 수 있습니다."));
  } else if (regionMatch === "partial") {
    items.push(createItem("region", "지역", "warning", "내 시도는 지원지역과 같지만 상세 시군구 목록과 다를 수 있습니다. 원문 또는 상세 지역 목록에서 내 지역 포함 여부를 확인하세요."));
  } else if (regionMatch === true) {
    items.push(createItem("region", "지역", "success", "내 거주지역이 정책 지원지역에 포함되는 것으로 보입니다."));
  } else {
    items.push(createItem("region", "지역", "info", "지원 지역이 전국이거나 원문 확인 대상입니다."));
  }

  if (hasConcreteIncomeCondition(policy)) {
    if (!hasValue(profile?.incomeLevel)) {
      missingProfileFields.push("소득수준");
      items.push(createItem("income", "소득", "warning", "소득수준을 입력하면 소득 조건을 비교할 수 있습니다."));
    } else if ((policy.minIncome && profile.incomeLevel < policy.minIncome) || (policy.maxIncome && profile.incomeLevel > policy.maxIncome)) {
      items.push(createItem("income", "소득", "error", "입력한 소득수준이 정책 소득 조건과 다를 수 있습니다."));
    } else {
      items.push(createItem("income", "소득", "success", "입력한 소득수준이 정책 소득 조건 범위에 들어갑니다."));
    }
  } else {
    items.push(createItem("income", "소득", "info", "명확한 소득 분위 조건은 원문 확인이 필요합니다."));
  }

  const conditionText = buildPolicyConditionText(policy);
  if (policy.youthMaritalStatusLabel === "미혼" || hasAnyTextSignal(conditionText, ["미혼", "혼인"])) {
    items.push(createItem("marital", "혼인", "warning", "혼인 상태 조건이 있는 정책입니다. 서비스 프로필에는 혼인 상태가 없어 원문 기준으로 직접 확인해야 합니다."));
  }

  if (hasAnyTextSignal(conditionText, ["무주택", "주택 미소유", "주택소유"])) {
    items.push(createItem("homeless", "무주택", "warning", "무주택 또는 주택 소유 관련 조건이 보입니다. 부모님 명의 집에 거주하는 것만으로 본인 자가로 보진 않는 편이 안전하지만, 세대주/가구원 기준은 원문에서 확인해야 합니다."));
  }

  if (hasAnyTextSignal(conditionText, ["주거급여", "기초생활", "수급", "차상위"])) {
    if (!hasValue(profile?.basicLivingRecipientTypeCode)) {
      missingProfileFields.push("복지 수급 정보");
      items.push(createItem("recipient", "수급", "warning", "주거급여 또는 수급 관련 조건이 보입니다. 복지 수급 정보를 입력하면 추천 품질이 좋아지고, 최종 대상 여부는 원문에서 확인해야 합니다."));
    } else {
      items.push(createItem("recipient", "수급", "info", "입력한 수급 정보가 있습니다. 세부 수급 종류와 가구 기준은 원문에서 다시 확인하세요."));
    }
  }

  if (hasAnyTextSignal(conditionText, ["월세", "전세", "임차", "임대", "주거"])) {
    if (!hasValue(profile?.houseTenureCode)) {
      missingProfileFields.push("주거형태");
      items.push(createItem("housing", "주거", "warning", "주거 형태와 관련된 조건이 보입니다. 자가는 본인 또는 배우자 명의 주택 거주 기준으로 입력하고, 부모님 집 거주는 원문 세대 기준을 함께 확인하세요."));
    } else {
      items.push(createItem("housing", "주거", "info", "입력한 주거형태가 있습니다. 자가 여부는 본인 또는 배우자 명의 주택 기준으로 보고, 부모님 집 거주와 무주택세대 기준은 원문에서 다시 확인하세요."));
    }
  }

  const hasError = items.some((item) => item.status === "error");
  const hasWarning = items.some((item) => item.status === "warning");
  return {
    status: hasError ? "error" : hasWarning ? "warning" : "success",
    title: hasError ? "조건이 다를 수 있어요" : hasWarning ? "입력 보완이 필요해요" : "기본 조건은 맞아 보여요",
    description: "입력한 프로필과 수집된 정책 정보를 비교한 사전점검입니다. 최종 자격과 서류는 원문 공고와 운영기관 기준이 우선입니다.",
    items,
    missingProfileFields: [...new Set(missingProfileFields)],
  };
}
