# 장학금 제도 Row / Reference Matrix 분리 초안

관련 문서:

- [policy-source-onboarding-playbook.md](../../policy/policy-source-onboarding-playbook.md)
- [policy-normalization-schema-draft.md](./policy-normalization-schema-draft.md)
- [policy-source-canonical-onboarding-priority.md](../../policy/policy-source-canonical-onboarding-priority.md)
- [phase-plan.md](../../phase-plan.md)

## 목적

한국장학재단/국가장학금 계열 source를 받을 때,

- `제도/상품 row`
- `지원가능대학/학기/지원구간/금액표`

를 어디서 분리할지 고정합니다.

핵심은 장학금 상품 1건의 의미를 유지하면서도,
대학/학기/구간 같은 matrix 정보를 잃지 않는 것입니다.

## 결론

장학금 계열은 1차에서 아래 두 층으로 분리합니다.

1. 제도 row
   - `welfare_services + sidecars`
2. reference matrix
   - 별도 `scholarship_reference_sets`
   - 별도 `scholarship_reference_rows`

즉 장학금 상품 1건을 대학/학기별로 `welfare_services` 에 flatten 하지 않습니다.

## 왜 분리하는가

장학금/학자금 계열은 다음 두 종류의 정보가 섞여 있습니다.

### 1. 제도 row

예:

- 국가장학금 I유형
- 다자녀 국가장학금
- 국가근로장학금
- 학자금대출 이자지원

이건 정책형 row입니다.

특징:

- 사용자에게 “정책 카드 1건” 으로 보여줄 수 있음
- 요약, 지원대상, 신청기간, 지원내용, 기관, 기본 eligibility가 있음
- 추천/북마크/CTR 의미가 유지됨

### 2. reference matrix

예:

- 지원가능 대학 목록
- 학기별 지원금액 표
- 소득구간 경곗값
- 상품별 세부 가능조건

이건 reference형입니다.

특징:

- 직접 추천 카드 row로 보여주기보다 제도 row를 보강함
- 대학/학기/구간 차원으로 파생 row가 급격히 늘어날 수 있음
- `welfare_services` 에 flatten 하면 정책 1건 의미가 깨짐

## 제도 row 쪽 저장

장학금 상품 자체는 canonical 정책 row로 저장합니다.

예상 방향:

- `welfare_services`
- `service_taxonomies`
- `service_taxonomy_terms`
- `service_facts`
- `raw_api_payloads`

예상 해석:

- `compat_unified_category`
  - 기본은 `교육·직업훈련`
  - 일부 `학자금대출 이자지원` 류는 `금융·생활지원` bridge 후보로 남길 수 있음
- `service_facts`
  - 소득구간/학점요건/재학생 여부/복학생 여부/다자녀 여부/대학유형 여부 등

하지만 대학별/학기별 상세 테이블은 여기로 넣지 않습니다.

## reference matrix 쪽 저장

## 1. `scholarship_reference_sets`

장학금 제도 row에 연결되는 reference 묶음 header입니다.

예상 컬럼:

- `id`
- `service_id`
- `reference_kind`
  - `ELIGIBILITY_MATRIX`
  - `UNIVERSITY_LIST`
  - `SEMESTER_AMOUNT_TABLE`
  - `INCOME_BRACKET_TABLE`
  - `COLLEGE_TYPE_TABLE`
- `source_type`
- `title`
- `version_label`
- `effective_start_date`
- `effective_end_date`
- `raw_payload_id`
- `collected_at`
- `last_seen_at`

의미:

- 한 장학금 제도 row에 여러 reference set이 붙을 수 있음
- 예: 국가장학금 I유형 하나에
  - 소득구간표
  - 학기별 금액표
  - 대학유형 가능조건
  를 각각 다른 set으로 둘 수 있음

## 2. `scholarship_reference_rows`

set 아래의 실제 matrix row입니다.

예상 컬럼:

- `id`
- `reference_set_id`
- `row_order`
- `dimension_key_1`
- `dimension_value_1`
- `dimension_key_2`
- `dimension_value_2`
- `dimension_key_3`
- `dimension_value_3`
- `amount_text`
- `amount_value`
- `eligibility_text`
- `note_text`

예상 dimension 예시:

- `university_name`
- `semester`
- `income_bracket`
- `student_type`
- `college_type`

이 구조는 matrix를 너무 강하게 정규화하지 않으면서도,
row별 비교/표시가 가능하도록 최소한만 공통화한 형태입니다.

## 왜 `dimension_key/value` 형태를 먼저 쓰는가

장학금 reference는 source마다 축이 다릅니다.

예:

- 어떤 표는 `학기 x 소득구간`
- 어떤 표는 `대학 x 학년`
- 어떤 표는 `대상유형 x 지원금액`

처음부터 고정 컬럼으로 박으면:

- 대부분 null 이 늘고
- 새 source가 들어올 때마다 컬럼 추가가 반복됩니다.

따라서 1차는:

- set level에서 `reference_kind` 로 묶고
- row level에서는 `dimension_key/value` 1~3축으로 받는 쪽이 안전합니다.

## canonical fact와의 경계

reference matrix 전체를 `service_facts` 로 복사하지 않습니다.

원칙:

- 제도 row의 공통 eligibility만 `service_facts`
- 대학/학기/구간별 variation은 `scholarship_reference_*`

예:

- `소득 8구간 이하` 같은 전역 조건
  - `service_facts` 가능
- `A대학은 1학기 350만원, B대학은 2학기 250만원`
  - reference matrix에만 저장

즉 `service_facts` 는 추천/필터 중심,
reference matrix는 상세 안내/추후 계산 중심으로 분리합니다.

## current phase에서 일부러 안 여는 것

이번 초안에서는 아래를 같이 열지 않습니다.

- 장학금 reference matrix를 추천 hard filter에 직접 연결
- 대학별 row를 `welfare_services` 로 파생 생성
- 장학금 전용 별도 추천 lane
- matrix row를 모두 typed table로 세분화

이 항목들은 실제 source inventory를 본 뒤에 다시 엽니다.

## future reopen 후보

나중에 다시 열 수 있는 것:

- `scholarship_reference_sets` 를 kind별 typed table로 세분화
- 지원가능대학 matrix를 대학 메타데이터와 join
- 장학금 reference 기반 explanation 강화
- 장학금 전용 eligibility evaluator

## 요약

1. 장학금 상품 자체는 canonical 정책 row로 저장합니다.
2. 지원가능대학/학기/지원구간/금액표는 reference matrix로 분리합니다.
3. reference matrix는 `scholarship_reference_sets + scholarship_reference_rows` 2층으로 받습니다.
4. `service_facts` 는 전역 eligibility만 받고, matrix variation은 넣지 않습니다.
5. 장학금 상품 1건을 대학/학기별 `welfare_services` 파생 row로 늘리지 않습니다.
