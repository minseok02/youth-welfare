# `Gov24` blocked track 현재 상태

문서군 진입점: [policy-docs-index.md](./policy-docs-index.md)

관련 문서:

- [policy-next-active-track-priority.md](./policy-next-active-track-priority.md)
- [policy-local-closeout-pending-inventory.md](./policy-local-closeout-pending-inventory.md)
- [policy-gov24-implementation-checklist.md](./policy-gov24-implementation-checklist.md)
- [policy-gov24-runtime-audit-runbook.md](./policy-gov24-runtime-audit-runbook.md)
- [policy-gov24-support-unmapped-inventory.md](./policy-gov24-support-unmapped-inventory.md)
- [policy-gov24-canonical-mapping-draft.md](./policy-gov24-canonical-mapping-draft.md)
- [policy-normalization-blocked-sql-reopen-priority.md](../history/policy/policy-normalization-blocked-sql-reopen-priority.md)
- [policy-normalization-gov24-request-package-checklist.md](../history/policy/policy-normalization-gov24-request-package-checklist.md)
- [policy-normalization-gov24-codebook-request-template.md](../history/policy/policy-normalization-gov24-codebook-request-template.md)
- [policy-normalization-gov24-support-condition-request-template.md](../history/policy/policy-normalization-gov24-support-condition-request-template.md)
- [policy-normalization-current-state.md](./policy-normalization-current-state.md)

## 목적

현재 `Gov24` 관련 pending을

- 왜 지금 active 구현 트랙이 아닌지
- 다시 열 조건이 무엇인지
- 자료가 오면 어떤 순서로 reopen 하는지

한 문서에서 바로 보게 정리합니다.

## 현재 결론

현재 `Gov24` 는

- **runtime collect/source 트랙은 active**
- **label-first canonical promotion 트랙도 active**
- **hard import/backfill / stable-code 트랙만 blocked 또는 deferred**

상태입니다.

즉 현재 로컬에서 이미 진행 가능한 축은

1. `serviceList -> detail -> supportConditions` runtime collect 확장
2. raw/detail/fact 적재 및 품질 점검
3. collect runtime 안정화

이고, 여전히 stable code/import-backfill 또는 제품 범위 결정이 필요한 축은

1. `GOV24_SERVICE_FIELD`
2. `GOV24_USER_TYPE`
3. `GOV24_BENEFIT_TYPE`
4. `GOV24_SUPPORT_CONDITION` full-scope import/backfill

입니다.

현재 `Gov24` practical next action 은 세 갈래입니다.

- runtime 쪽: backlog closeout 기준을 유지하고 샘플 품질을 점검
- support fact gap 쪽: unmapped official support code inventory를 기준으로 deferred 판단 유지
- normalization 쪽: string label을 canonical term으로 올리는 내부 규칙 고정, 또는 stable code/import-backfill 여부 판정

## 지금 blocked 인 이유

### 1. runtime collect와 normalization reopen은 다른 문제다

현재 local DB 기준 `Gov24` runtime collect는 이미 active 입니다.

- `serviceList=10942`
- `detail=10942`
- `support raw=10942`
- `support facts=163806`
- `support fact service coverage=9963 / 10942 (91.1%)`

따라서 `Gov24` 가 source row 자체가 없는 상태는 아닙니다.

다만 summary slot / import-backfill 기준으로는 현재도

- `slot_services_GOV24_SERVICE_FIELD=0`
- `slot_services_GOV24_USER_TYPE=0`
- `slot_services_GOV24_BENEFIT_TYPE=0`
- `slot_rows_GOV24_SERVICE_FIELD=0`
- `slot_rows_GOV24_USER_TYPE=0`
- `slot_rows_GOV24_BENEFIT_TYPE=0`

이고, 이것은 runtime collect 부재가 아니라 **official code import/backfill을 아직 열지 않은 상태**를 반영합니다.

### 2. `supportConditions` 는 부분 구현 상태이고 full-scope 만 남아 있다

현재 `supportConditions` 는 완전 blocked 가 아닙니다.

runtime fact extractor가 이미 읽는 축:

- 성별: `JA0101`, `JA0102`
- 연령: `JA0110`, `JA0111`
- 소득: `JA0201~JA0205`
- 교육: `JA0317~JA0320`
- 고용: `JA0326`, `JA0327`
- 가구: `JA0401~JA0404`, `JA0411~JA0414`
- 특수대상: `JA0328~JA0330`

실제 local DB 기준 `service_facts.fact_code_set_key='GOV24_SUPPORT_CONDITION'` 로 적재된 서비스는
`9963 / 10942 (91.1%)` 입니다.

즉 현재 `supportConditions` 의 blocked 의미는

- raw가 없다
- 코드 의미를 전혀 모른다

가 아니라,

- 현재 제품 경계에서 쓰지 않는 사업체/업종/창업 축까지 full-scope 로 올릴지 아직 결정하지 않았다

쪽에 가깝습니다.

### 3. label 3종은 더 이상 “공개 codebook 대기” 상태로 보지 않는다

`서비스분야 / 사용자구분 / 지원유형` 3축은 current API 기준

- field name 은 확인됐고
- observed raw inventory 도 local DB 에 존재하며
- 공개 공식 Swagger 기준으로도 이 값들은 enum/codebook 이 아니라 `string` 필드다

즉 현재 active 과제는 “외부 codebook이 오기 전까지 멈춤”이 아니라,
**공식 codebook 부재를 전제로 raw exact label + allowlist token split 규칙을 내부 canonical 층에 어떻게 고정할지 정하는 것**이다.

반대로 계속 blocked 로 남는 것은

- 이 3축의 stable code/import-backfill SQL
- `supportConditions` full-scope business/industry/startup code 승격

같은 더 강한 구조화 단계다.

## reopen 조건

아래 중 하나가 오면 `Gov24` stable-code blocked SQL 을 다시 active 로 올립니다.

- current API 기준 공식 codebook / enum / schema export 확보
- 운영자가 current API 기준 inventory를 전달
- 내부에서 current raw inventory 기준 canonical 매핑 규칙을 승인

그 전까지는 blocked/backlog 유지가 기본입니다.

## 자료가 오면 다시 여는 순서

우선순위는 아래입니다.

1. `GOV24_SERVICE_FIELD / USER_TYPE / BENEFIT_TYPE`
2. `GOV24_SUPPORT_CONDITION` full-scope inventory/backfill

이유:

- label 3종이 canonical onboarding 기준선과 직접 연결됨
- `supportConditions` 는 representative subset 이 아니라 이미 partial runtime fact가 존재하므로,
  남은 full-scope 를 제품 경계에 맞게 넓힐지 별도로 판단해야 함

## 발송/수신 패키지 기준

발송은 one package로 묶고,
판정은 two tracks로 나눕니다.

### track A

- `serviceField`
- `userType`
- `benefitType`

판정 기준:

- field name 있음
- code 있음
- official label 있음
- current API 기준 자료임이 분명함

### track B

- `supportConditions` full-scope

판정 기준:

- code 있음
- official label 있음
- current runtime extractor가 아직 읽지 않는 code 군도 제품적으로 의미가 분명함
- current API 기준 자료임이 분명함

## practical next action

현재 practical next action 은 아래 셋 중 하나입니다.

1. `서비스분야 / 사용자구분 / 지원유형` label-first canonical 규칙을 코드/문서에 고정
2. runtime collect + quality audit 기준선을 계속 유지
3. stable code/import-backfill 또는 `supportConditions` full-scope 확장은 deferred/blocked 로 유지

즉 label-first canonical 승격은 내부 문서/코드로 진행 가능하지만,
stable code/import-backfill 은 여전히 외부 source-of-truth 없이는 unblock 되지 않습니다.

단, `Gov24` runtime collect 자체를 검토/구현할 때는
[policy-gov24-implementation-checklist.md](./policy-gov24-implementation-checklist.md)
기준으로 `serviceList -> detail -> supportConditions` 범위를 분리해서 진행합니다.

runtime collect가 이미 붙은 뒤 coverage/shape/null-heavy sample을 다시 볼 때는
[policy-gov24-runtime-audit-runbook.md](./policy-gov24-runtime-audit-runbook.md)
기준으로 closeout, raw shape, missing fact sample 순서를 고정합니다.

현재 local runtime 기준 practical status:

- `serviceList` 는 `10942` 건까지 적재됨
- `detail` raw/detail row 는 `10942` 건까지 적재됨
- `supportConditions` raw 는 `10942` 건까지 적재됨
- `supportConditions` fact 는 `163806` row, 서비스 기준 coverage는 `9963 / 10942 (91.1%)`
- `.env` / `docker-compose` 는 `PUBLIC_DATA_PORTAL_API_KEY` 기준으로 통합됨
- `detail/support` fetch에는 lightweight retry가 적용됨
- `support raw` 는 모두 `{"서비스ID","서비스명","conditions":{...}}` shape로 통일됨
- raw는 있지만 fact가 없는 나머지 `979`건은 `Gov24` runtime audit runbook 기준으로 다시 나눠서 본다.
  - 현재 local audit 기준 `missing_no_support_raw=0`, `missing_all_null_payload=0`
  - `missing_unmapped_only_payload=979`, `missing_mapped_signal_payload=0`
  - 즉 지금 남은 갭은 저장 실패보다 `현재 extractor가 아직 읽지 않는 official support code-only payload` 로 해석하는 편이 맞다
- `missing fact` 집합을 다시 보면 `JA0301~JA0303` 같은 개인 eligibility code 는 `0건` 이고,
  사실상 남은 축은 `JA210*`, `JA220*`, `JA120*`, `JA1299/JA2299`, `JA110*` 같은 사업체/업종/창업 코드다
- 개인 eligibility 쪽 잔여는 `JA0313~JA0316` 이 `2/1/1/1건`, `JA0322/JA0410` 이 `2/3건` 수준으로 매우 작다
- `JA210*`, `JA220*`, `JA120*`, `JA1299/JA2299`, `JA110*` 중심 unmapped inventory는
  [policy-gov24-support-unmapped-inventory.md](./policy-gov24-support-unmapped-inventory.md)
  에 따로 정리했고, 현재 제품 경계 기준 판단은 `deferred` 다

즉 current blocked 의미는 한 층으로 줄었습니다.

1. `GOV24_SERVICE_FIELD / USER_TYPE / BENEFIT_TYPE` hard import/backfill 트랙은 여전히 stable code/schema 기준으로 blocked
2. `GOV24_SUPPORT_CONDITION` 은 partial runtime fact는 active, full-scope 확장만 deferred/blocked

## 요약

1. `Gov24` runtime collect 트랙은 현재 active 다.
2. `GOV24_SUPPORT_CONDITION` 은 partial runtime fact가 이미 active 이고, full-scope 확장만 남아 있다.
3. `서비스분야 / 사용자구분 / 지원유형` label-first canonical 승격은 active 이고, hard import/backfill 만 blocked track이다.
4. `GOV24_* = 0` 은 현재 slot/import 기준에서는 expected result 이다.
5. `supportConditions` gap의 중심인 사업체/업종/창업 상태 code는 현재 제품 경계 기준으로 `deferred` 다.
6. stable code/import-backfill 을 다시 열 조건은 current API 기준 codebook/schema 확보, 또는 제품이 더 강한 구조화를 실제로 요구하기 시작하는 것이다.
7. 현재 active 순서는 label 3종 canonical term 고정이 먼저이고, `supportConditions` full-scope 와 stable code/import-backfill 은 그다음이다.
