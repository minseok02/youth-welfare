# `Gov24` blocked track 현재 상태

문서군 진입점: [policy-docs-index.md](./policy-docs-index.md)

관련 문서:

- [policy-next-active-track-priority.md](./policy-next-active-track-priority.md)
- [policy-local-closeout-pending-inventory.md](./policy-local-closeout-pending-inventory.md)
- [policy-normalization-blocked-sql-reopen-priority.md](./history/policy/policy-normalization-blocked-sql-reopen-priority.md)
- [policy-normalization-gov24-request-package-checklist.md](./history/policy/policy-normalization-gov24-request-package-checklist.md)
- [policy-normalization-gov24-codebook-request-template.md](./history/policy/policy-normalization-gov24-codebook-request-template.md)
- [policy-normalization-gov24-support-condition-request-template.md](./history/policy/policy-normalization-gov24-support-condition-request-template.md)
- [policy-normalization-current-state.md](./policy-normalization-current-state.md)

## 목적

현재 `Gov24` 관련 pending을

- 왜 지금 active 구현 트랙이 아닌지
- 다시 열 조건이 무엇인지
- 자료가 오면 어떤 순서로 reopen 하는지

한 문서에서 바로 보게 정리합니다.

## 현재 결론

현재 `Gov24` 는 **active runtime collect/source 트랙이 아니라 blocked import/backfill 트랙** 입니다.

즉 지금 당장 로컬에서 더 구현할 기본 축은 `Gov24` SQL 이 아니라

1. 로컬 기능/구조 검증
2. smoke/current-state closeout
3. 프론트 연동 전 contract 정리

입니다.

`Gov24` 쪽 다음 실제 액션은 내부 구현이 아니라
**provider/operator 응답 수신 또는 current API schema/codebook 확보** 입니다.

## 지금 inactive 인 이유

### 1. local snapshot에 `Gov24` source row 자체가 없다

현재 local DB 기준 source 분포:

- `YOUTH=2364`
- `BOKJIRO_CENTRAL=119`
- `BOKJIRO_LOCAL=1225`

즉 runtime collect 기준으로는 아직 `Gov24` source 적재가 없습니다.

그래서 현재

- `slot_services_GOV24_SERVICE_FIELD=0`
- `slot_services_GOV24_USER_TYPE=0`
- `slot_services_GOV24_BENEFIT_TYPE=0`
- `slot_rows_GOV24_SERVICE_FIELD=0`
- `slot_rows_GOV24_USER_TYPE=0`
- `slot_rows_GOV24_BENEFIT_TYPE=0`

인 것은 현재 구조 버그라기보다,
populate 할 source row 또는 별도 import/backfill 이 없는 상태를 반영한 결과입니다.

### 2. source-of-truth 가 아직 외부 응답 경계에 있다

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

즉 지금은 코드보다 source-of-truth 확보가 먼저입니다.

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

현재 practical next action 은 아래 둘 중 하나입니다.

1. 실제 provider/operator 에 request package 발송
2. 응답 수신 전까지는 local-first closeout 트랙 계속 진행

즉 내부 문서/코드만 더 쌓는 것으로는 unblock 되지 않습니다.

## 요약

1. `Gov24` 는 현재 active source 구현 트랙이 아니라 blocked import/backfill 트랙이다.
2. `GOV24_* = 0` 은 현재 로컬 snapshot에서 expected result 이다.
3. 다시 열 조건은 current API 기준 codebook/schema 확보이다.
4. 자료가 오면 label 3종을 먼저, `supportConditions` full inventory를 그다음 순서로 reopen 한다.
