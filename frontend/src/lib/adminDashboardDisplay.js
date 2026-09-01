const SOURCE_TYPE_LABELS = {
  YOUTH: "온통청년",
  BOKJIRO_CENTRAL: "복지로 중앙",
  BOKJIRO_LOCAL: "복지로 지자체",
  GOV24: "정부24",
  xlsx: "엑셀 파일",
  txt: "텍스트 파일",
};

const ACTOR_TYPE_LABELS = {
  USER: "회원",
  ANONYMOUS: "비회원",
  GUEST: "게스트",
  SYSTEM: "시스템",
};

const SEARCH_STATUS_FILTER_LABELS = {
  ALL: "전체",
  ACTIVE_ONLY: "진행중만",
  ACTIVE_AND_UPCOMING: "진행중+예정",
  UPCOMING_ONLY: "예정만",
  CLOSED_ONLY: "마감만",
};

const SEARCH_SORT_KEY_LABELS = {
  DEFAULT: "기본 정렬",
  RELEVANCE: "관련도순",
  DEADLINE_ASC: "마감임박순",
  DEADLINE_DESC: "마감여유순",
  LATEST: "최신순",
  POPULAR: "인기순",
};

const POLICY_LINK_REVIEW_BUCKET_LABELS = {
  announcement_recruitment: "공고/모집형",
  benefit_support: "지원금/급부형",
  program_event: "프로그램형",
  event_culture: "행사/문화형",
  other: "기타",
};

const POLICY_DUPLICATE_REVIEW_CLASS_LABELS = {
  exact_duplicate_candidate: "완전 중복 후보",
  mirror_or_channel_variant_candidate: "채널만 다른 중복 후보",
  date_or_contract_drift_candidate: "기간/조건 차이 확인",
  title_only_false_positive_risk: "제목만 같아 주의",
};

const POLICY_CORRECTION_SCOPE_LABELS = {
  REGIONS: "지역 보정",
  REGION: "지역 보정",
  TARGET_REGION: "대상 지역 보정",
  TARGET_REGIONS: "대상 지역 보정",
  APPLICATION_REGION: "신청 지역 보정",
  SERVICE_REGION: "서비스 지역 보정",
};

const POLICY_CORRECTION_TYPE_LABELS = {
  APPLICATION_PERIOD: "신청 기간 보정",
  DETAIL_URL: "상세 링크 보정",
  ELIGIBILITY: "자격 조건 보정",
  DUPLICATE_POLICY: "중복 정책 보정",
  REGION: "지역 보정",
  REGIONS: "지역 보정",
};

const CODEBOOK_METADATA_LABELS = {
  codeSetKey: "코드셋",
  codeSetName: "코드셋 이름",
  displayName: "표시명",
  name: "이름",
  code: "코드",
  label: "라벨",
  value: "값",
  description: "설명",
  meaning: "의미",
  parentCode: "상위 코드",
  sortOrder: "정렬 순서",
  sourceType: "자료 출처",
  sourceFile: "원본 파일",
  sheetName: "시트",
  headerRow: "머리글 행",
  rowCount: "전체 행",
  activeRowCount: "사용 중인 행",
  matchedRowCount: "표시 행",
  rowDataIncluded: "행 조회",
  intendedUse: "용도",
  encoding: "인코딩",
  delimiter: "구분자",
  generatedAt: "생성 시각",
  updatedAt: "갱신 시각",
};

const CODEBOOK_SET_LABELS = {
  LOCAL_HOUSE_TENURE_TYPE: "가옥(주거형태)코드",
  LOCAL_HOUSING_TYPE: "주택유형구분코드",
  LOCAL_AGENCY_CODES: "행정기관 코드",
};

const CODEBOOK_INTENDED_USE_LABELS = {
  "user-profile housing tenure normalization": "회원 주거형태 입력값 표준화",
  "user-profile and housing policy normalization": "회원 주거 정보와 주거 정책 조건 표준화",
  "gov24 agency normalization crosswalk": "정부24 기관 코드 매핑/표준화",
};

const STATUS_LABELS = {
  ALL_TIME_LATEST_PER_USER: "전체 기간 사용자별 최신 추천 기준",
  READY_REAL_USER_TRAFFIC: "실사용자 이용 데이터 충분",
  READY_REAL_USER_COHORT: "실사용자 그룹 확보",
  BALANCED_ENOUGH_FOR_LOGIC_REVIEW: "추천 로직 검토 가능",
  CONCENTRATED_TOP1: "1순위 집중 상태",
  NO_PRIORITY_DOMINANT: "무우선순위 편중 상태",
  EMPTY_TOP1_LEADER: "1순위 선두 없음",
  EXAMPLE_SMOKE_ONLY_LEADER: "예제 스모크만 선두",
  BOUNDED_LOCAL_WITH_EXAMPLE_LEADER: "로컬 제한군과 예제가 선두",
  LOCAL_SEED_WITHOUT_REAL_USER_LEADER: "로컬 시드만 선두",
  REAL_USER_SIGNAL_THIN_LEADER: "1순위 실사용자 신호 부족",
  MIXED_REAL_USER_LEADER: "실사용자와 테스트 데이터 혼합 선두",
  REAL_USER_ONLY_LEADER: "실사용자만 선두",
  SYNTHETIC_ONLY_LATEST_BATCH: "합성 데이터 위주 배치",
  MIXED_WITH_NON_REAL_BATCH: "비실사용 혼합 배치",
  DEFERRED_EMPTY_COHORT: "추천 데이터 없음",
  DEFERRED_EMPTY_RECENT_WINDOW: "최근 추천 데이터 없음",
  DEFERRED_NO_REAL_USER_TRAFFIC: "실사용자 이용 데이터 없음",
  DEFERRED_REAL_USER_SAMPLE_THIN: "실사용자 표본 부족",
  DEFERRED_REAL_USER_CLICK_SAMPLE_THIN: "실사용자 클릭 표본 부족",
  DEFERRED_NON_REAL_LEADER_SIGNAL: "테스트 데이터 영향으로 검토 보류",
  DEFERRED_REAL_USER_LEADER_SIGNAL_THIN: "1순위 실사용자 신호 부족",
  DEFERRED_NO_REAL_USER_RECENT_WINDOW: "최근 실사용자 데이터 부족",
  RECENT_WINDOW_STILL_TARGET_DOMINANT: "최근에도 기존 대상 서비스 편중",
  RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE: "최근 데이터에서 기존 편중 해소",
  RECENT_WINDOW_INCONCLUSIVE: "최근 데이터 판단 보류",
  RECENT_WINDOW_POLICY_CANDIDATE: "최근 기준 전환 검토 대상",
  NOT_A_CANDIDATE_NO_HISTORICAL_EXAMPLE_DOMINANCE: "예제 지배 이력 없음",
  NOT_A_CANDIDATE_PRIMARY_GATE_NOT_NON_REAL_BLOCKED: "현재 상태 기준 후보 아님",
  NOT_A_CANDIDATE_PRIMARY_REFERENCE_NOT_ALL_TIME_LATEST: "전체 기간 기준이 아니어서 후보 아님",
  NOT_A_CANDIDATE_TARGET_STILL_PRESENT_IN_RECENT_EXAMPLE_WINDOW: "최근 예제 구간에 대상 서비스가 남아 있음",
  NOT_A_CANDIDATE_NO_REAL_USER_RECENT_LATEST_USERS: "최근 실사용자 최신 추천 없음",
  NOT_A_CANDIDATE_RECENT_WINDOW_NOT_CLEAR: "최근 데이터로 편중 해소 확인 안 됨",
  KEEP_PRIMARY_BASELINE: "기본 기준 유지",
  REQUIRES_EXPLICIT_POLICY_CHANGE_REVIEW: "명시적 정책 변경 검토 필요",
  PROMOTION_READY: "전환 준비됨",
  RUN_BOUNDED_PROMOTION_REVIEW: "제한 범위 검토 실행",
  AWAIT_EXPLICIT_POLICY_REVIEW_DECISION: "명시적 정책 검토 결정 대기",
  NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW: "제한 승격 검토 미준비",
  READY_FOR_BOUNDED_PROMOTION_REVIEW: "제한 승격 검토 준비됨",
  DO_NOT_RUN_BOUNDED_PROMOTION_REVIEW: "제한 승격 검토 실행 안 함",
  NOT_READY_FOR_EXPLICIT_PROMOTION_APPROVAL: "명시적 승격 승인 미준비",
  READY_FOR_EXPLICIT_PROMOTION_APPROVAL: "명시적 승격 승인 준비됨",
  PENDING_EXPLICIT_PROMOTION_APPROVAL: "명시적 승격 승인 대기",
  BOUNDED_PROMOTION_REVIEW_APPROVED: "제한 승격 검토 승인됨",
  PROMOTION_APPROVAL_NOT_APPLICABLE: "승격 승인 대상 아님",
  APPROVAL_DECISION_NOT_READY: "승인 결정 미준비",
  AWAIT_EXPLICIT_PROMOTION_APPROVAL_DECISION: "명시적 승인 결정 대기",
  APPROVED_FOR_BOUNDED_PROMOTION_REVIEW: "제한 승격 검토 승인",
  APPROVAL_DECISION_NOT_APPLICABLE: "승인 결정 대상 아님",
  APPROVAL_RECORD_NOT_READY: "승인 기록 미준비",
  PENDING_EXPLICIT_PROMOTION_APPROVAL_RECORD: "명시적 승인 기록 대기",
  EXPLICIT_PROMOTION_APPROVAL_RECORDED: "명시적 승인 기록 완료",
  APPROVAL_RECORD_NOT_APPLICABLE: "승인 기록 대상 아님",
  BOUNDED_PROMOTION_REVIEW_RUN_NOT_READY: "제한 검토 실행 미준비",
  PENDING_BOUNDED_PROMOTION_REVIEW_RUN: "제한 검토 실행 대기",
  AWAIT_BOUNDED_PROMOTION_REVIEW_RUN: "제한 검토 실행 승인 대기",
  BOUNDED_PROMOTION_REVIEW_RUN_NOT_APPLICABLE: "제한 검토 실행 대상 아님",
  NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN: "제한 검토 실행 기준 미충족",
  READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN: "제한 검토 실행 기준 충족",
  BOUNDED_PROMOTION_REVIEW_RUN_DECISION_NOT_READY: "제한 검토 실행 결정 미준비",
  AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_DECISION: "제한 검토 실행 결정 대기",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVED: "제한 검토 실행 승인됨",
  BOUNDED_PROMOTION_REVIEW_RUN_DECISION_NOT_APPLICABLE: "제한 검토 실행 결정 대상 아님",
  NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL: "제한 검토 실행 승인 미준비",
  READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL: "제한 검토 실행 승인 준비됨",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_DECISION_NOT_READY: "제한 검토 실행 승인 결정 미준비",
  AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_DECISION: "제한 검토 실행 승인 결정 대기",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_APPROVED: "제한 검토 실행 승인됨",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_DECISION_NOT_APPLICABLE: "제한 검토 실행 승인 결정 대상 아님",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_NOT_READY: "제한 검토 실행 승인 기록 미준비",
  PENDING_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL: "제한 검토 실행 승인 대기",
  APPROVED_FOR_BOUNDED_PROMOTION_REVIEW_RUN: "제한 검토 실행 승인",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_NOT_APPLICABLE: "제한 검토 실행 승인 대상 아님",
  NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD: "제한 검토 실행 승인 레코드 미준비",
  READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD: "제한 검토 실행 승인 기록 준비됨",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_NOT_READY: "제한 검토 실행 승인 레코드 없음",
  PENDING_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD: "제한 검토 실행 승인 기록 대기",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORDED: "제한 검토 실행 승인 기록 완료",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_NOT_APPLICABLE: "제한 검토 실행 승인 기록 대상 아님",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_TRANSITION_NOT_READY: "제한 검토 실행 전이 미준비",
  AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITE: "제한 검토 실행 승인 기록 쓰기 대기",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITTEN: "제한 검토 실행 승인 기록 작성됨",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_TRANSITION_NOT_APPLICABLE: "제한 검토 실행 전이 대상 아님",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITE_NOT_READY: "제한 검토 실행 기록 쓰기 미준비",
  PENDING_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITE: "제한 검토 실행 승인 기록 쓰기 대기",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITE_COMPLETED: "제한 검토 실행 승인 기록 쓰기 완료",
  BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITE_NOT_APPLICABLE: "제한 검토 실행 기록 쓰기 대상 아님",
  SCHEDULED: "예약됨",
  RUNNING: "실행 중",
  SUCCESS: "성공",
  ERROR: "오류",
  NO_CANDIDATES: "후보 없음",
  REVIEWED: "처리완료",
  SCORED: "AI 점수 반영",
  RULE_ONLY: "규칙 기반만 사용",
  CALL_FAILED: "AI 호출 실패",
  PARTIAL_MISSING: "AI 일부 누락",
  NOT_REQUESTED: "AI 미요청",
  FORCE_RULE_ONLY: "규칙 기반 강제",
  INVALID_KEY: "AI 키 미설정",
  passed: "정상",
  ok: "정상",
  skipped: "건너뜀",
  failed: "실패",
  missing: "없음",
  warning: "주의",
  info: "확인",
  success: "정상",
  sent: "발송 성공",
  fanout_completed: "대상 확장 완료",
  skipped_disabled: "비활성 수신처 건너뜀",
  gateway_false: "발송 시스템 실패",
  exception: "예외 발생",
  fanout_failed: "대상 확장 실패",
  disabled: "비활성 수신처",
  web_push: "웹푸시",
  email: "이메일",
  deadline_reminder: "마감 임박 알림",
  recommendation: "추천 알림",
  notification: "알림",
  MANUAL: "수동",
  SNAPSHOT: "스냅샷",
  DETAIL: "상세",
  ENRICHMENT: "보강",
  MAINTENANCE: "유지보수",
  FAILED: "실패",
  PARTIAL_SUCCESS: "부분 성공",
  CANCELLED: "취소됨",
  OPEN: "열림",
  CLOSED: "닫힘",
  DUPLICATE_THEN_LINK_PRIORITY: "중복 우선",
  LINK_REVIEW_PRIORITY: "링크 우선",
  DRIFT_TAIL_PRIORITY: "기간/조건 차이 우선",
  LOW_BACKLOG_STEADY_STATE: "안정 상태",
  EXACT_DUPLICATE_PRIORITY: "완전 중복 우선",
};

const STATUS_TOKEN_LABELS = {
  ALL: "전체",
  TIME: "기간",
  LATEST: "최신",
  PER: "별",
  USER: "사용자",
  USERS: "사용자",
  READY: "준비됨",
  NOT: "아님",
  FOR: "대상",
  REAL: "실사용자",
  TRAFFIC: "이용 데이터",
  COHORT: "사용자 그룹",
  GATE: "상태",
  REVIEW: "검토",
  RUN: "실행",
  APPROVAL: "승인",
  APPROVED: "승인됨",
  RECORD: "기록",
  RECORDED: "기록됨",
  WRITE: "쓰기",
  WRITTEN: "작성됨",
  PENDING: "대기",
  AWAIT: "대기",
  DECISION: "결정",
  CRITERIA: "기준",
  APPLICABLE: "대상",
  PROMOTION: "승격",
  BOUNDED: "제한 범위",
  EXPLICIT: "명시적",
  POLICY: "정책",
  PRIMARY: "기본",
  BASELINE: "기준",
  RECENT: "최근",
  WINDOW: "구간",
  CANDIDATE: "후보",
  CLEAR: "해소",
  CLEARS: "해소",
  HISTORICAL: "과거",
  EXAMPLE: "예제",
  DOMINANCE: "편중",
  TARGET: "대상",
  PRESENT: "남아 있음",
  EMPTY: "없음",
  DEFERRED: "보류",
  SAMPLE: "표본",
  THIN: "부족",
  CLICK: "클릭",
  NON: "비",
  LEADER: "선두",
  SIGNAL: "신호",
  TOP1: "1순위",
  LOCAL: "로컬",
  SEED: "시드",
  MIXED: "혼합",
  ONLY: "만",
  BALANCED: "균형",
  ENOUGH: "충분",
  LOGIC: "로직",
  INCONCLUSIVE: "판단 보류",
  STALE: "오래된",
  REFERENCE: "기준",
  MODE: "방식",
  EXECUTION: "실행",
  LAYER: "단계",
  ALIGNED: "정렬됨",
  PREREQUISITES: "선행 조건",
  MET: "충족",
  SUPPORTS: "지원",
  SUPPORTED: "지원됨",
  TRANSITION: "전환",
  COMPLETED: "완료",
  IS: "임",
  ARE: "임",
  BY: "기준",
  WITH: "포함",
  WITHOUT: "없음",
  BUT: "단",
  AND: "및",
  FROM: "에서",
  STILL: "아직",
  HAS: "있음",
  HAVE: "있음",
  DETECTED: "감지됨",
  CONFIRMED: "확인됨",
  CONDITIONS: "조건",
  CHANGE: "변경",
  ACTIVE: "활성",
  EXECUTED: "실행됨",
  ALLOWS: "허용",
  CAN: "가능",
  BE: "됨",
  PROMOTED: "전환됨",
  REQUIRES: "필요",
  STATE: "상태",
  READING: "판단",
  MATRIX: "조합표",
  STATUS: "상태",
  PRECHECK: "사전 점검",
  POSITIVE: "상승",
  RULE: "규칙",
  DELTA: "변화",
  FINAL: "최종",
  SCENARIO: "시나리오",
  SCENARIOS: "시나리오",
};

const COLLECT_JOB_LABELS = {
  YOUTH: "온통청년 목록 수집",
  BOKJIRO_CENTRAL: "복지로 중앙 목록 수집",
  BOKJIRO_LOCAL: "복지로 지자체 목록 수집",
  BOKJIRO_DETAIL: "복지로 상세 수집",
  GOV24: "정부24 목록 수집",
  GOV24_DETAIL: "정부24 상세 수집",
  GOV24_SUPPORT_CONDITIONS: "정부24 지원조건 수집",
  YOUTH_DETAILS: "온통청년 상세 보강",
  BOKJIRO_DETAIL_GAP_FILL: "복지로 상세 누락 보강",
  BOKJIRO_DETAIL_REFRESH: "복지로 상세 재수집",
};

const CONFIG_LABELS = {
  Scheduler: "자동 실행 일정",
  Budget: "실행 한도",
  "List pacing": "목록 요청 간격",
  "Detail pacing": "상세 요청 간격",
  Retry: "재시도",
  "429 guard": "요청 제한 보호",
  "429 abort": "요청 제한 중단 기준",
  "Open circuit": "회로 열림 유지 시간",
  "Lock guard": "중복 실행 방지",
  "Per-source cap": "출처별 한도",
};

const ADMIN_OPERATIONAL_TEXT_REPLACEMENTS = [
  [/\bnotification\s+gateway\s+returned\s+false\b/gi, "알림 발송 시스템이 실패를 반환했습니다"],
  [/\bNO_CANDIDATES\b/g, "후보 없음"],
  [/\bRULE_ONLY\b/g, "규칙 기반만 사용"],
  [/\bCALL_FAILED\b/g, "AI 호출 실패"],
  [/\bPARTIAL_MISSING\b/g, "AI 일부 누락"],
  [/\bNOT_REQUESTED\b/g, "AI 미요청"],
  [/\bSCORED\b/g, "AI 점수 반영"],
  [/\bgateway_false\b/g, "발송 시스템 실패"],
  [/\bfanout_completed\b/g, "대상 확장 완료"],
  [/\bfanout_failed\b/g, "대상 확장 실패"],
  [/\bskipped_disabled\b/g, "비활성 수신처 건너뜀"],
  [/\bweb_push\b/g, "웹푸시"],
  [/\bdeadline_reminder\b/g, "마감 임박 알림"],
  [/\bfalse[\s_-]?positive\b/gi, "오탐"],
  [/\bexact\s*duplicate\b/gi, "완전 중복"],
  [/\bmirror\s*variant\b/gi, "채널만 다른 후보"],
  [/\bbenefit\/support\b/gi, "급부형/지원형"],
  [/\bannouncement\/recruitment\b/gi, "공고/모집형"],
  [/\bprogram\/event\b/gi, "프로그램/행사형"],
  [/표준코드\s+입력\s+backlog/gi, "선택 프로필 코드 보정 항목"],
  [/표준코드\s+입력\s+대기\s+항목/gi, "선택 프로필 코드 보정 항목"],
  [/\buser-profile\s+housing\s+tenure\s+normalization\b/gi, "회원 주거형태 입력값 표준화"],
  [/\buser-profile\s+and\s+housing\s+policy\s+normalization\b/gi, "회원 주거 정보와 주거 정책 조건 표준화"],
  [/\bgov24\s+agency\s+normalization\s+crosswalk\b/gi, "정부24 기관 코드 매핑/표준화"],
  [/\bdate\/contract\s*drift\s*tail\s*review\b/gi, "기간/조건 차이 잔여 항목 검토"],
  [/\bmissing\s+detail\s+rows\s+only\b/gi, "상세 정보가 없는 항목만"],
  [/열린\s+circuit과/gi, "열린 회로와"],
  [/bounded\s+hide\s+처리/gi, "범위 제한 숨김 처리"],
  [/\bbounded\s+hide\b/gi, "범위 제한 숨김"],
  [/충돌\s+gap을/gi, "충돌 누락/불일치를"],
  [/\bdetail\s*URL\b/gi, "상세 링크"],
  [/\bsource\s*contract\b/gi, "원천 자료 규칙"],
  [/\bsource\s*Ids?\b/gi, "원천 ID"],
  [/\bsource\s*Type\b/gi, "자료 출처"],
  [/\bsource\b/gi, "출처"],
  [/\bcurrent\s*priority\b/gi, "현재 우선순위"],
  [/\bactive\s*baseline\b/gi, "기준 요약"],
  [/\battention\s*feed\b/gi, "주의 항목 목록"],
  [/\brecommendation\s*observation\b/gi, "추천 관측"],
  [/\bops\s*observation\b/gi, "운영 관측"],
  [/\bstandard[\s_-]?codes?\b/gi, "표준코드"],
  [/\bprofile\s*row\b/gi, "상세 프로필"],
  [/\bprofile\s*only\s*gap\b/gi, "상세에만 있는 값"],
  [/\buser\s*only\s*gap\b/gi, "기본 정보에만 있는 값"],
  [/\busers\/profile\s*gap\b/gi, "기본/상세 정보 차이"],
  [/\busers\/profile\b/gi, "기본/상세 정보"],
  [/\buser-profile\b/gi, "회원 프로필"],
  [/\bhousing\s+tenure\b/gi, "주거형태"],
  [/\bhousing\s+policy\b/gi, "주거 정책"],
  [/\bagency\b/gi, "기관"],
  [/\bcrosswalk\b/gi, "매핑표"],
  [/\bsafe\s*reconcile\b/gi, "안전 보정"],
  [/\breconcile\b/gi, "값 보정"],
  [/\bsame[\s_-]?config\b/gi, "같은 설정"],
  [/\btop\s*1\b/gi, "1순위"],
  [/\btop1\b/gi, "1순위"],
  [/\breal\s*user\b/gi, "실사용자"],
  [/\bYOUTH\b/g, "온통청년"],
  [/\bBOKJIRO_LOCAL\b/g, "복지로 지자체"],
  [/\bBOKJIRO_CENTRAL\b/g, "복지로 중앙"],
  [/\bGOV24\b/g, "정부24"],
  [/\bPII\b/g, "개인정보"],
  [/\bbacklog\b/gi, "대기 항목"],
  [/\bduplicate\b/gi, "중복"],
  [/\bduplicates\b/gi, "중복"],
  [/\breview\b/gi, "검토"],
  [/\bqueue\b/gi, "대기열"],
  [/\bdrift\b/gi, "변경 차이"],
  [/\bclassification\b/gi, "분류"],
  [/\bmirror\b/gi, "채널 차이"],
  [/\bexact\b/gi, "완전 일치"],
  [/\blink\b/gi, "링크"],
  [/\bbucket\b/gi, "분류"],
  [/\bgap\b/gi, "누락/불일치"],
  [/\bbreakdown\b/gi, "상세 내역"],
  [/\btail\b/gi, "잔여 항목"],
  [/\bstale\b/gi, "오래된"],
  [/\bunread\b/gi, "미열람"],
  [/\btarget\b/gi, "대상"],
  [/\bcluster\b/gi, "묶음"],
  [/\brows\b/gi, "건"],
  [/\brow\b/gi, "건"],
  [/\bartifact\b/gi, "결과 파일"],
  [/\bwrapper\b/gi, "상위 요약"],
  [/\battention\b/gi, "주의 항목"],
  [/\bcoverage\b/gi, "입력률"],
  [/\bcohort\b/gi, "사용자 그룹"],
  [/\bcircuit\b/gi, "회로"],
  [/\bgate\b/gi, "판정 상태"],
  [/\bbaseline\b/gi, "기준"],
  [/\bpriority\b/gi, "우선순위"],
  [/\bmatrix\b/gi, "조합표"],
  [/\bprecheck\b/gi, "사전 점검"],
  [/\bdelta\b/gi, "변화"],
  [/\brule\b/gi, "규칙"],
  [/\bstatus\b/gi, "상태"],
  [/\bsummary\b/gi, "요약"],
  [/\blatest\b/gi, "최신"],
  [/\bhandoff\b/gi, "전달"],
  [/\btriage\b/gi, "분류"],
  [/\bdeeplink\b/gi, "이동 경로"],
  [/\battempts?\b/gi, "시도"],
  [/\boutcome\b/gi, "결과"],
  [/\bfanout\b/gi, "대상 확장"],
  [/\bgateway\b/gi, "발송 시스템"],
  [/\bdisabled\s*endpoint\b/gi, "비활성 수신처"],
  [/\bdisabled\b/gi, "비활성"],
  [/\bendpoint\b/gi, "수신처"],
  [/\bcount\b/gi, "건수"],
  [/\bskipped\b/gi, "건너뜀"],
  [/\bpassed\b/gi, "정상"],
  [/\bfailed\b/gi, "실패"],
  [/\bchannel\b/gi, "채널"],
  [/\bkind\b/gi, "유형"],
  [/\berrorType\b/g, "오류 유형"],
  [/\brunbook\b/gi, "운영 절차"],
  [/\bsmall[\s-]?batch\b/gi, "소량 묶음"],
  [/\bnightly\s*observation\b/gi, "야간 관측"],
  [/\bnormalization\b/gi, "정규화"],
  [/\bmetadata\b/gi, "메타데이터"],
  [/\bkeep\b/gi, "유지"],
  [/\s*->\s*/g, " → "],
  [/`([^`]+)`/g, "$1"],
];

export const humanizeAdminStatusKey = (value) => {
  if (!value) return "—";
  const tokens = String(value)
    .split("_")
    .filter(Boolean)
    .map((token) => STATUS_TOKEN_LABELS[token] ?? token);
  return tokens.join(" / ");
};

export const formatStatusLabel = (value) => STATUS_LABELS[value] ?? humanizeAdminStatusKey(value);
export const formatSourceType = (value) => SOURCE_TYPE_LABELS[value] ?? (value ? formatStatusLabel(value) : "—");
export const formatActorType = (value) => ACTOR_TYPE_LABELS[value] ?? formatStatusLabel(value);
export const formatSearchStatusFilter = (value) => SEARCH_STATUS_FILTER_LABELS[value] ?? (value ? formatStatusLabel(value) : "전체");
export const formatSortKey = (value) => SEARCH_SORT_KEY_LABELS[value] ?? (value ? formatStatusLabel(value) : "기본 정렬");
export const formatPolicyLinkReviewBucket = (value) => POLICY_LINK_REVIEW_BUCKET_LABELS[value] ?? (value ? formatStatusLabel(value) : "기타");
export const formatPolicyDuplicateReviewClass = (value) =>
  POLICY_DUPLICATE_REVIEW_CLASS_LABELS[value] ?? (value ? formatStatusLabel(value) : "분류 없음");
export const formatPolicyCorrectionScope = (value) =>
  POLICY_CORRECTION_SCOPE_LABELS[value] ?? (value ? formatStatusLabel(value) : "보정 범위 없음");
export const formatPolicyCorrectionType = (value) =>
  POLICY_CORRECTION_TYPE_LABELS[value] ?? (value ? formatStatusLabel(value) : "보정 유형 없음");
export const formatCodebookMetadataKey = (value) => CODEBOOK_METADATA_LABELS[value] ?? formatStatusLabel(value);
export const formatCodebookSetLabel = (value) => CODEBOOK_SET_LABELS[value] ?? (value ? formatStatusLabel(value) : "코드셋 없음");
export const formatCodebookIntendedUse = (value) => {
  const raw = String(value ?? "").trim();
  if (!raw) return "용도 정보 없음";
  return CODEBOOK_INTENDED_USE_LABELS[raw] ?? formatAdminOperationalText(raw, "용도 정보 없음");
};
export const formatSourceIdLabel = (value) => value ? `원천 ID ${value}` : "원천 ID 없음";
export const formatBooleanLabel = (value, trueLabel, falseLabel) => (value ? trueLabel : falseLabel);
export const formatCodeOrStatus = (value) => value ? formatStatusLabel(value) : "미분류";
export const parseRegionCorrectionCodes = (value) => (
  (value || "")
    .split(/[\s,]+/)
    .map((code) => code.trim())
    .filter(Boolean)
);
export const parseSuggestedRegionCodes = (value) => (
  Array.from(new Set(Array.from(String(value || "").matchAll(/\((\d{5})\)/g)).map((match) => match[1])))
);
export const resolveFieldCorrectionType = (reasonCode) => ({
  PERIOD_MISMATCH: "APPLICATION_PERIOD",
  BROKEN_LINK: "DETAIL_URL",
  ELIGIBILITY_MISMATCH: "ELIGIBILITY",
  DUPLICATE_POLICY: "DUPLICATE_POLICY",
}[reasonCode] ?? null);
export const formatCollectJobName = (value) => COLLECT_JOB_LABELS[value] ?? formatStatusLabel(value);
export const formatConfigLabel = (value) => CONFIG_LABELS[value] ?? value ?? "설정";
export const formatConfigValue = (value) => String(value ?? "—")
  .replace(/^max /, "최대 ")
  .replace(/ items\/run/g, "건/회")
  .replace(/ calls\/run/g, "회 호출/회")
  .replace(/ attempts/g, "회 시도")
  .replace(/ backoff/g, " 대기")
  .replace(/cooldown /g, "재개 대기 ")
  .replace(/ consecutive hits/g, "회 연속")
  .replace(/missing detail rows only/g, "상세 정보가 없는 항목만")
  .replace(/operator supplied rounds\/maxCallsPerRound/g, "관리자가 지정한 회차/회차별 호출 수")
  .replace(/central /g, "중앙 ")
  .replace(/local /g, "지자체 ")
  .replace(/lease /g, "잠금 ")
  .replace(/heartbeat /g, "상태 확인 ");

export const formatAdminOperationalText = (value, fallback = "내용 없음") => {
  const raw = String(value ?? "").trim();
  if (!raw) return fallback;

  const knownStatusLabel = STATUS_LABELS[raw];
  if (knownStatusLabel) return knownStatusLabel;

  return ADMIN_OPERATIONAL_TEXT_REPLACEMENTS.reduce(
    (text, [pattern, replacement]) => text.replace(pattern, replacement),
    redactAdminDisplayText(raw)
  )
    .replace(/\s+([,.])/g, "$1")
    .replace(/\s{2,}/g, " ")
    .trim() || fallback;
};

export const formatAdminMessage = (value) => {
  if (!value) return "에러 메시지 없음";
  return formatAdminOperationalText(String(value), "")
    .replace(/notification gateway returned false/gi, "알림 발송 시스템이 실패를 반환했습니다")
    .replace(/rate limit/gi, "요청 제한")
    .replace(/timeout/gi, "응답 시간 초과")
    .replace(/connection reset/gi, "연결이 끊어짐");
};

export const formatPercent = (value) => {
  const num = Number(value);
  return Number.isFinite(num) ? `${num.toFixed(2)}%` : "—";
};

export const formatNumber = (value) => {
  const num = Number(value);
  return Number.isFinite(num) ? num.toLocaleString("ko-KR") : "—";
};

const formatCompactJsonValue = (value) => {
  if (typeof value === "number") return `${formatNumber(value)}건`;
  if (typeof value === "boolean") return value ? "예" : "아니오";
  if (value === null || value === undefined || value === "") return "없음";
  if (Array.isArray(value)) {
    if (!value.length) return "없음";
    return value.map((entry) => formatAdminOperationalText(entry, "")).filter(Boolean).join(", ");
  }
  if (typeof value === "object") {
    return formatCompactJson(JSON.stringify(value));
  }
  return formatAdminOperationalText(value, String(value));
};

export const formatCodebookMetadataValue = (value) => {
  if (typeof value === "number") return formatNumber(value);
  if (typeof value === "boolean") return value ? "예" : "아니오";
  if (Array.isArray(value)) {
    return value.map((entry) => formatCodebookMetadataValue(entry)).filter(Boolean).join(", ") || "없음";
  }
  return formatAdminOperationalText(value, String(value ?? "없음"));
};

export const formatCompactJson = (value) => {
  const raw = String(value ?? "").trim();
  if (!raw) return "—";
  try {
    const parsed = JSON.parse(raw);
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
      const entries = Object.entries(parsed);
      if (!entries.length) return "없음";
      return entries
        .slice(0, 6)
        .map(([key, entryValue]) => `${formatStatusLabel(key)} ${formatCompactJsonValue(entryValue)}`)
        .join(" · ")
        + (entries.length > 6 ? ` · 외 ${formatNumber(entries.length - 6)}개` : "");
    }
    if (Array.isArray(parsed)) {
      if (!parsed.length) return "없음";
      return parsed
        .slice(0, 6)
        .map((entry) => formatCompactJsonValue(entry))
        .join(" · ")
        + (parsed.length > 6 ? ` · 외 ${formatNumber(parsed.length - 6)}개` : "");
    }
  } catch {
    // Non-JSON snippets are shortened below without changing their original meaning.
  }
  return raw.length > 96 ? `${raw.slice(0, 96)}...` : raw;
};

export const formatDateTime = (value) => {
  if (!value) return "—";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return "—";
  return new Intl.DateTimeFormat("ko-KR", {
    month: "numeric",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(parsed);
};

export const formatDate = (value) => {
  if (!value) return "—";
  const parsed = new Date(`${value}T00:00:00`);
  if (Number.isNaN(parsed.getTime())) return "—";
  return new Intl.DateTimeFormat("ko-KR", {
    month: "numeric",
    day: "numeric",
  }).format(parsed);
};

export const formatMaskedUserKey = (value, fallback = "미연결") => {
  const raw = String(value ?? "").trim();
  if (!raw) return fallback;
  if (raw.length <= 8) return `${raw.slice(0, 2)}…${raw.slice(-2)}`;
  return `${raw.slice(0, 6)}…${raw.slice(-4)}`;
};

const EMAIL_DISPLAY_PATTERN = /([A-Z0-9._%+-]{1,64})@([A-Z0-9.-]+\.[A-Z]{2,})/gi;
const PHONE_DISPLAY_PATTERN = /\b(?:\+?82[-.\s]?)?0?1[016789][-\s.]?\d{3,4}[-\s.]?\d{4}\b/g;
const RESIDENT_ID_DISPLAY_PATTERN = /\b\d{6}[-\s]?[1-8]\d{6}\b/g;
const LONG_NUMBER_DISPLAY_PATTERN = /\b\d{4,6}[-\s]\d{2,6}[-\s]\d{2,8}\b/g;

export const formatMaskedEmail = (value, fallback = "이메일 없음") => {
  const raw = String(value ?? "").trim();
  if (!raw) return fallback;
  const atIndex = raw.lastIndexOf("@");
  if (atIndex <= 0 || atIndex === raw.length - 1) return "이메일 형식 오류";

  const local = raw.slice(0, atIndex);
  const domain = raw.slice(atIndex + 1);
  const visibleLocal = local.length <= 2 ? local.slice(0, 1) : local.slice(0, 2);
  return `${visibleLocal}***@${domain}`;
};

export const redactAdminDisplayText = (value) => String(value ?? "")
  .replace(EMAIL_DISPLAY_PATTERN, "[이메일]")
  .replace(PHONE_DISPLAY_PATTERN, "[전화번호]")
  .replace(RESIDENT_ID_DISPLAY_PATTERN, "[식별번호]")
  .replace(LONG_NUMBER_DISPLAY_PATTERN, "[번호]");

export const formatAdminDisplayText = (value, fallback = "내용 없음") => {
  const text = redactAdminDisplayText(value).trim();
  return text || fallback;
};

export const formatAdminReviewNoteSuffix = (value) => {
  const text = formatAdminDisplayText(value, "");
  return text ? ` · ${text}` : "";
};

export const formatAdminRoutePath = (value, fallback = "경로 정보 없음", baseOrigin = "http://localhost") => {
  const raw = String(value ?? "").trim();
  if (!raw) return fallback;

  try {
    const resolvedBaseOrigin = typeof window !== "undefined" ? window.location.origin : baseOrigin;
    const parsed = new URL(raw, resolvedBaseOrigin);
    if (parsed.origin !== resolvedBaseOrigin || !parsed.pathname.startsWith("/")) {
      return fallback;
    }
    return parsed.pathname;
  } catch {
    return fallback;
  }
};

export const formatRelativeDateTime = (value, now = Date.now()) => {
  if (!value) return "업데이트 정보 없음";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return "업데이트 정보 없음";

  const nowMs = typeof now === "number" ? now : new Date(now).getTime();
  const diffMinutes = Math.max(0, Math.floor((nowMs - parsed.getTime()) / 60000));
  if (diffMinutes < 1) return "방금 갱신";
  if (diffMinutes < 60) return `${diffMinutes}분 전 갱신`;

  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours}시간 전 갱신`;

  const diffDays = Math.floor(diffHours / 24);
  return `${diffDays}일 전 갱신`;
};
