# 복지로 Detail Budget Observability 정책

관련 문서:

- [collect-ops.md](../../collect-ops.md)
- [policy-bokjiro-detail-validation-rehearsal.md](./policy-bokjiro-detail-validation-rehearsal.md)
- [phase-plan.md](../../phase-plan.md)

## 목적

복지로 detail refresh / gap-fill 에서 계산되는

- `centralBudget`
- `localBudget`

를 admin collect observability에 어디까지 노출할지 고정합니다.

## 결론

현재 phase에서는 `centralBudget` / `localBudget` 을

- service 내부 metadata/log 에는 유지
- admin API public response 에는 올리지 않음

으로 고정합니다.

즉 운영자가 immediate triage에 쓸 수 있는 정보는 남기되,
현재 `DetailGapFillResponse` 계약은 계속 round summary 중심으로 둡니다.

## 현재 상태

이미 코드상으로는 아래가 존재합니다.

- `BokjiroDetailCollectService.collectBokjiroDetailsResult(...)`
  - `metadataJson` 안에 `maxCalls`, `centralBudget`, `localBudget`, `centralCalls`, `localCalls`, `refreshExisting`
- service log
  - source별 `budget`
- unit test
  - low `maxCalls` 상황에서 `centralBudget/localBudget` 분배 회귀 고정

즉 budget 자체는 계산/기록되고 있고, 현재 쟁점은 “admin response에 올릴지” 입니다.

## 왜 response에 안 올리는가

### 1. `gap-fill` 은 multi-round semantics 가 먼저다

`POST /api/admin/collect/bokjiro-details-gap-fill` 응답은 현재:

- `roundsRequested`
- `roundsExecuted`
- `requestedCount`
- `savedCount`
- `skippedCount`
- `failedCount`
- `stoppedAfterNoSaves`

중심입니다.

여기에 `centralBudget/localBudget` 를 올리면 즉시 생기는 문제가 있습니다.

- 어느 round 의 budget 인가
- 각 round budget array를 줄 것인가
- aggregate budget 총합을 줄 것인가

즉 response 의미가 단순하지 않습니다.

### 2. 운영자가 당장 보는 핵심 지표는 budget 자체보다 coverage 결과다

현재 phase에서 운영자가 먼저 보는 값은:

- raw detail coverage 증가
- missing detail backlog 감소
- `service_facts` density 변화

입니다.

`centralBudget/localBudget` 은 분배 회귀를 디버깅할 때는 유용하지만,
일상적인 gap-fill 성공/실패 판단의 1차 지표는 아닙니다.

### 3. response contract 를 늘리기 전에 observability 경계를 따로 설계하는 편이 낫다

budget을 정말 운영 응답에 올리려면,

- single refresh 경로와 gap-fill 경로를 같이 볼지
- round별 breakdown 을 줄지
- future status endpoint 로 뺄지

를 먼저 정해야 합니다.

이번 phase에서는 그 수준까지 열지 않습니다.

## 이번 phase의 운영 기준

운영자는 아래 순서로 봅니다.

1. admin response summary
   - rounds / saved / failed / stoppedAfterNoSaves
2. service log
   - source별 calls / saved / skipped / failed / budget
3. 필요 시 stored coverage/fact density 쿼리

즉 budget triage 는 2차 정보로 둡니다.

## future reopen 조건

아래 중 하나가 생기면 다시 엽니다.

- round별 budget breakdown 이 실제 운영 의사결정에 필요해짐
- `bokjiro-details-gap-fill` 을 자주 운영하고 source starvation triage 빈도가 높아짐
- 별도 admin status/observability endpoint 를 추가함

그때는 아래 둘 중 하나로 다시 열 수 있습니다.

1. `DetailGapFillResponse` 에 round별 budget array 추가
2. 별도 `collect observability` endpoint 에 budget summary 추가

## 요약

1. `centralBudget/localBudget` 은 이미 service metadata/log 에는 남는다.
2. 현재 phase에서는 admin API response 계약에는 올리지 않는다.
3. 이유는 multi-round response 의미가 커지고, 운영 1차 지표가 coverage/fact density이기 때문이다.
4. budget observability 확장은 별도 status/observability 트랙이 필요할 때 다시 연다.
