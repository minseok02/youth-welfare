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

## 출력 항목

- `ctr_total_logs`
- `ctr_clicked_logs`
- `ctr_pct`
- `ctr_total_logs_7d`
- `ctr_clicked_logs_7d`
- `ctr_pct_7d`
- `ctr_distinct_users`
- `ctr_distinct_services`
- `ctr_fallback_sent`
- `ctr_fallback_clicked`
- `ctr_ai_sent`
- `ctr_ai_clicked`
- `[weight_bucket_ctr]`
- `[weight_bucket_ctr_7d]`
- `[tuning_readiness]`

`[weight_bucket_ctr]` / `[weight_bucket_ctr_7d]` 포맷:

```text
rule_weight|ai_weight|total_sent|clicked|ctr_pct
```

## 현재 기준선 (2026-05-15)

```text
ctr_total_logs=1532
ctr_clicked_logs=13
ctr_pct=0.85
ctr_total_logs_7d=1532
ctr_clicked_logs_7d=13
ctr_pct_7d=0.85
ctr_distinct_users=36
ctr_distinct_services=110
ctr_fallback_sent=1022
ctr_fallback_clicked=0
ctr_ai_sent=510
ctr_ai_clicked=13
```

weight bucket 분포:

```text
0.40|0.60|1019|10|0.98
0.60|0.40|366|3|0.82
0.80|0.20|147|0|0.00
```

readiness 판정:

```text
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

## 현재 판단

`2026-05-15` 기준 local 데이터는:

- total logs는 `1532` 로 top stage까지 올라가 있음
- 하지만 클릭은 `13` 건뿐임
- fallback `1022` 건은 클릭 `0`
- AI `510` 건에서만 클릭 `13`

즉 현재 병목은 **instrumentation bug** 가 아니라 **click sample 부족** 입니다.

따라서 다음 practical step은:

1. weight 조정보다 표본 축적 유지
2. dashboard / CTR audit로 readiness만 재확인
3. `READY_FOR_WEIGHT_REVIEW` 가 나온 뒤에만 실제 튜닝 reopen
