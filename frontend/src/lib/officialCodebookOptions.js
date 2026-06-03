import api from "./axios";

export const PROFILE_STANDARD_CODEBOOK_KEYS = {
  houseTenure: "LOCAL_HOUSE_TENURE_TYPE",
  housingType: "LOCAL_HOUSING_TYPE",
  basicLivingRecipientType: "LOCAL_BASIC_LIVING_RECIPIENT_TYPE",
  disabilityGrade: "LOCAL_DISABILITY_GRADE",
};

const CODE_VALUE_KEY = "코드값";
const CODE_LABEL_KEY = "코드값의미";
const DEFAULT_LIMIT = 500;

function mapCodebookRowsToOptions(rows = []) {
  return rows
    .map((row) => ({
      value: row?.[CODE_VALUE_KEY] ?? "",
      label: row?.[CODE_LABEL_KEY] ?? row?.[CODE_VALUE_KEY] ?? "",
    }))
    .filter((option) => option.value && option.label);
}

export async function fetchOfficialCodebookOptions(codeSetKey, limit = DEFAULT_LIMIT) {
  const { data } = await api.get(`/api/reference/official-codes/${codeSetKey}`, {
    params: { limit },
  });
  return mapCodebookRowsToOptions(data?.data?.rows ?? []);
}

export async function fetchProfileStandardCodebookOptions() {
  const [
    houseTenure,
    housingType,
    basicLivingRecipientType,
    disabilityGrade,
  ] = await Promise.all([
    fetchOfficialCodebookOptions(PROFILE_STANDARD_CODEBOOK_KEYS.houseTenure),
    fetchOfficialCodebookOptions(PROFILE_STANDARD_CODEBOOK_KEYS.housingType),
    fetchOfficialCodebookOptions(PROFILE_STANDARD_CODEBOOK_KEYS.basicLivingRecipientType),
    fetchOfficialCodebookOptions(PROFILE_STANDARD_CODEBOOK_KEYS.disabilityGrade),
  ]);

  return {
    houseTenure,
    housingType,
    basicLivingRecipientType,
    disabilityGrade,
  };
}
