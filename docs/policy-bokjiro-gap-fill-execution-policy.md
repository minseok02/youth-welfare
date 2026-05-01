# 복지로 Gap Fill 추가 실행 정책

관련 문서:

- [policy-bokjiro-gap-fill-budget-strategy.md](./policy-bokjiro-gap-fill-budget-strategy.md)
- [policy-bokjiro-detail-validation-rehearsal.md](./policy-bokjiro-detail-validation-rehearsal.md)
- [policy-bokjiro-detail-budget-observability-policy.md](./policy-bokjiro-detail-budget-observability-policy.md)
- [db-migration.md](./db-migration.md)
- [phase-plan.md](./phase-plan.md)

## 목적

`bokjiro-details-gap-fill` 을

- 지금 기본 작업처럼 계속 실행할지
- 아니면 수동 catch-up/on-demand 경로로만 둘지

를 현재 phase 기준으로 고정합니다.

## 결론

현재 phase에서는 `bokjiro-details-gap-fill` 추가 실행을

- routine default 작업으로 두지 않고
- **수동 catch-up/on-demand 작업으로만 유지**

합니다.

즉 gap-fill 경로는 열어 두되,
기본 진행축은 더 이상 coverage 확대 run 반복이 아닙니다.

## 이유

### 1. coverage/fact 증가 효율이 완만하다

이미 확인된 local 기준:

- detail raw payload `190 -> 199`
- missing detail service `1145 -> 1136`
- `service_facts` `99 -> 103`

즉 효과는 있지만,

- payload coverage 증가
- fact density 증가

가 빠르게 따라오는 구간은 아닙니다.

### 2. 남은 갭의 중심이 payload signal 분포 쪽으로 이동했다

현재 복지로 residual 해석은:

- strict age residual 은 거의 소진
- `BK_APPLY_END_DATE` 는 explicit field 근거 없음
- 남은 큰 덩어리는 `beneficiary_only`, `threshold_like`, 일반복지성 text signal

즉 지금은 더 많이 호출하는 것보다,
이미 확보한 payload를 어떻게 해석할지의 비중이 더 큽니다.

### 3. gap-fill 은 운영 실험과 catch-up run을 구분해야 한다

예산 전략은 이미:

- `2 rounds x 20 calls`
- `2 rounds x 40 calls`
- `95/API` catch-up

으로 나뉘어 있습니다.

이걸 그대로 “계속 더 돌리는 기본 작업”으로 두면
실험과 catch-up의 의미가 다시 섞입니다.

## 현재 운영 계약

### 기본값

- 추가 gap-fill 실행은 하지 않음

### 예외적으로 실행하는 경우

- stored detail coverage ceiling 을 다시 확인해야 할 때
- source starvation/regression smoke가 필요할 때
- 실제 backlog catch-up run이 필요할 때

### 실행 시 시작점

- `2 rounds x 20 calls`

## 이 결정이 의미하는 것

이 문서가 닫는 것은

- “gap-fill 을 더 돌릴 수 있다”

가 아니라

- “현재 phase의 기본 진행축은 gap-fill 확대가 아니다”

라는 뜻입니다.

현재 우선순위는 점점 아래로 이동합니다.

- soft signal 경계
- 정책형/listing형/reference형 source onboarding
- canonical 저장/읽기 경계

## future reopen 조건

아래 중 하나가 생기면 다시 엽니다.

- stored detail coverage 를 더 밀어야 할 운영 필요
- small-budget run에서 coverage 증가가 다시 유의미하게 나옴
- `welfare_service_details` / `service_facts` 검증에서 source ceiling을 다시 봐야 함

## 요약

1. `bokjiro-details-gap-fill` 추가 실행은 현재 phase의 기본 작업이 아닙니다.
2. 필요할 때만 수동 catch-up/on-demand 작업으로 유지합니다.
3. 이유는 coverage/fact 증가 효율이 완만하고, 남은 갭의 중심이 signal 해석 쪽으로 이동했기 때문입니다.
4. 다음 기본 축은 gap-fill 반복보다 다른 canonical/source pending 입니다.
