# `compat=기타 + canonical youth_major 채움` 해석 정책

2026-04-30 local summary 재적재 후에도  
`YOUTH` source에서:

- `compat_unified_category = 기타`
- `service_taxonomies.youth_major_*` 채움

인 row가 `421`건 남았습니다.

이 문서는 이 집합을 priority/read-model에서 어떻게 다룰지 고정합니다.

관련 문서:

- [policy-normalization-compat-category-drift-inventory.md](./policy-normalization-compat-category-drift-inventory.md)
- [policy-normalization-recommendation-read-model.md](./policy-normalization-recommendation-read-model.md)
- [policy-normalization-bridge-rules.md](./policy-normalization-bridge-rules.md)

## 결론

당분간은 아래 3개를 같이 유지합니다.

1. priority 레이어는 계속 `compat_unified_category` 만 사용
2. `compat=기타` 를 `youth_major` 로 조용히 override 하지 않음
3. canonical `youth_major` 는 read-model의 보조 힌트로만 노출

즉:

- `DefaultPriorityMatcher`
- current priority bonus
- legacy 응답/정렬 의미

는 그대로 `compat_unified_category` 를 기준으로 유지합니다.

반면 canonical `youth_major` 는:

- inventory 분석
- explanation/badge 후보
- future experiment

용 보조 signal로만 남깁니다.

## 왜 override 하지 않는가

### 1. `compat` 는 현재 제품 계약이다

지금 priority와 응답은 모두 `compat_unified_category` 의미를 전제로 합니다.

여기서 `compat=기타` 를 보고
`youth_major=복지문화` 또는 `참여권리` 로 곧바로 덮어쓰면,
저장값은 그대로인데 추천 의미만 조용히 바뀝니다.

### 2. `youth_major` 는 canonical major이지 priority intent가 아니다

예:

- `복지문화`
- `참여권리`
- `교육`

는 canonical 분류로는 유효합니다.

하지만 현재 priority 옵션:

- `금융·생활지원`
- `참여·기회`
- `교육·직업훈련`

과 1:1 같은 의미라고 바로 가정하면 안 됩니다.

즉 canonical taxonomy와 current UX priority bucket은 아직 같은 계층이 아닙니다.

### 3. `기타` 안에는 의도적으로 broad/혼합 성격이 남아 있다

`compat=기타 + youth_major 채움` 집합은
정제 실패가 아니라 현재 compat layer가 더 보수적으로 남겨둔 결과일 수 있습니다.

예:

- `금융･복지･문화 -> 복지문화`
- `참여･기반 -> 참여권리`

처럼 canonical major는 채워도,
현재 제품이 이를 바로 `금융·생활지원`, `참여·기회` priority로 쓰는 것이
항상 같은 UX 의미를 주는지는 아직 별도 검토가 필요합니다.

## 현재 정책

### 1. priority / scoring

- `RecommendationCandidateProjection.unifiedCategoryCompat` 유지
- `DefaultPriorityMatcher` 는 계속 `compat_unified_category` 기준
- `compat=기타` 이면 priority bonus는 추가하지 않음
- `youth_major` 만으로 priority bonus를 새로 만들지 않음

### 2. read-model

`RecommendationCandidateProjection` 또는 future projection에:

- `unifiedCategoryCompat`
- `youthMajorCode`
- `youthMajorLabel`

을 같이 둘 수는 있다.

하지만 역할은 다르다.

- `unifiedCategoryCompat`: 현재 추천/우선순위 호환용
- `youthMajor*`: canonical 분류 힌트

즉 projection에는 함께 실어도,
현재 stage에서 같은 필드처럼 소비하지 않는다.

### 3. response / UI

지금 단계에서는 `compat=기타` 인 서비스를
response category에서 `youth_major` 로 치환하지 않는다.

필요하면 나중에:

- subtitle
- badge
- explanation

같은 보조 표현으로만 제한적으로 사용한다.

## 허용되는 다음 단계

아래는 후속 검토 가능:

1. `compat=기타 + youth_major=복지문화` 집합을 별도 inventory로 나눠,
   실제 current `금융·생활지원` priority와 의미가 충분히 같은지 검토
2. `참여권리 -> 참여·기회`, `교육 -> 교육·직업훈련` 등
   canonical-to-priority bridge table을 명시적으로 작성
3. 그 bridge가 고정된 뒤에만
   `compat=기타` 일부를 read-model 계산값으로 보정하는 실험 수행

## 금지되는 것

현재 단계에서 하지 않는 것:

- `compat=기타` 를 `youth_major` 로 자동 override
- `DefaultPriorityMatcher` 가 `youth_major` 를 직접 해석
- response category를 `youth_major` 로 바로 치환
- canonical summary 하나만 보고 legacy priority bucket을 역계산

## 다음 작업

1. `RecommendationCandidateProjection` 에 canonical summary hint를 어디까지 실을지 정리
2. `compat=기타 + youth_major 채움 421건` 을 `복지문화 / 참여권리 / 교육 / 일자리 / 주거` 별로 다시 쪼개 inventory 작성
3. priority용 explicit bridge table이 필요한지 결정

세부 분포와 샘플은
[policy-normalization-compat-other-youth-major-inventory.md](./policy-normalization-compat-other-youth-major-inventory.md)
에 별도로 정리한다.
