# CTR readiness audit runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

CTR 기반 추천 품질 조정 전에 아래를 빠르게 확인합니다.

- 클릭 추적이 살아 있는지
- 실제 튜닝 가능한 클릭 표본이 쌓였는지
- weight bucket이 한 구간에만 몰려 있는지
- fallback/AI 분포가 어느 정도인지

이 문서는 **가중치를 바로 바꾸는 절차**가 아니라, **지금이 튜닝 가능한 상태인지 판단하는 절차**입니다.

## 실행

```bash
bash deploy/smoke/run-local-ctr-readiness-audit.sh
```

실제 tuning reopen 판단은 아래 `real_non_example` 기준도 같이 봅니다.

```bash
USER_COHORT=real_non_example bash deploy/smoke/run-local-ctr-readiness-audit.sh
```

이 스크립트는 현재 로컬 DB의 `recommendation_logs` 를 직접 읽습니다.
즉 실시간 사용자 체감 테스트라기보다, 현재 로그 snapshot 기준으로 tuning readiness를 판정하는 audit wrapper입니다.
기본값 `USER_COHORT=all` 은 전체 로그를 보며, `example`, `bounded_local`, `real_non_example` 으로 cohort를 좁혀 같은 wrapper를 재사용할 수 있습니다. legacy 호환용 `non_example` 은 `bounded_local + real_non_example` 합산입니다.

## 출력 항목

- `ctr_total_logs`
- `audit_user_cohort`
- `audit_scope_logs`
- `audit_scope_users`
- `ctr_clicked_logs`
- `ctr_pct`
- `ctr_total_logs_7d`
- `ctr_clicked_logs_7d`
- `ctr_pct_7d`
- `ctr_distinct_users`
- `ctr_distinct_services`
- `ctr_clicked_users`
- `ctr_clicked_services`
- `ctr_fallback_sent`
- `ctr_fallback_clicked`
- `ctr_ai_sent`
- `ctr_ai_clicked`
- `ctr_example_logs`
- `ctr_bounded_local_logs`
- `ctr_real_non_example_logs`
- `ctr_non_example_logs`
- `ctr_example_users`
- `ctr_bounded_local_users`
- `ctr_real_non_example_users`
- `ctr_non_example_users`
- `ctr_example_clicked_users`
- `ctr_bounded_local_clicked_users`
- `ctr_real_non_example_clicked_users`
- `ctr_non_example_clicked_users`
- `[weight_bucket_ctr]`
- `[top_clicked_services]`
- `[top_clicked_users]`
- `[weight_bucket_ctr_7d]`
- `[tuning_readiness]`

`[weight_bucket_ctr]` / `[weight_bucket_ctr_7d]` 포맷:

```text
rule_weight|ai_weight|total_sent|clicked|ctr_pct
```

`[top_clicked_services]` 포맷:

```text
service_id|clicks|share_pct
```

`[top_clicked_users]` 포맷:

```text
user_key|clicks
```

## 현재 기준선 (2026-05-17, signal quality guardrail 추가 후 local audit 기준)

```text
ctr_total_logs=4083
ctr_clicked_logs=27
ctr_pct=0.66
ctr_total_logs_7d=4083
ctr_clicked_logs_7d=27
ctr_pct_7d=0.66
ctr_distinct_users=452
ctr_distinct_services=113
ctr_clicked_users=27
ctr_clicked_services=2
ctr_fallback_sent=1067
ctr_fallback_clicked=0
ctr_ai_sent=3016
ctr_ai_clicked=27
ctr_example_logs=4077
ctr_bounded_local_logs=6
ctr_real_non_example_logs=0
ctr_non_example_logs=6
ctr_example_users=451
ctr_bounded_local_users=1
ctr_real_non_example_users=0
ctr_non_example_users=1
ctr_example_clicked_users=26
ctr_bounded_local_clicked_users=1
ctr_real_non_example_clicked_users=0
ctr_non_example_clicked_users=1
```

weight bucket 분포:

```text
0.40|0.60|3570|24|0.67
0.60|0.40|366|3|0.82
0.80|0.20|147|0|0.00
```

clicked service concentration:

```text
2622|21|77.78
3688|6|22.22
```

readiness 판정:

```text
DEFERRED_REAL_NON_EXAMPLE_USER_SAMPLE_THIN
```

같은 시점 `USER_COHORT=bounded_local` rerun:

```text
audit_scope_logs=6
audit_scope_users=1
DIAGNOSTIC_BOUNDED_LOCAL_TRAFFIC
```

같은 시점 `USER_COHORT=real_non_example` rerun:

```text
audit_scope_logs=6
audit_scope_users=1
DEFERRED_CLICK_SAMPLE_THIN
```

## 해석 기준

### `DEFERRED_CLICK_SAMPLE_THIN`

- 총 클릭 수가 아직 너무 적습니다.
- 현재 단계에서는 weight를 바꾸기보다 표본을 더 쌓는 쪽이 맞습니다.

### `DEFERRED_AI_CLICK_SAMPLE_THIN`

- 클릭은 조금 있어도 AI 경로 클릭이 너무 적습니다.
- AI 비중 조정보다 표본 축적이 우선입니다.

### `DEFERRED_SINGLE_WEIGHT_BUCKET`

- click 표본은 있어도 weight bucket 분산이 충분치 않습니다.
- 현재 stage bias가 심하므로 조정 효과를 읽기 어렵습니다.

### `READY_FOR_WEIGHT_REVIEW`

- 최소한의 클릭 표본과 weight bucket 분산이 확보됐습니다.
- 이 상태에서만 rule/AI 가중치 재조정 검토를 엽니다.

### `DEFERRED_NO_REAL_NON_EXAMPLE_TRAFFIC`

- raw click 수는 있어도 현재 로그에 `REAL_NON_EXAMPLE` 사용자가 없습니다.
- `EXAMPLE` 과 `BOUNDED_LOCAL` 표본만으로는 weight tuning reopen 근거로 쓰지 않습니다.

### `DEFERRED_EMPTY_REAL_NON_EXAMPLE_COHORT`

- `USER_COHORT=real_non_example` 로 다시 봤을 때 로그가 아예 없습니다.
- 즉 지금은 “실사용 로그가 적다”가 아니라 “실사용 로그가 없다” 쪽으로 해석해야 합니다.

### `DEFERRED_REAL_NON_EXAMPLE_USER_SAMPLE_THIN`

- `USER_COHORT=all` 기준 raw click은 있어도 `REAL_NON_EXAMPLE` 사용자가 아직 너무 적습니다.
- 현재 reopen guardrail은 최소한 `REAL_NON_EXAMPLE user` 표본이 더 쌓일 때까지 유지합니다.

### `DEFERRED_REAL_NON_EXAMPLE_CLICK_SAMPLE_THIN`

- `REAL_NON_EXAMPLE` 사용자는 조금 생겼지만, 클릭한 사용자가 아직 너무 적습니다.
- 이 상태도 `READY_FOR_WEIGHT_REVIEW` 로 읽지 않습니다.

### `DIAGNOSTIC_BOUNDED_LOCAL_TRAFFIC`

- `USER_COHORT=bounded_local` 은 로컬 seed 계정만 따로 읽는 진단 모드입니다.
- 이 결과는 instrumentation 확인용이지 tuning reopen 기준으로 쓰지 않습니다.

### `DIAGNOSTIC_EXAMPLE_ONLY_TRAFFIC`

- `USER_COHORT=example` 은 smoke/validation 계정만 따로 읽는 진단 모드입니다.
- 이 결과는 instrumentation 확인용이지 tuning reopen 기준으로 쓰지 않습니다.

## 현재 판단

`2026-05-17` 기준 local 데이터는:

- total logs는 `4089`, clicked logs는 `28` 까지 올라 raw threshold는 넘겼음
- `example_logs/users = 4077 / 451`, `bounded_local_logs/users = 6 / 1`, `real_non_example_logs/users = 6 / 1`
- 다만 `REAL_NON_EXAMPLE clicked users = 1` 이라 전체 readiness는 `DEFERRED_REAL_NON_EXAMPLE_USER_SAMPLE_THIN`
- `USER_COHORT=bounded_local` 로 좁혀 보면 `6 logs / 1 user / 1 clicked user`, readiness `DIAGNOSTIC_BOUNDED_LOCAL_TRAFFIC`
- `USER_COHORT=real_non_example` 로 좁혀 보면 `6 logs / 1 user / 1 clicked user`, readiness `DEFERRED_CLICK_SAMPLE_THIN`
- clicked service는 여전히 `2` 개뿐이고 fallback `1067` 건은 클릭 `0`
- AI `3022` 건에서만 클릭 `28` 이 나와 있어, 지금 단계에서 weight를 바로 바꾸면 smoke 계정 편향과 특정 서비스/AI 경로 편향을 함께 전체 품질 신호로 오해할 위험이 큼

즉 현재 병목은 **instrumentation bug** 도, 단순 **sample 부족** 도 아니라
**synthetic-heavy local traffic + 클릭 분포 편중 + fallback 무반응** 입니다.

따라서 다음 practical step은:

1. `bounded_local` 과 `real_non_example` 신호를 분리해 기록한다
2. `USER_COHORT=all` readiness가 `DEFERRED_REAL_NON_EXAMPLE_*` 인지 먼저 확인한다
3. concentration audit를 synthetic/non-synthetic 해석과 함께 본다
4. 그 다음에만 실제 weight tuning review를 연다

## 실행 후 남길 최소 기록

- wrapper 실행 시각
- `audit_user_cohort`
- `ctr_total_logs`, `ctr_clicked_logs`, `ctr_pct`
- `ctr_clicked_users`, `ctr_clicked_services`
- fallback/AI sent/clicked
- `[weight_bucket_ctr]` 상위 3줄
- `[top_clicked_services]` 상위 2~5줄
- readiness 값
- baseline과 달라진 점 / tuning reopen 여부
