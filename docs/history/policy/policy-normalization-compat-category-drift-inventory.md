# `compat_unified_category` / canonical summary drift inventory

2026-04-30 local draft sidecar migration 기준으로
`service_taxonomies` summary row를 직접 확인한 결과입니다.

이 문서의 아래 수치는 `youth_major` summary 정제 규칙을
writer/backfill 양쪽에 반영하기 전 snapshot이 아니라,
로컬 DB에서 `YOUTH` summary를 실제로 재적재한 뒤 재측정한 최신 snapshot 기준입니다.

목적:

- priority 호환 레이어로 쓰는 `compat_unified_category` 와
- canonical summary(`youth_major_label`, `youth_mid_label`, `gov24_*`)

사이의 실제 상태 차이를 먼저 고정합니다.

## 요약

현 상태에서는 `compat_unified_category` 를 계속 priority 호환 레이어로 유지하는 쪽이 맞습니다.

이유:

1. `compat_unified_category` 는 `3634 / 3634` rows에 채워져 있습니다.
2. 반면 canonical summary는 `youth_major_label 2248 / 3634`, `youth_mid_label 0 / 3634`, `gov24_service_field_label 0 / 3634` 입니다.
3. `YOUTH` summary의 raw 복사 흔적은 정제 후 사라졌습니다.
   - `compat=기타` + `youth_major_label` 채움: `421`
   - `youth_major_label` comma 포함: `0`
   - raw variant label(`금융･복지･문화`, `참여･기반`, `교육･직업훈련`) 잔존: `0`
4. 즉 지금은 “summary 품질 불안정” 문제보다
   `compat=기타` 와 canonical `youth_major` 역할 차이 자체가 더 큰 관찰 포인트입니다.

## 집계

### 전체

| metric | count |
|---|---:|
| `service_taxonomies` total | `3634` |
| `compat_unified_category_label` filled | `3634` |
| `youth_major_label` filled | `2248` |
| `youth_mid_label` filled | `0` |
| `gov24_service_field_label` filled | `0` |

### source별

| source | total | compat filled | youth major filled | youth mid filled |
|---|---:|---:|---:|---:|
| `YOUTH` | `2299` | `2299` | `2248` | `0` |
| `BOKJIRO_CENTRAL` | `115` | `115` | `0` | `0` |
| `BOKJIRO_LOCAL` | `1220` | `1220` | `0` | `0` |

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

재측정 후 집계는 아래처럼 정리됐습니다.

- `일자리 -> 일자리`: `878`
- `주거 -> 주거`: `232`
- `교육·직업훈련 -> 교육`: `157`
- `금융·생활지원 -> 복지문화`: `370`
- `참여·기회 -> 참여권리`: `190`

즉 canonical summary가 single normalized value로 정리된 뒤에도, 현재 priority 호환 레이어에서 쓰는 대표 매핑 자체는 크게 어긋나지 않았습니다.

### 2. `YOUTH` 의 `compat=기타` 비중은 여전히 높지만, 이제 raw 흔적이 아니라 canonical summary와의 역할 차이로 읽어야 함

집계:

- `compat=기타` + `youth_major_label` 채움: `421`
- `compat=기타` + `youth_major_label` 비움: `51`

대표 샘플:

| service_id | title | compat | youth_major_label | category_main | category_sub |
|---|---|---|---|---|---|
| `1` | `(접수 마감)2026년 11기 광주 청년 13(일+삶)통장` | `기타` | `복지문화` | `금융･복지･문화` | `취약계층 및 금융지원` |
| `6` | `2026 광산구 홍보파트너 모집` | `기타` | `참여권리` | `참여･기반` | `청년참여` |
| `13` | `[남구] 월간 청년밋업 강연(1월)` | `기타` | `교육` | `교육･직업훈련` | `교육비지원` |
| `16` | `2026년 삼삼오오 이웃돌봄 참여자 모집` | `기타` | `복지문화` | `금융･복지･문화` | `문화활동 및 생활지원` |

해석:

- `compat_unified_category` 는 현재 서비스/프론트 계약용 분류를 유지하고 있고
- `youth_major_label` 은 이제 canonical label(`복지문화`, `참여권리`, `교육`)로 정규화됐습니다

따라서 두 값은 같은 용도가 아닙니다.

### 3. `youth_major_label` 에 comma/중복 raw 값은 더 이상 남아 있지 않음

집계:

- `youth_major_label` comma 포함: `0`
- raw variant label 잔존: `0`

대표 샘플:

| service_id | title | compat | youth_major_label | category_main | category_sub |
|---|---|---|---|---|---|
| `44` | `신성장산업-청년인재플러스사업` | `기타` | `일자리` | `일자리,일자리,일자리` | `취업,재직자,권익보호` |
| `56` | `브릿지보증 (실패보장제) 강화` | `기타` | `일자리` | `일자리,일자리` | `창업,취업` |
| `106` | `청년월세 지원` | `기타` | `주거` | `주거,주거` | `주택 및 거주지,전월세 및 주거급여 지원` |
| `359` | `항공정비 청년일자리 맞춤지원` | `기타` | `교육` | `교육･직업훈련,교육･직업훈련` | `온·오프라인교육 ,미래역량강화` |

해석:

- summary 자체는 이제 single canonical value invariant를 만족합니다
- 따라서 남은 문제는 summary 품질보다 `compat_unified_category` 와 canonical major를 언제 어떻게 연결할지에 가깝습니다

## 현재 판단

당분간:

- priority/read-model 호환 레이어는 `compat_unified_category` 유지
- `DefaultPriorityMatcher` 는 `RecommendationCandidateProjection.unifiedCategoryCompat` 만 사용
- canonical summary(`youth_major_label`, `gov24_service_field_label`)는
  아직 직접 해석하지 않음

다만 이전과 달리, 보류 이유는 “summary 품질이 raw/multi-value라서”가 아니라
`compat=기타` 이면서 canonical major는 채워지는 `421`건을
priority/read-model 에서 어떻게 다룰지 별도 정책이 필요하기 때문입니다.

## 다음 작업

1. `compat=기타 + canonical youth_major 채움(421건)` 을 priority/read-model 호환 레이어에서 어떻게 해석할지 결정
2. `service_taxonomies` summary 재적재 후 drift inventory를 기준으로 `compat_unified_category` 저장/계산 정책 재검토
3. 그 뒤에만 `DefaultPriorityMatcher` 의 summary code/label direct-read 여부 재검토
