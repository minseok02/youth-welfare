# recommendation ai exclusion snapshot compare runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 두 `ai-exclusion-snapshot-summary.txt` 파일을 같은 key 집합으로 바로 비교하는 wrapper 입니다.

새 snapshot을 다시 뜬 뒤,

- fresh top zero-AI 개수
- zero reason bucket 분포
- `non_example` baseline 분포
- `REAL_USER` gate 상태

가 이전 실행과 달라졌는지 한 번에 확인할 때 씁니다.

## 기본 스크립트

```bash
bash deploy/smoke/run-local-recommendation-ai-exclusion-snapshot-compare.sh
```

기본 동작:

- `tmp/recommendation-ai-exclusion-snapshot/` 아래 최신 summary 2개를 자동 선택

명시적으로 비교하려면:

```bash
BASELINE_SUMMARY=/path/to/old/ai-exclusion-snapshot-summary.txt \
TARGET_SUMMARY=/path/to/new/ai-exclusion-snapshot-summary.txt \
bash deploy/smoke/run-local-recommendation-ai-exclusion-snapshot-compare.sh
```

## 주요 출력

- `drift_detected=true|false`
- `changed_keys=...`
- `baseline_ai_zero_reason_buckets`
- `target_ai_zero_reason_buckets`
- `baseline_baseline_zero_ai_reason_buckets`
- `target_baseline_zero_ai_reason_buckets`
- `baseline_dashboard_real_user_gate`
- `target_dashboard_real_user_gate`
- `baseline_breakdown_real_user_cohort_gate`
- `target_breakdown_real_user_cohort_gate`

## 언제 쓰나

1. snapshot wrapper를 다시 돌린 직후 이전 baseline과 차이만 빠르게 보고 싶을 때
2. prompt/score/documentation 변경 뒤 exclusion evidence drift가 생겼는지 확인할 때
3. `REAL_USER` 표본이 생긴 뒤 gate 상태와 target cohort 분포가 실제로 바뀌었는지 확인할 때

## 읽는 법

- `drift_detected=false` 면
  - 현재 exclusion baseline은 직전 snapshot과 같은 뜻입니다.
- `changed_keys` 에 `ai_zero_reason_buckets` 만 바뀌고 `baseline_zero_ai_reason_buckets` 는 그대로면
  - fresh target family window만 움직였고 cohort baseline은 아직 안정적이라는 뜻입니다.
- `changed_keys` 에 `dashboard_real_user_gate`, `breakdown_real_user_cohort_gate` 가 들어오면
  - `REAL_USER` 운영 비교 lane이 열렸는지 먼저 봅니다.
- `target_target_zero_ai_reason_buckets` 가 비기 시작하지 않으면
  - 아직 real-user distribution 자체는 비어 있는 상태입니다.
