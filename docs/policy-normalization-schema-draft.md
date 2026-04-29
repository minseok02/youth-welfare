# 정책 정규화 스키마 초안

이 문서는 [policy-normalization-research.md](./policy-normalization-research.md) 와 [policy-normalization-sample-spike.md](./policy-normalization-sample-spike.md) 의 결론을 실제 DB 스키마 초안으로 고정하기 위한 문서입니다.

목표는 아래 4개입니다.

1. 내부 canonical 구조를 `core / detail / taxonomy / facts / raw / AI enrichment` 로 확정
2. 기존 `WelfareService` 중심 구조와 어떻게 공존할지 결정
3. `Gov24 supportConditions` 와 `온통청년` 코드북을 어떻게 보관할지 결정
4. 이후 collect/read-model/recommendation 전환 작업의 기준 테이블을 고정

관련 문서:

- [policy-normalization-bridge-rules.md](./policy-normalization-bridge-rules.md)
- [policy-normalization-research.md](./policy-normalization-research.md)
- [policy-normalization-sample-spike.md](./policy-normalization-sample-spike.md)
- [recommendation-pipeline.md](./recommendation-pipeline.md)
- [collect-ops.md](./collect-ops.md)
- [phase-plan.md](./phase-plan.md)

## canonical decision

정책 도메인의 canonical 구조는 아래 6계층으로 확정합니다.

1. `core`
   - 서비스 공통 메타데이터
   - 제목, 요약, 기관, 기본 기간, 대표 URL, 상태
2. `detail`
   - 상세 본문
   - 지원내용, 신청방법, 선정기준, 제출서류, 문의처, 법적근거
3. `taxonomy`
   - 정책 분류축
   - 온통청년 major/mid, Gov24 서비스분야/사용자구분/지원유형, 제공방법, 호환 카테고리
4. `facts`
   - 구조화 eligibility / support facts
   - 연령, 소득, 학력, 취업, 가구특성, 장애/보훈 등
5. `raw`
   - source 원문 payload
6. `AI enrichment`
   - source-specific 자유서술 보강
   - canonical 공식 축을 대체하지 않고 보조로만 사용

이 결정은 다음을 의미합니다.

- `WelfareService` 는 당장 삭제하지 않는다.
- 다만 신규 canonical 저장 기준은 `service_taxonomies / service_taxonomy_terms / service_facts` 쪽으로 이동한다.
- `unifiedCategory` 는 canonical의 최종 원본이 아니라 **compatibility field** 로 취급한다.

## 공존 원칙

전환 초기에 유지되는 기존 테이블:

- `welfare_services`
- `welfare_service_details`
- `service_regions`
- `service_tags`
- `raw_api_payloads`

새로 붙는 sidecar 테이블:

- `normalization_code_sets`
- `normalization_codes`
- `service_taxonomies`
- `service_taxonomy_terms`
- `service_facts`

초기 read path는 여전히 `welfare_services` 기반으로 유지하되, 새 수집 저장 경계는 위 sidecar를 같이 채우는 구조를 목표로 합니다.

## 코드 저장 원칙

### 결론

`Gov24 supportConditions` 와 `온통청년` 코드북은 **Java enum이 아니라 DB code table** 로 보관합니다.

이유:

- 외부 코드북은 변경 가능성이 있음
- 코드 + 라벨 + 부모관계 + 정렬순서 + 활성/비활성 상태를 함께 보관해야 함
- 운영 중 라벨 보정, 코드 deprecated 처리, source별 sync 시점 추적이 필요함

## 테이블 1. `normalization_code_sets`

코드 집합 메타데이터.

예상 컬럼:

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | BIGINT PK | 내부 식별자 |
| `code_set_key` | VARCHAR(64) UNIQUE | 예: `YOUTH_MAJOR`, `YOUTH_MID`, `GOV24_SUPPORT_CONDITION` |
| `domain_type` | ENUM | `TAXONOMY`, `FACT` |
| `source_system` | ENUM | `YOUTH`, `GOV24`, `SYSTEM` |
| `description` | VARCHAR(255) | 코드셋 설명 |
| `version_label` | VARCHAR(50) | source 문서 버전/수집일 메모 |
| `is_active` | TINYINT(1) | 현재 사용 여부 |
| `created_at` | DATETIME | 생성시각 |
| `updated_at` | DATETIME | 수정시각 |

대표 `code_set_key` 예시:

- `YOUTH_MAJOR`
- `YOUTH_MID`
- `YOUTH_KEYWORD`
- `YOUTH_PROVISION_METHOD`
- `YOUTH_EMPLOYMENT_REQUIREMENT`
- `YOUTH_EDUCATION_REQUIREMENT`
- `YOUTH_SPECIAL_REQUIREMENT`
- `YOUTH_MARITAL_STATUS`
- `YOUTH_INCOME_CONDITION_TYPE`
- `GOV24_SERVICE_FIELD`
- `GOV24_USER_TYPE`
- `GOV24_BENEFIT_TYPE`
- `GOV24_SUPPORT_CONDITION`
- `SYSTEM_COMPAT_UNIFIED_CATEGORY`

## 테이블 2. `normalization_codes`

실제 코드 값 보관 테이블.

예상 컬럼:

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | BIGINT PK | 내부 식별자 |
| `code_set_key` | VARCHAR(64) FK | `normalization_code_sets.code_set_key` |
| `code` | VARCHAR(64) | 외부 코드 또는 내부 파생 코드 |
| `label` | VARCHAR(255) | 표시 라벨 |
| `parent_code` | VARCHAR(64) NULL | 계층형 코드의 부모 |
| `sort_order` | INT | 정렬순서 |
| `extra_json` | JSON | 설명, 문서 원문, 메모 |
| `is_active` | TINYINT(1) | 사용 여부 |
| `created_at` | DATETIME | 생성시각 |
| `updated_at` | DATETIME | 수정시각 |

유니크 제약:

- `uq_norm_code (code_set_key, code)`

예시:

- `code_set_key=GOV24_SUPPORT_CONDITION`, `code=JA0110`, `label=대상연령(시작)`
- `code_set_key=YOUTH_MAJOR`, `code=JOB`, `label=일자리`
- `code_set_key=SYSTEM_COMPAT_UNIFIED_CATEGORY`, `code=HOUSING`, `label=주거`

## 테이블 3. `service_taxonomies`

서비스당 1행을 기본으로 두는 canonical taxonomy summary 테이블.

역할:

- 현재 `unifiedCategory` 의 compatibility 역할 유지
- 주요 분류축을 빠르게 읽는 read-model 요약 row

예상 컬럼:

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `service_id` | BIGINT PK/FK | `welfare_services.id` |
| `primary_source_system` | ENUM | `YOUTH`, `BOKJIRO`, `GOV24`, `SYSTEM` |
| `compat_unified_category_code` | VARCHAR(64) | 현재 응답/우선순위 호환용 |
| `compat_unified_category_label` | VARCHAR(100) | `주거`, `일자리` 등 |
| `youth_major_code` | VARCHAR(64) | 온통청년 대분류 |
| `youth_major_label` | VARCHAR(100) | 대분류명 |
| `youth_mid_code` | VARCHAR(64) | 온통청년 중분류 |
| `youth_mid_label` | VARCHAR(100) | 중분류명 |
| `gov24_service_field_code` | VARCHAR(64) | Gov24 서비스분야 |
| `gov24_service_field_label` | VARCHAR(100) | 서비스분야명 |
| `gov24_user_type_code` | VARCHAR(64) | Gov24 사용자구분 |
| `gov24_user_type_label` | VARCHAR(100) | 사용자구분명 |
| `gov24_benefit_type_code` | VARCHAR(64) | Gov24 지원유형 |
| `gov24_benefit_type_label` | VARCHAR(100) | 지원유형명 |
| `provision_method_code` | VARCHAR(64) | 온통청년/파생 제공방법 |
| `provision_method_label` | VARCHAR(100) | 제공방법명 |
| `authority` | ENUM | `OFFICIAL`, `SYSTEM_DERIVED`, `AI_ENRICHED` |
| `confidence` | DECIMAL(4,3) NULL | 파생 taxonomy일 때만 사용 |
| `created_at` | DATETIME | 생성시각 |
| `updated_at` | DATETIME | 수정시각 |

핵심 원칙:

- `compat_unified_category_*` 는 유지하되 canonical 원본이 아님
- `authority=OFFICIAL` 인 경우 source가 직접 준 분류만 저장
- `Gov24 -> youth category` 같은 브릿지 값은 `authority=SYSTEM_DERIVED` 로만 저장

## 테이블 4. `service_taxonomy_terms`

서비스당 다중 분류어/키워드/대상군 등을 보관하는 반복 테이블.

역할:

- 기존 `service_tags` 보다 의미를 더 세분화
- source가 여러 값을 주는 taxonomy 계열 필드를 raw하게 보관

예상 컬럼:

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | BIGINT PK | 내부 식별자 |
| `service_id` | BIGINT FK | `welfare_services.id` |
| `term_group` | VARCHAR(64) | 예: `YOUTH_KEYWORD`, `LIFE_STAGE`, `TARGET_GROUP`, `GOV24_USER_TYPE` |
| `code_set_key` | VARCHAR(64) NULL | 연결된 code set |
| `term_code` | VARCHAR(64) NULL | 외부/내부 코드 |
| `term_label` | VARCHAR(255) NOT NULL | 표시 라벨 |
| `source_field` | VARCHAR(100) | 예: `plcyKywdNm`, `intrsThemaArray` |
| `authority` | ENUM | `OFFICIAL`, `SYSTEM_DERIVED`, `AI_ENRICHED` |
| `sort_order` | INT | 입력 순서 |
| `created_at` | DATETIME | 생성시각 |
| `updated_at` | DATETIME | 수정시각 |

유니크 제약:

- `uq_service_term (service_id, term_group, term_code, term_label, authority)`

용도 예시:

- 온통청년 `정책키워드`
- 복지로 `life stage / interest theme / target group`
- Gov24 `사용자구분` 복수 항목
- system-derived `compatibility category signal`

## 테이블 5. `service_facts`

구조화 eligibility / support signal의 실제 저장 테이블.

역할:

- hard filter의 원본
- rule scoring의 structured signal
- AI enrichment 결과의 별도 authority 저장

예상 컬럼:

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | BIGINT PK | 내부 식별자 |
| `service_id` | BIGINT FK | `welfare_services.id` |
| `fact_group` | VARCHAR(64) | 예: `AGE`, `INCOME`, `EMPLOYMENT`, `EDUCATION`, `HOUSEHOLD`, `SPECIAL_GROUP` |
| `fact_code_set_key` | VARCHAR(64) NULL | `GOV24_SUPPORT_CONDITION` 등 |
| `fact_code` | VARCHAR(64) NULL | 예: `JA0203`, `UNEMPLOYED`, `YOUTH_MAJOR_JOB` |
| `fact_label` | VARCHAR(255) | 표시 라벨 |
| `operator` | ENUM | `EQ`, `GTE`, `LTE`, `RANGE`, `FLAG`, `MEMBER` |
| `value_type` | ENUM | `BOOLEAN`, `INTEGER`, `DECIMAL`, `STRING`, `DATE` |
| `bool_value` | TINYINT(1) NULL | 플래그 값 |
| `int_value` | INT NULL | 단일 정수 값 |
| `decimal_value` | DECIMAL(12,2) NULL | 단일 숫자 값 |
| `text_value` | VARCHAR(255) NULL | 단일 문자열 값 |
| `date_value` | DATE NULL | 단일 날짜 값 |
| `range_min_int` | INT NULL | 범위 최소 |
| `range_max_int` | INT NULL | 범위 최대 |
| `unit` | VARCHAR(32) NULL | `%`, `원`, `세` 등 |
| `source_field` | VARCHAR(100) | 예: `sprtTrgtMinAge`, `JA0110`, `servDgst` |
| `authority` | ENUM | `OFFICIAL`, `RULE_DERIVED`, `AI_ENRICHED` |
| `confidence` | DECIMAL(4,3) NULL | `RULE_DERIVED`, `AI_ENRICHED` 에서 사용 |
| `raw_value` | VARCHAR(255) NULL | 원문 값 snapshot |
| `evidence_text` | TEXT NULL | 텍스트 추출 근거 |
| `created_at` | DATETIME | 생성시각 |
| `updated_at` | DATETIME | 수정시각 |

인덱스:

- `(service_id, fact_group)`
- `(fact_group, fact_code)`
- `(authority, fact_group, fact_code)`
- `(fact_group, range_min_int, range_max_int)` for age/income range filters

대표 fact 예시:

- `fact_group=AGE`, `operator=RANGE`, `range_min_int=19`, `range_max_int=34`, `unit=세`
- `fact_group=INCOME`, `fact_code=JA0203`, `operator=FLAG`, `bool_value=1`
- `fact_group=EMPLOYMENT`, `fact_code=JA0327`, `operator=FLAG`, `bool_value=1`
- `fact_group=HOUSEHOLD`, `fact_code=JA0412`, `operator=FLAG`, `bool_value=1`
- `fact_group=EDUCATION`, `fact_code=JA0320`, `operator=FLAG`, `bool_value=1`

## 테이블 간 역할 분리

### `service_tags` 와의 관계

전환 초기:

- `service_tags`
  - 검색/표시/기존 추천 호환
- `service_taxonomy_terms`
  - canonical taxonomy 다중값
- `service_facts`
  - hard filter / structured rule signal

최종 목표:

- `service_tags` 는 lightweight display/search tag 로 축소
- 추천의 핵심 pass/fail 은 `service_facts`
- 분류/priority 는 `service_taxonomies`

### `welfare_services` 와의 관계

초기에는 `welfare_services` 가 기존 응답/검색/추천 SQL의 anchor row를 유지합니다.

즉:

- canonical의 anchor PK는 계속 `welfare_services.id`
- 새 sidecar는 전부 `service_id` FK 로 붙음
- `welfare_services` 의 `unified_category`, `min_age`, `max_age` 는 전환 중 compatibility read model 로만 간주

## source별 저장 원칙

### 온통청년

- `service_taxonomies`
  - youth major/mid 직접 저장
- `service_taxonomy_terms`
  - youth keywords
- `service_facts`
  - age/income 직접 저장
- `detail`
  - 약함

### 복지로

- `service_taxonomies`
  - interest theme 기반 compatibility category 보조
- `service_taxonomy_terms`
  - life stage / target group / interest theme 저장
- `service_facts`
  - 본문 규칙 추출 결과만 `RULE_DERIVED`
- `detail`
  - 매우 중요

### Gov24/보조금24

- `service_taxonomies`
  - service field / user type / benefit type 공식 저장
- `service_taxonomy_terms`
  - 필요 시 사용자구분 다중값 저장
- `service_facts`
  - supportConditions 공식 코드 직접 저장
- `detail`
  - 서류/문의처/법적근거 강함

## migration 순서 초안

1. code tables 생성
   - `normalization_code_sets`
   - `normalization_codes`
2. sidecar tables 생성
   - `service_taxonomies`
   - `service_taxonomy_terms`
   - `service_facts`
3. 온통청년/복지로 기존 row 기준 backfill
4. collect 저장 경계에서 sidecar dual-write 시작
5. read-model이 sidecar 사용 시작
6. recommendation hard filter 일부 이관
7. 기존 `service_tags` / `unified_category` 의존 축소

## 지금 결정한 것

- canonical 구조는 `core/detail/taxonomy/facts/raw/AI enrichment`
- `Gov24 supportConditions` 와 `온통청년` 코드북은 Java enum이 아니라 DB code table
- `service_taxonomies` 는 service당 1행 summary
- `service_taxonomy_terms` 는 다중 taxonomy term row
- `service_facts` 는 hard filter와 structured signal의 실제 원본
- `unifiedCategory` 는 canonical 원본이 아니라 compatibility layer

## 다음 작업

1. `Gov24 service field / user type / benefit type -> compatibility unifiedCategory / youth taxonomy bridge` 규칙 초안 작성
2. `복지로 list/detail text -> facts fallback extraction` 허용 범위와 authority 기준 작성
3. collect saver가 새 sidecar를 어떤 aggregate로 저장할지 내부 DTO 초안 작성
4. `WelfareServiceRepository.findCandidates*` 를 어떤 fact_group부터 치환할지 우선순위 결정
