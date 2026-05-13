# 정책 정규화 fact merge / upsert 규칙

이 문서는 [policy-normalization-schema-draft.md](./policy-normalization-schema-draft.md) 와 [policy-normalization-bridge-rules.md](./policy-normalization-bridge-rules.md) 에서 정의한 `service_facts` 저장 구조에 대해, 특히 **복지로 list aggregate 와 detail aggregate 가 같은 서비스에 대해 동시에 들어올 때** 어떻게 merge / upsert 할지 고정하기 위한 문서입니다.

관련 문서:

- [policy-normalization-schema-draft.md](./policy-normalization-schema-draft.md)
- [policy-normalization-bridge-rules.md](./policy-normalization-bridge-rules.md)
- [policy-normalization-research.md](./policy-normalization-research.md)
- [collect-ops.md](../../collect/collect-ops.md)

## 1. 목표

이번 규칙의 목표는 아래 4개입니다.

1. 복지로 list 수집과 detail 수집이 같은 fact를 중복 적재하지 않게 한다
2. detail 수집이 list fallback fact를 안전하게 덮어쓸 수 있게 한다
3. 이후 `service_facts` migration SQL 에 필요한 logical merge key 를 먼저 고정한다
4. `WelfareServiceMapper` / `BokjiroDetailCollectService` / future sidecar saver 가 같은 우선순위 규칙을 공유하게 한다

## 2. 기본 원칙

### 2.1 `fact_code` 와 `fact_merge_key` 는 역할이 다르다

- `fact_code`
  - 외부 코드 또는 canonical 분류 코드
  - 예: `JA0203`, `UNEMPLOYED`
- `fact_merge_key`
  - **같은 서비스에서 같은 사실 슬롯을 대체/병합할 때 쓰는 내부 key**
  - 예: `BK_AGE_ELIGIBILITY`, `BK_APPLY_END_DATE`

즉 `TEXT_AGE` / `DETAIL_TEXT_AGE` 처럼 phase를 코드값에 넣어 버리면 merge key 역할을 할 수 없습니다.

## 3. 복지로에서 필요한 merge 유형

복지로 fact는 두 종류로 나눕니다.

### 3.1 singleton slot

한 서비스에 대해 **최종적으로 1행만 남아야 하는 fact**.

예:

- 연령 범위
- 신청 종료일
- 소득 상한(%)
- 소득 상한(만원)
- 월세 상한

이 fact들은 list 와 detail 에서 모두 추출될 수 있어도, 저장 시점에는 가장 강한 1행만 남겨야 합니다.

### 3.2 set-like slot

한 서비스에 대해 **여러 값이 공존 가능** 한 fact.

예:

- 취업 상태
- 학력 상태
- 가구 특성
- 특수 대상

이 fact들은 list 와 detail 에서 같은 값이 중복 추출되면 dedupe 하고, 다른 값이면 union 합니다.

## 4. `service_facts` 에 필요한 logical merge key

복지로 계열에서 우선 고정할 merge key는 아래와 같습니다.

| fact_group | merge 유형 | `fact_merge_key` 예시 | 설명 |
|---|---|---|---|
| `AGE` | singleton | `BK_AGE_ELIGIBILITY` | 지원 연령 범위 |
| `APPLY_END_DATE` | singleton | `BK_APPLY_END_DATE` | 신청 종료일 |
| `INCOME` | singleton | `BK_INCOME_LIMIT_PERCENT` | `%` 기준 상한 |
| `INCOME` | singleton | `BK_INCOME_LIMIT_AMOUNT` | `만원` 기준 상한 |
| `RENT_CAP` | singleton | `BK_RENT_CAP` | 월세/임차료 상한 |
| `EMPLOYMENT` | set-like | `BK_EMPLOYMENT:<normalized-code>` | 취업상태 |
| `EDUCATION` | set-like | `BK_EDUCATION:<normalized-code>` | 학력상태 |
| `HOUSEHOLD` | set-like | `BK_HOUSEHOLD:<normalized-code>` | 가구특성 |
| `SPECIAL_GROUP` | set-like | `BK_SPECIAL:<normalized-code>` | 특수대상 |

### 4.1 schema 반영 원칙

`service_facts` 에 아래 컬럼을 추가하는 쪽으로 결정합니다.

- `fact_merge_key VARCHAR(128) NOT NULL`

유니크 제약 후보:

- `uq_service_fact_merge (service_id, fact_merge_key)`

이 컬럼이 없으면 list row 와 detail row 가 같은 의미의 fact 인지 애플리케이션 레벨에서만 추측해야 하고, migration 이후에도 deterministic upsert 를 보장하기 어렵습니다.

## 5. precedence 규칙

같은 `service_id + fact_merge_key` 에 대해 기존 row 와 incoming row 가 충돌할 때는 아래 순서로 판정합니다.

1. `authority` 우선순위
2. `confidence` 우선순위
3. source field priority
4. 그 외에는 기존 row 유지

### 5.1 authority 우선순위

높은 순서:

1. `OFFICIAL`
2. `SYSTEM_DERIVED`
3. `RULE_DERIVED`
4. `AI_ENRICHED`

### 5.2 confidence 우선순위

같은 authority 면 `confidence` 가 높은 row가 이깁니다.

예:

- list fallback `AGE` = `RULE_DERIVED`, `0.80`
- detail fallback `AGE` = `RULE_DERIVED`, `0.90`

이 경우 detail row가 기존 list row를 덮어씁니다.

### 5.3 source field priority

authority 와 confidence 가 모두 같으면 아래 source field 우선순위를 씁니다.

1. `targetDetail`
2. `selectionCriteria`
3. `servDgst`
4. `applyMethodDetail`
5. `supportDetail`

즉 같은 `RULE_DERIVED 0.90` 이라도 `targetDetail` 기반 추출이 `servDgst` 기반 추출보다 우선입니다.

## 6. 복지로 list/detail merge 규칙

### 6.1 singleton slot

동일 `fact_merge_key` 에 대해:

- incoming row 가 더 강하면 `UPDATE`
- incoming row 가 약하면 `SKIP`
- 값이 동일하면 `no-op`

예시:

1. list collect
   - `BK_AGE_ELIGIBILITY = 19~34, RULE_DERIVED, 0.80, source_field=servDgst`
2. detail collect
   - `BK_AGE_ELIGIBILITY = 19~34, RULE_DERIVED, 0.90, source_field=targetDetail`

결과:

- detail row가 list row를 덮어씀

### 6.2 set-like slot

동일 `fact_merge_key` 면 dedupe, 다른 `fact_merge_key` 면 union 합니다.

예시:

1. list collect
   - `BK_HOUSEHOLD:ONE_PERSON`
2. detail collect
   - `BK_HOUSEHOLD:ONE_PERSON`
   - `BK_HOUSEHOLD:HOMELESS`

결과:

- `ONE_PERSON` 은 1행 유지
- `HOMELESS` 는 신규 추가

## 7. mapper 에 바로 반영할 원칙

`WelfareServiceMapper` 에서 future sidecar persistence 전에 아래를 맞춰야 합니다.

1. phase-specific `fact_code` (`TEXT_AGE`, `DETAIL_TEXT_AGE`) 를 그대로 persistence key로 쓰지 않는다
2. persistence 전 단계에서 stable `fact_merge_key` 를 만들 수 있게 canonical helper 를 추가한다
3. 복지로 detail aggregate 는 list aggregate 와 같은 semantic slot 이름을 공유한다

즉:

- 지금 코드의 `fact_code` 는 테스트/초안 용도로는 허용
- 실제 `service_facts` 저장 전에는 stable merge key 체계로 한 번 더 정리해야 함

## 8. 이번 단계에서 하지 않는 것

- `Gov24` official facts merge 규칙
- `AI enrichment` 와 official/rule facts conflict merge
- `service_taxonomies` merge 규칙
- `service_tags` 와 `service_taxonomy_terms` 통합 삭제 시점 결정

이번 문서는 **복지로 list/detail facts merge** 에만 집중합니다.

## 9. 다음 작업

1. `policy-normalization-schema-draft.md` 에 `fact_merge_key` 컬럼과 unique key 반영
2. `service_facts` migration SQL 초안에 `fact_merge_key` 포함
3. `WelfareServiceMapper` 의 복지로 facts 를 stable merge key 체계로 정리
4. future sidecar saver 테스트에 `list -> detail overwrite`, `detail -> list no-op`, `set-like union` 케이스 추가
