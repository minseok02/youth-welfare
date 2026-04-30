# `compat_unified_category` / canonical summary drift inventory

2026-04-30 local draft sidecar migration 기준으로
`service_taxonomies` summary row를 직접 확인한 결과입니다.

목적:

- priority 호환 레이어로 쓰는 `compat_unified_category` 와
- canonical summary(`youth_major_label`, `youth_mid_label`, `gov24_*`)

사이의 실제 상태 차이를 먼저 고정합니다.

## 요약

현 상태에서는 `compat_unified_category` 를 계속 priority 호환 레이어로 유지하는 쪽이 맞습니다.

이유:

1. `compat_unified_category` 는 `3634 / 3634` rows에 채워져 있습니다.
2. 반면 canonical summary는 `youth_major_label 2298 / 3634`, `youth_mid_label 0 / 3634`, `gov24_service_field_label 0 / 3634` 입니다.
3. `YOUTH` summary에도 raw 복사 흔적이 남아 있습니다.
   - `compat=기타` + `youth_major_label` 채움: `471`
   - `youth_major_label` comma 포함: `102`
4. 즉 지금은 “compat와 canonical이 충돌한다”기보다
   `compat는 안정적인 호환 출력`, `canonical summary는 아직 정제/안정화 중` 상태입니다.

## 집계

### 전체

| metric | count |
|---|---:|
| `service_taxonomies` total | `3634` |
| `compat_unified_category_label` filled | `3634` |
| `youth_major_label` filled | `2298` |
| `youth_mid_label` filled | `0` |
| `gov24_service_field_label` filled | `0` |

### source별

| source | total | compat filled | youth major filled | youth mid filled |
|---|---:|---:|---:|---:|
| `YOUTH` | `2299` | `2299` | `2298` | `0` |
| `BOKJIRO` | `1335` | `1335` | `0` | `0` |

## 핵심 관찰

### 1. mapped compat category 자체의 직접 충돌은 아직 거의 없음

로컬 snapshot에서 아래 매핑에 해당하는 row들은 별도 drift sample이 나오지 않았습니다.

- `주거 -> 주거`
- `일자리 -> 일자리`
- `교육·직업훈련 -> 교육`
- `금융·생활지원 -> 복지문화`
- `참여·기회 -> 참여권리`

즉 현재 문제는 “같은 row에서 compat와 summary가 서로 다른 의미를 가리킨다”보다,
summary가 아예 비어 있거나 raw 다중값/legacy label을 그대로 품고 있다는 쪽에 가깝습니다.

### 2. `YOUTH` 의 `compat=기타` 비중이 높고, 여기에 summary raw 흔적이 몰려 있음

집계:

- `compat=기타` + `youth_major_label` 채움: `471`
- `compat=기타` + `youth_major_label` 비움: `1`

대표 샘플:

| service_id | title | compat | youth_major_label | category_main | category_sub |
|---|---|---|---|---|---|
| `1` | `(접수 마감)2026년 11기 광주 청년 13(일+삶)통장` | `기타` | `금융･복지･문화` | `금융･복지･문화` | `취약계층 및 금융지원` |
| `6` | `2026 광산구 홍보파트너 모집` | `기타` | `참여･기반` | `참여･기반` | `청년참여` |
| `13` | `[남구] 월간 청년밋업 강연(1월)` | `기타` | `교육･직업훈련` | `교육･직업훈련` | `교육비지원` |
| `16` | `2026년 삼삼오오 이웃돌봄 참여자 모집` | `기타` | `금융･복지･문화` | `금융･복지･문화` | `문화활동 및 생활지원` |

해석:

- `compat_unified_category` 는 현재 서비스/프론트 계약용 분류를 유지하고 있고
- `youth_major_label` 은 여전히 `category_main` raw 계열(`참여･기반`, `금융･복지･문화`)을 따라갑니다

따라서 두 값은 같은 용도가 아닙니다.

### 3. `youth_major_label` 에 comma/중복 raw 값이 남아 있음

집계:

- `youth_major_label` comma 포함: `102`

대표 샘플:

| service_id | title | compat | youth_major_label | category_main | category_sub |
|---|---|---|---|---|---|
| `44` | `신성장산업-청년인재플러스사업` | `기타` | `일자리,일자리,일자리` | `일자리,일자리,일자리` | `취업,재직자,권익보호` |
| `56` | `브릿지보증 (실패보장제) 강화` | `기타` | `일자리,일자리` | `일자리,일자리` | `창업,취업` |
| `106` | `청년월세 지원` | `기타` | `주거,주거` | `주거,주거` | `주택 및 거주지,전월세 및 주거급여 지원` |
| `359` | `항공정비 청년일자리 맞춤지원` | `기타` | `교육･직업훈련,교육･직업훈련` | `교육･직업훈련,교육･직업훈련` | `온·오프라인교육 ,미래역량강화` |

해석:

- `service_taxonomies.youth_major_label` summary가 아직 “canonical single summary” 라기보다
  raw `category_main` backfill 복사 흔적을 유지합니다
- 이 상태에서는 `DefaultPriorityMatcher` 가 summary 직독으로 가면
  같은 row를 어떻게 분류해야 하는지 기준이 흔들릴 수 있습니다

## 현재 판단

당분간:

- priority/read-model 호환 레이어는 `compat_unified_category` 유지
- `DefaultPriorityMatcher` 는 `RecommendationCandidateProjection.unifiedCategoryCompat` 만 사용
- canonical summary(`youth_major_label`, `gov24_service_field_label`)는
  drift inventory와 summary 정제 규칙이 준비될 때까지 직접 해석하지 않음

## 다음 작업

1. `YOUTH category_main -> service_taxonomies.youth_major_*` summary 정제 규칙 작성
2. comma/duplicate `youth_major_label` collapse/backfill 전략 설계
3. `service_taxonomies` summary 재적재 후 compat vs summary drift inventory 재측정
4. 그 뒤에만 `DefaultPriorityMatcher` 의 summary code/label direct-read 여부 재검토
