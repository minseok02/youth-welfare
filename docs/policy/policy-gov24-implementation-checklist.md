# `Gov24` 구현 체크리스트

문서군 진입점: [policy-docs-index.md](./policy-docs-index.md)

관련 문서:

- [policy-gov24-blocked-track-status.md](./policy-gov24-blocked-track-status.md)
- [policy-source-onboarding-checklist.md](./policy-source-onboarding-checklist.md)
- [policy-source-code-entrypoints.md](./policy-source-code-entrypoints.md)
- [policy-source-onboarding-architecture.md](./policy-source-onboarding-architecture.md)

## 목적

이 문서는 `Gov24` 작업을 할 때

- 이번 턴에서 어디까지 할지
- 무엇을 건드리지 말아야 하는지
- 다음 단계로 넘어갈 조건이 무엇인지

를 짧게 고정하기 위한 작업 계약입니다.

긴 설계 배경보다 **작업 범위 통제**가 목적입니다.

## 사용 규칙

`Gov24` 관련 작업을 시작할 때는 먼저 아래 두 줄을 말하고 시작합니다.

1. 이번 턴 범위
2. 이번 턴에서 하지 않을 것

예:

```text
Gov24 체크리스트 기준으로 이번 턴 범위는 serviceList client/DTO/addapter 연결까지만 합니다.
이번 턴에서는 detail, supportConditions, taxonomy import, recommendation, 배치는 건드리지 않습니다.
```

위 선언이 없으면 작업 전에 이 문서를 다시 보고 범위를 먼저 고정합니다.

## 현재 기본 원칙

1. `Gov24` 는 우선 `정책형 source` 로 본다.
2. 1차 구현은 `runtime collect` 와 `raw/list 저장` 이다.
3. `detail`, `supportConditions`, taxonomy/fact 고도화는 분리된 다음 단계다.
4. `GOV24_SERVICE_FIELD`, `GOV24_USER_TYPE`, `GOV24_BENEFIT_TYPE` hard import/backfill 은 1차 범위가 아니다.
5. `compat unifiedCategory`, recommendation read-model, 배치 스케줄은 초기에 같이 열지 않는다.

## 절대 하지 말 것

1. `CollectBatchService`, controller, 상위 service 본문에 `if (source == GOV24)` 분기를 직접 흩뿌리지 않는다.
2. raw 저장 없이 바로 `welfare_services` 나 taxonomy만 채우지 않는다.
3. `serviceList`, `serviceDetail`, `supportConditions` 를 한 번에 같이 붙이지 않는다.
4. `서비스분야`, `사용자구분`, `지원유형` 을 처음부터 hard taxonomy로 확정하지 않는다.
5. `supportConditions` 를 정책 row처럼 별도 서비스로 저장하지 않는다.
6. `서비스ID` 대신 title 같은 불안정한 값을 identity로 쓰지 않는다.
7. 추천/read-model, 배치 스케줄, 운영 문서를 1차 구현과 같이 묶지 않는다.

## 단계별 체크리스트

## 1단계. env / config / source 등록

완료 조건:

- `PUBLIC_DATA_PORTAL_API_KEY` fallback으로 `Gov24` 도 기동 가능하다.
- `CollectSource`, `WelfareSourceTypeSupport` 에 `GOV24` 자리가 생긴다.
- 아직 collect 실행은 안 붙여도 된다.

이번 단계에서 허용:

- `.env.example`
- `application.yml`
- `docker-compose.yml`
- `CollectSource`
- `WelfareSourceTypeSupport`

이번 단계에서 금지:

- 실제 API 호출 구현
- mapper
- recommendation/read-model 수정

## 2단계. client / DTO / serviceList collect

완료 조건:

- `serviceList` 를 실제 호출할 수 있다.
- 응답 DTO가 고정된다.
- raw payload 저장이 된다.
- `welfare_services` upsert 까지 닫힌다.

이번 단계에서 허용:

- `Gov24Client`
- `Gov24ServiceListDto`
- `Gov24CollectSourceAdapter`
- `WelfareServiceMapper.fromGov24(...)`
- `CollectSourceRegistry` list binding

이번 단계에서 금지:

- detail
- supportConditions
- hard taxonomy import/backfill
- recommendation lane 변경

검증:

- 수동 admin collect 1회
- raw payload row 확인
- `welfare_services` source=`GOV24` 적재 확인

## 3단계. serviceDetail 연결

완료 조건:

- `서비스ID` 기준 detail fetch가 된다.
- detail raw 저장이 된다.
- 본문/링크/기관/법령 같은 상세 필드 보강이 된다.

이번 단계에서 허용:

- detail DTO/client
- detail collect adapter 또는 detail fetch service
- detail 기반 aggregate 보강

이번 단계에서 금지:

- supportConditions
- taxonomy import/backfill
- recommendation 가중치 조정

## 4단계. supportConditions 연결

완료 조건:

- `서비스ID` 기준 `supportConditions` fetch가 된다.
- raw 저장이 된다.
- fact/조건 보강 방향이 문서화된다.

이번 단계에서 허용:

- `Gov24SupportConditionsDto`
- supportConditions fetch/save
- sidecar/fact 보강 초안

이번 단계에서 금지:

- `GOV24_SERVICE_FIELD / USER_TYPE / BENEFIT_TYPE` hard import/backfill
- `supportConditions` 전체 inventory를 곧바로 SQL seed로 확정

## 5단계. taxonomy / fact 고도화

이 단계는 1차 구현이 아니다.

들어가기 전 확인:

- live payload를 충분히 봤는가
- label-first로도 현재 기능에 문제가 없는가
- official inventory / schema 설명이 더 필요한가

여기서부터만 검토:

- `GOV24_SERVICE_FIELD`
- `GOV24_USER_TYPE`
- `GOV24_BENEFIT_TYPE`
- `GOV24_SUPPORT_CONDITION` inventory 고도화

## 매 턴 선언 템플릿

짧은 선언:

```text
Gov24 체크리스트 기준으로 이번 턴 범위는 <단계/파일/기능> 까지입니다.
이번 턴에서는 <제외 범위> 는 하지 않습니다.
```

예시 1:

```text
Gov24 체크리스트 기준으로 이번 턴 범위는 2단계의 client/DTO/addapter 까지입니다.
이번 턴에서는 detail, supportConditions, recommendation, 배치는 하지 않습니다.
```

예시 2:

```text
Gov24 체크리스트 기준으로 이번 턴 범위는 serviceList raw 저장과 welfare_services upsert 확인까지입니다.
이번 턴에서는 taxonomy import/backfill 과 read-model 변경은 하지 않습니다.
```

## 요약

1. `Gov24` 는 한 번에 끝내지 않는다.
2. `serviceList -> detail -> supportConditions` 순서로 나눈다.
3. 매 턴 시작 전에 이번 범위와 제외 범위를 먼저 선언한다.
4. raw 먼저, identity는 `서비스ID`, hard taxonomy와 recommendation은 나중에 본다.
