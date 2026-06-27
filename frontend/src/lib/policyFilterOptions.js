export const POLICY_CATEGORIES = [
  { label: "전체", value: "" },
  { label: "주거", value: "주거" },
  { label: "일자리", value: "일자리" },
  { label: "교육·직업훈련", value: "교육·직업훈련" },
  { label: "금융·생활지원", value: "금융·생활지원" },
  { label: "문화·여가", value: "문화·여가" },
  { label: "건강·의료", value: "건강·의료" },
  { label: "가족·돌봄", value: "가족·돌봄" },
  { label: "안전·위기", value: "안전·위기" },
  { label: "참여·기회", value: "참여·기회" },
  { label: "분류없음", value: "기타" },
];

export const POLICY_INCOME_ROWS = [
  { value: "1", label: "1~2분위 (하위 20%)" },
  { value: "3", label: "3~4분위 (하위 40%)" },
  { value: "5", label: "5~6분위 (중간 40%)" },
  { value: "7", label: "7~8분위 (상위 40%)" },
  { value: "9", label: "9~10분위 (상위 20%)" },
];

export const POLICY_TARGET_GROUPS = [
  { label: "장애인", value: "장애인" },
  { label: "한부모·조손", value: "한부모·조손" },
  { label: "다문화·탈북민", value: "다문화·탈북민" },
  { label: "보훈대상자", value: "보훈대상자" },
  { label: "다자녀", value: "다자녀" },
];

export const GOV24_SOURCE_LABEL = "정부24";

export const POLICY_SOURCE_OPTIONS = ["전체", "온통청년", "복지로 중앙", "복지로 지자체", GOV24_SOURCE_LABEL];

export const POLICY_SOURCE_TYPE_MAP = {
  "온통청년": "YOUTH",
  "복지로 중앙": "BOKJIRO_CENTRAL",
  "복지로 지자체": "BOKJIRO_LOCAL",
  [GOV24_SOURCE_LABEL]: "GOV24",
};

export const GOV24_SERVICE_FIELDS = [
  "전체",
  "생활안정",
  "농림축산어업",
  "보육·교육",
  "보건·의료",
  "임신·출산",
  "고용·창업",
  "문화·환경",
  "보호·돌봄",
  "행정·안전",
  "주거·자립",
];

export const GOV24_USER_TYPES = [
  "전체",
  "개인",
  "가구",
  "법인/시설/단체",
  "소상공인",
];

export const GOV24_BENEFIT_TYPES = [
  "전체",
  "현금",
  "현물",
  "기타",
  "현금(감면)",
  "이용권",
  "서비스(의료)",
  "시설이용",
  "기타(교육)",
  "현금(보험)",
  "현금(장학금)",
  "현금(융자)",
  "기타(상담)",
  "서비스(돌봄)",
  "서비스(일자리)",
  "의료지원",
  "상담/법률지원",
  "기술지원",
  "문화/여가지원",
  "민원",
  "봉사/기부",
];

export const POLICY_SORT_MAP = {
  relevance: "RELEVANCE",
  views: "VIEWS",
  latest: "LATEST",
  deadline: "DEADLINE",
};

export const POLICY_STATUS_FILTER_MAP = {
  신청가능: "ACTIVE_ONLY",
  마감: "EXPIRED_ONLY",
  전부표기: "ALL",
};
