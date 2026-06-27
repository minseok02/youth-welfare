# canonical summary slot 저장 구조 설계

## 목적

현재 canonical taxonomy summary는 코드 경계에서

- `TaxonomySummary`
- `CanonicalTaxonomySummarySlots`
- `ServiceTaxonomyLegacySummaryBridge`

로 한 번 정리됐지만,
실제 DB 저장 모델은 아직 `service_taxonomies.youth_*`, `gov24_*` 같은
source-specific summary 컬럼 중심입니다.

이 문서는 다음 단계에서 summary 저장을 더 generic하게 만들기 위한
**병행 저장 구조와 이행 순서**를 고정합니다.

## 현재 문제

현재 `service_taxonomies` 는 서비스당 1행 summary row를 유지하고,
아래 축을 컬럼으로 직접 가집니다.

- `compat_unified_category_*`
- `youth_major_*`
- `youth_mid_*`
- `gov24_service_field_*`
- `gov24_user_type_*`
- `gov24_benefit_type_*`
- `provision_method_*`

이 구조의 장점은 read/query가 단순하다는 점이지만,
단점도 명확합니다.

1. 새 source가 새로운 summary 축을 추가하면 DDL이 바로 필요하다.
2. writer가 아무리 generic해져도 마지막 저장 모델은 source-specific 이다.
3. summary slot inventory를 read-model, backfill, drift query가 재사용하기 어렵다.

즉 현재 구조는 `현재 source 3종 유지` 에는 충분하지만,
`source를 더 붙일 때 요약 축을 계속 늘리는 구조` 로는 확장성이 약합니다.

## 목표

다음 두 가지를 동시에 만족하는 것이 목표입니다.

1. 현재 `service_taxonomies` 기반 read/query 계약을 당장 깨지 않는다.
2. 신규 source summary 축은 generic slot row로 먼저 저장할 수 있게 만든다.

즉 **legacy summary row 유지 + generic slot row 병행 저장** 이 기본 전략입니다.

## 제안 저장 모델

### 1. 기존 `service_taxonomies`

당분간 유지합니다.

역할:

- compat/read-model 호환 레이어
- 현재 추천/search/detail 응답 계약 유지
- 자주 쓰는 대표 축을 빠르게 읽는 summary row

단, 이 테이블은 앞으로도 “모든 summary truth” 가 아니라
**legacy projection / compatibility row** 로 정의합니다.

### 2. 신규 `service_taxonomy_summary_slots`

서비스당 다중 summary slot을 저장하는 반복 테이블을 추가합니다.

예상 컬럼:

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | BIGINT PK | 내부 식별자 |
| `service_id` | BIGINT FK | `welfare_services.id` |
| `slot_key` | VARCHAR(64) | 예: `YOUTH_MAJOR`, `YOUTH_MID`, `GOV24_SERVICE_FIELD`, `PROVISION_METHOD` |
| `code_set_key` | VARCHAR(64) NULL | slot이 연결되는 code set |
| `slot_code` | VARCHAR(64) NOT NULL DEFAULT `''` | stable code가 있으면 저장, 없으면 빈 문자열 |
| `slot_label` | VARCHAR(255) NOT NULL | canonical summary label |
| `source_field` | VARCHAR(100) NULL | summary를 만든 대표 source field |
| `authority` | ENUM | `OFFICIAL`, `SYSTEM_DERIVED`, `AI_ENRICHED` |
| `confidence` | DECIMAL(4,3) NULL | derived slot일 때 사용 |
| `created_at` | DATETIME | 생성시각 |
| `updated_at` | DATETIME | 수정시각 |

유니크 제약:

- `uq_service_summary_slot (service_id, slot_key, slot_code, slot_label, authority)`

기본 원칙:

- `slot_key` 는 `CanonicalTaxonomySummarySlots` catalog와 같은 값 사용
- code가 없으면 `slot_code=''`
- label만 있는 slot도 동일 테이블에서 관리
- source별 새 summary 축은 이 테이블에 먼저 추가 가능

## why row table

summary를 JSON 컬럼으로 두지 않고 row table로 두는 이유는 아래와 같습니다.

1. `slot_key` 단위 조건 쿼리가 쉽다.
2. authority/confidence/source_field 를 slot별로 따로 남길 수 있다.
3. `service_taxonomy_terms` / `service_facts` 와 같은 sidecar family로 일관된다.
4. backfill/drift inventory를 SQL로 직접 비교하기 쉽다.

## legacy와 generic의 관계

핵심 규칙은 이렇습니다.

### canonical truth

- 신규 canonical summary truth는 장기적으로 `service_taxonomy_summary_slots`

### legacy projection

- 현재 계약용 요약 row는 `service_taxonomies`

### bridge

- `ServiceTaxonomyLegacySummaryBridge` 는
  `summary_slots -> service_taxonomies` 투영 규칙으로만 본다

즉 지금까지는 `TaxonomySummary -> service_taxonomies` 였다면,
장기 목표는 아래입니다.

1. `TaxonomySummary -> summary_slots`
2. `summary_slots -> service_taxonomies`

## write path 이행 순서

### 단계 1. 현재 상태 유지

- writer는 계속 `service_taxonomies` 만 upsert
- `CanonicalTaxonomySummarySlots` 는 메모리 경계로만 존재

### 단계 2. `service_taxonomy_summary_slots` 추가

- draft migration으로 테이블 생성
- writer가 `CanonicalTaxonomySummarySlots.from(taxonomy)` 를 이 테이블에도 저장
- 동시에 기존 `service_taxonomies` 도 계속 upsert

### 단계 3. read/query inventory 추가

예:

- slot row count
- slot coverage by source
- slot drift vs legacy summary

이 단계에서는 아직 추천/응답 read path를 바꾸지 않습니다.

### 단계 4. legacy summary bridge source 전환

- `service_taxonomies` 값을 직접 aggregate에서 만들지 않고
- 저장된 `summary_slots` row를 기준으로 projection/backfill 가능하게 정리

### 단계 5. read-model selective migration

이후 준비가 되면:

- recommendation read-model 일부
- drift inventory query 일부
- source onboarding validation 일부

를 `summary_slots` 우선으로 전환합니다.

## read path 원칙

현재 추천/응답 경계는 계속 아래를 유지합니다.

- `compat_unified_category` 우선
- priority는 compat 호환 레이어 유지
- `youth_major/gov24_*` 는 secondary hint

따라서 `summary_slots` 를 추가해도 당장
`DefaultPriorityMatcher`, response `unifiedCategory`, search 대표 category를
직접 바꾸지 않습니다.

## slot inventory 초안

초기 known slot:

- `YOUTH_MAJOR`
- `YOUTH_MID`
- `GOV24_SERVICE_FIELD`
- `GOV24_USER_TYPE`
- `GOV24_BENEFIT_TYPE`
- `PROVISION_METHOD`

향후 source 추가 시 후보:

- `PROVIDER_GROUP`
- `HOUSING_SUPPLY_TYPE`
- `SCHOLARSHIP_KIND`
- `EMPLOYMENT_PROGRAM_STAGE`

중요한 점은 새 slot을 추가할 때
`service_taxonomies` DDL 없이도 먼저 slot row 저장은 가능해야 한다는 점입니다.

## migration 원칙

### backfill

초기 backfill은 `service_taxonomies` existing row에서 역으로 채우기보다,
가능하면 raw canonical aggregate replay 기준으로 채웁니다.

이유:

- legacy row는 이미 source-specific collapse 결과라 정보 손실이 있을 수 있음
- slot row의 `authority/source_field/confidence` 를 정확히 복구하려면 aggregate replay가 더 낫다

### compatibility

`service_taxonomies` 는 당분간 남기므로

- 기존 SQL
- existing recommendation projection
- ops query

는 깨지지 않습니다.

## reopen 조건

아래가 준비되면 실제 DDL/task로 reopen 합니다.

1. `service_taxonomy_summary_slots` draft DDL 작성
  - 2026-05-02 `backend/src/main/resources/db/migration-draft/V2026_05_02_01__add_service_taxonomy_summary_slots.sql` 로 초안 추가 완료
2. writer dual-write 범위 정의
  - 2026-05-02 `DeferredNormalizedPolicySidecarWriter` 가 optional dual-write 를 수행하도록 반영 완료
3. local replay smoke에 slot density 검증 추가
  - 2026-05-02 replay summary에 `slot_rows / slot_services / slot_education_services` 출력 추가 완료
4. read-model 중 어떤 경계를 slot-first로 바꿀지 범위 확정

## 결론

다음 단계의 목표는 `service_taxonomies` 를 바로 없애는 것이 아닙니다.

목표는:

- `source-specific legacy summary row`
- `source-neutral canonical summary slot`

을 분리해,
새 source가 새로운 summary 축을 가져와도
먼저 generic row 저장으로 흡수할 수 있게 만드는 것입니다.

즉 추천/응답 계약은 유지하면서,
summary 저장 모델의 확장성만 먼저 높이는 설계입니다.
