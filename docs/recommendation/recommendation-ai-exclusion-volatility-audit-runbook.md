# recommendation ai exclusion volatility audit runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

관련 문서:

- [recommendation-pr-review-brief.md](./recommendation-pr-review-brief.md)
- [recommendation-pr-draft-exit-checklist.md](./recommendation-pr-draft-exit-checklist.md)
- [recommendation-post-merge-followup-checklist.md](./recommendation-post-merge-followup-checklist.md)
- [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)
- [recommendation-ai-exclusion-snapshot-runbook.md](./recommendation-ai-exclusion-snapshot-runbook.md)

## 현재 단계 해석

현재 local 기본값은 `WAIT_FOR_REAL_USER_TRAFFIC`, latest reading은 `VOLATILE_ONLY_DRIFT` 입니다.

즉 이 volatility audit은 recommendation을 당장 reopen할지 보는 gate가 아니라, **현재 유지 중인 baseline에서 fresh target-family window가 얼마나 흔들리는지 더 정교하게 측정하는 volatility helper** 로 읽는 것이 맞습니다.

## 목적

이 문서는 baseline snapshot 하나를 고정한 뒤 `drift check` 를 여러 번 반복해서,

- fresh target family zero-AI 개수
- fresh target family zero reason bucket
- changed key 빈도

가 실제로 흔들리는지 확인하는 wrapper 입니다.

즉 현재 local drift가 `REAL_USER` gate 변화가 아니라 fresh window 변동성인지 확인할 때 씁니다.

## 기본 스크립트

```bash
RUN_COUNT=3 bash deploy/smoke/run-local-recommendation-ai-exclusion-volatility-audit.sh
```

기본 동작:

- 최신 snapshot summary 1개를 baseline으로 고정
- 새 `drift check` 를 `RUN_COUNT` 번 반복
- 각 run의 `fresh_top_ai_zero_count`, `ai_zero_reason_buckets`, `changed_keys` 를 다시 집계

명시 baseline을 쓰려면:

```bash
BASELINE_SUMMARY=/path/to/ai-exclusion-snapshot-summary.txt \
RUN_COUNT=3 \
bash deploy/smoke/run-local-recommendation-ai-exclusion-volatility-audit.sh
```

## 주요 출력

- `drift_detected_runs`
- `changed_keys_frequency`
- `fresh_top_ai_zero_count_frequency`
- `ai_zero_reason_buckets_frequency`
- `summary_output`

## 언제 쓰나

1. fresh window drift가 일시적인지 반복되는지 확인하고 싶을 때
2. current-state 숫자를 고정하기 전에 local volatility가 어느 정도인지 보고 싶을 때
3. `REAL_USER` gate는 그대로인데 fresh target family만 흔들리는지 확인하고 싶을 때

## 읽는 법

- `changed_keys_frequency` 가 `fresh_top_ai_zero_count`, `ai_zero_count`, `ai_zero_reason_buckets` 에만 몰리면
  - current drift는 fresh target family window 변동성으로 읽습니다.
- `ai_zero_reason_buckets_frequency` 에 `INCOME_MISMATCH`, `STUDENT_AUDIENCE_MISMATCH` 조합이 반복되면
  - 핵심 product exclusion 해석은 유지합니다.
- `dashboard_real_user_gate`, `breakdown_real_user_cohort_gate` 가 반복 변화하지 않으면
  - 운영 `REAL_USER` lane은 아직 안 열린 상태입니다.
