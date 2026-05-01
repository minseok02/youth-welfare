# 복지로 Gap Fill 라운드/호출 예산 전략

관련 문서:

- [collect-ops.md](../../collect-ops.md)
- [policy-bokjiro-detail-validation-rehearsal.md](./policy-bokjiro-detail-validation-rehearsal.md)
- [policy-bokjiro-detail-budget-observability-policy.md](./policy-bokjiro-detail-budget-observability-policy.md)
- [db-migration.md](../../db-migration.md)
- [phase-plan.md](../../phase-plan.md)

## 목적

`POST /api/admin/collect/bokjiro-details-gap-fill` 를 다시 태울 때

- 몇 라운드로 시작할지
- `maxCallsPerRound` 를 얼마나 줄지/늘릴지
- 어디서 멈출지

를 운영 기준으로 고정합니다.

## 현재 기준점

이미 확인된 local 기준점:

- 복지로 서비스 총 `1335`
- stored detail raw payload `190 -> 199`
- missing detail service `1145 -> 1136`
- `service_facts` `99 -> 103`
- 추가 raw payload `9`건 대비 fact 증가는 `4`건

즉 gap fill 효과는 분명 있지만,

- payload coverage 증가
- fact density 증가

가 1:1 은 아닙니다.

## 결론

현재 phase의 기본 전략은 아래와 같습니다.

1. 시작점은 `2 rounds x 20 calls`
2. 다음 증분은 `2 rounds x 40 calls`
3. 그 다음부터는 `savedCount=0` 또는 `coverage 증가 대비 fact 증가가 낮은 구간` 에서 멈춤

즉 초반은 작은 라운드로 payload ceiling 을 먼저 확인하고,
이후에만 budget 을 단계적으로 올립니다.

## 왜 작은 라운드부터 시작하는가

### 1. `95/API` cap 은 safety guard이지 기본 목표가 아니다

`95/API` 까지 한 번에 태우는 건 가능하지만,
그건 “최대 허용”일 뿐 항상 적절한 운영 기본값은 아닙니다.

초기 목적은:

- missing backlog 가 실제로 줄어드는지
- source starvation 없이 central/local 이 모두 돈는지
- fact density 가 따라오는지

를 확인하는 것입니다.

그래서 small-budget step이 먼저입니다.

### 2. raw detail 확보와 fact 추출은 다른 단계다

raw payload 가 늘어도

- age/deadline signal이 없으면
- `service_facts` 는 그대로일 수 있습니다.

따라서 early round 에서는

- payload coverage slope
- fact density slope

를 같이 봐야 합니다.

## 권장 단계

## 1단계. `2 rounds x 20 calls`

목적:

- 가장 작은 추가 budget으로 coverage 증가 가능성 확인
- source 분배 회귀 없는지 확인
- `savedCount=0` 조기 종료가 실제로 잘 동작하는지 확인

운영 해석:

- 여기서도 `savedCount=0` 이 빨리 나오면 큰 증분으로 올리지 않습니다.

## 2단계. `2 rounds x 40 calls`

전제:

- 1단계에서 raw detail coverage 가 실제로 늘었음
- source별 starvation 이 보이지 않음

목적:

- backlog drain 속도가 너무 느린지 확인
- `service_facts` 증가가 계속 유효한지 확인

운영 해석:

- raw payload 만 늘고 fact 증가는 거의 없으면, 다음 액션은 budget 확대보다 residual sample 분석입니다.

## 3단계. `1~2 rounds x 95 calls`

전제:

- coverage 확대 자체가 더 중요함
- API quota / 운영 시간대 / 429 리스크를 감당할 수 있음

목적:

- backlog drain 우선

운영 해석:

- 이 단계는 routine default가 아니라 catch-up run 으로만 봅니다.

## stop 조건

아래 중 하나면 멈춥니다.

1. `savedCount=0`
2. raw detail coverage 증가는 있지만 `service_facts` 증가가 거의 없음
3. `HTTP 429` 또는 rate-limit hit가 운영상 거슬릴 정도로 증가
4. source별 backlog 감소보다 관측 비용이 더 큼

즉 “API를 더 태울 수 있다”와 “지금 더 태워야 한다”를 같은 뜻으로 보지 않습니다.

## 운영자가 보는 1차 지표

각 run 뒤에 먼저 보는 값:

- `roundsExecuted`
- `savedCount`
- detail raw payload 증가량
- missing detail service 감소량
- `service_facts` 증가량

현재 phase에서는 `centralBudget/localBudget` 자체보다 위 지표가 우선입니다.

## 현재 phase에서 일부러 안 여는 것

이번 전략 문서에서는 아래를 같이 열지 않습니다.

- gap-fill 자동 스케줄링
- round별 budget breakdown 응답 노출
- `threshold_like` soft signal 실제 구현
- extractor 추가 보강 실행

이 문서는 수동 gap-fill run budget 전략만 고정합니다.

## 요약

1. 기본 시작점은 `2 rounds x 20 calls` 입니다.
2. 다음 증분은 `2 rounds x 40 calls` 입니다.
3. `95/API` 는 catch-up 용 상한이지 기본값이 아닙니다.
4. stop 조건은 `savedCount=0`, 낮은 fact 증가 효율, rate-limit 부담입니다.
5. gap-fill 평가는 budget보다 coverage/fact density 결과를 먼저 봅니다.
