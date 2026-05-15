# `Gov24` blocked track 현재 상태

문서군 진입점: [policy-docs-index.md](./policy-docs-index.md)

관련 문서:

- [policy-next-active-track-priority.md](./policy-next-active-track-priority.md)
- [policy-local-closeout-pending-inventory.md](./policy-local-closeout-pending-inventory.md)
- [policy-gov24-implementation-checklist.md](./policy-gov24-implementation-checklist.md)
- [policy-gov24-runtime-audit-runbook.md](./policy-gov24-runtime-audit-runbook.md)
- [policy-gov24-support-unmapped-inventory.md](./policy-gov24-support-unmapped-inventory.md)
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

현재 `Gov24` 는 **runtime collect/source 트랙은 active**, **hard import/backfill 트랙은 blocked** 상태입니다.

즉 현재 로컬에서 이미 진행 가능한 축은

1. `serviceList -> detail -> supportConditions` runtime collect 확장
2. raw/detail/fact 적재 및 품질 점검
3. collect runtime 안정화

이고, 여전히 외부 자료가 필요한 축은

1. `GOV24_SERVICE_FIELD`
2. `GOV24_USER_TYPE`
3. `GOV24_BENEFIT_TYPE`
4. `GOV24_SUPPORT_CONDITION` full inventory import/backfill

입니다.

현재 `Gov24` practical next action 은 세 갈래입니다.

- runtime 쪽: backlog closeout 기준을 유지하고 샘플 품질을 점검
- support fact gap 쪽: unmapped official support code inventory를 기준으로 deferred 판단 유지
- normalization 쪽: provider/operator 응답 수신 또는 current API schema/codebook 확보

## 지금 blocked 인 이유

### 1. runtime collect와 normalization reopen은 다른 문제다

현재 local DB 기준 `Gov24` runtime collect는 이미 active 입니다.

- `serviceList=10937`
- `detail=10937`
- `support raw=10937`
- `support facts=163743`
- `support fact service coverage=9959 / 10937 (91.1%)`

따라서 `Gov24` 가 source row 자체가 없는 상태는 아닙니다.

다만 summary slot / import-backfill 기준으로는 현재도

- `slot_services_GOV24_SERVICE_FIELD=0`
- `slot_services_GOV24_USER_TYPE=0`
- `slot_services_GOV24_BENEFIT_TYPE=0`
- `slot_rows_GOV24_SERVICE_FIELD=0`
- `slot_rows_GOV24_USER_TYPE=0`
- `slot_rows_GOV24_BENEFIT_TYPE=0`

이고, 이것은 runtime collect 부재가 아니라 **official code import/backfill을 아직 열지 않은 상태**를 반영합니다.

### 2. source-of-truth 가 여전히 외부 응답 경계에 있다

현재 blocked 축:

- `GOV24_SERVICE_FIELD`
- `GOV24_USER_TYPE`
- `GOV24_BENEFIT_TYPE`
- `GOV24_SUPPORT_CONDITION`

이 축들은 current API 기준 공식

- field name
- code
- official label

자료가 있어야 import/backfill SQL 을 다시 열 수 있습니다.

즉 runtime collect와 별개로, hard taxonomy/import-backfill 쪽은 여전히 코드보다 source-of-truth 확보가 먼저입니다.

## reopen 조건

아래 중 하나가 오면 `Gov24` blocked SQL 을 다시 active 로 올립니다.

- provider/operator codebook 응답 수신
- current Swagger/schema export 확보
- 운영자가 current API 기준 inventory를 전달

그 전까지는 blocked/backlog 유지가 기본입니다.

## 자료가 오면 다시 여는 순서

우선순위는 아래입니다.

1. `GOV24_SERVICE_FIELD / USER_TYPE / BENEFIT_TYPE`
2. `GOV24_SUPPORT_CONDITION` full inventory/backfill

이유:

- label 3종이 canonical onboarding 기준선과 직접 연결됨
- `supportConditions` 는 representative subset 과 full inventory 판단을 따로 봐야 함

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

- `supportConditions`

판정 기준:

- code 있음
- official label 있음
- representative subset 이 아니라 full inventory 로 볼 수 있음
- current API 기준 자료임이 분명함

## practical next action

현재 practical next action 은 아래 셋 중 하나입니다.

1. 실제 provider/operator 에 request package 발송
2. 응답 수신 전까지 runtime collect + quality audit 계속 진행
3. 응답 수신 전까지는 local-first closeout 트랙 계속 진행

즉 내부 문서/코드만 더 쌓는 것으로는 unblock 되지 않습니다.

단, `Gov24` runtime collect 자체를 검토/구현할 때는
[policy-gov24-implementation-checklist.md](./policy-gov24-implementation-checklist.md)
기준으로 `serviceList -> detail -> supportConditions` 범위를 분리해서 진행합니다.

runtime collect가 이미 붙은 뒤 coverage/shape/null-heavy sample을 다시 볼 때는
[policy-gov24-runtime-audit-runbook.md](./policy-gov24-runtime-audit-runbook.md)
기준으로 closeout, raw shape, missing fact sample 순서를 고정합니다.

현재 local runtime 기준 practical status:

- `serviceList` 는 `10937` 건까지 적재됨
- `detail` raw/detail row 는 `10937` 건까지 적재됨
- `supportConditions` raw 는 `10937` 건까지 적재됨
- `supportConditions` fact 는 `163743` row, 서비스 기준 coverage는 `9959 / 10937 (91.1%)`
- `.env` / `docker-compose` 는 `PUBLIC_DATA_PORTAL_API_KEY` 기준으로 통합됨
- `detail/support` fetch에는 lightweight retry가 적용됨
- `support raw` 는 모두 `{"서비스ID","서비스명","conditions":{...}}` shape로 통일됨
- raw는 있지만 fact가 없는 나머지 `978`건은 `Gov24` runtime audit runbook 기준으로 다시 나눠서 본다.
  - 현재 local audit 기준 `missing_no_support_raw=0`, `missing_all_null_payload=0`
  - `missing_unmapped_only_payload=978`, `missing_mapped_signal_payload=0`
  - 즉 지금 남은 갭은 저장 실패보다 `현재 extractor가 아직 읽지 않는 official support code-only payload` 로 해석하는 편이 맞다
- `JA210*`, `JA220*`, `JA120*`, `JA1299/JA2299`, `JA110*` 중심 unmapped inventory는
  [policy-gov24-support-unmapped-inventory.md](./policy-gov24-support-unmapped-inventory.md)
  에 따로 정리했고, 현재 제품 경계 기준 판단은 `deferred` 다

즉 current blocked 의미는 한 층으로 줄었습니다.

1. hard import/backfill 트랙만 여전히 codebook/schema 기준으로 blocked

## 요약

1. `Gov24` runtime collect 트랙은 현재 active 다.
2. 다만 `GOV24_*` hard import/backfill 은 여전히 blocked track이다.
3. `GOV24_* = 0` 은 현재 slot/import 기준에서는 expected result 이다.
4. `supportConditions` gap의 중심인 사업체/업종/창업 상태 code는 현재 제품 경계 기준으로 `deferred` 다.
5. 다시 열 조건은 current API 기준 codebook/schema 확보 또는 제품이 사업체/업종 축을 실제로 소비하기 시작하는 것이다.
6. 자료가 오면 label 3종을 먼저, `supportConditions` full inventory를 그다음 순서로 reopen 한다.
