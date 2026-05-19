# recommendation ai exclusion drift check runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 `snapshot 생성 -> 직전 baseline과 compare` 를 한 번에 수행하는 one-shot wrapper 입니다.

즉 수동으로

1. `run-local-recommendation-ai-exclusion-snapshot.sh`
2. `run-local-recommendation-ai-exclusion-snapshot-compare.sh`

를 따로 치지 않고, 현재 local exclusion baseline drift만 바로 보고 싶을 때 씁니다.

## 기본 스크립트

```bash
bash deploy/smoke/run-local-recommendation-ai-exclusion-drift-check.sh
```

기본 동작:

- `tmp/recommendation-ai-exclusion-snapshot/` 아래 최신 summary 1개를 baseline으로 사용
- 새 snapshot을 한 번 더 생성
- baseline vs new snapshot을 compare

명시 baseline을 쓰려면:

```bash
BASELINE_SUMMARY=/path/to/old/ai-exclusion-snapshot-summary.txt \
bash deploy/smoke/run-local-recommendation-ai-exclusion-drift-check.sh
```

## 주요 출력

- `baseline_summary`
- `target_summary`
- `drift_detected=true|false`
- `changed_keys=...`
- `artifact_dir`

## 언제 쓰나

1. 코드/프롬프트/문서 변경 직후 exclusion baseline이 실제로 바뀌었는지 바로 보고 싶을 때
2. `REAL_USER` 표본이 생긴 뒤 current local baseline 대비 어떤 key가 바뀌는지 보고 싶을 때
3. 운영 메모에 “이번 실행은 baseline 대비 변화 없음/있음” 만 빠르게 남기고 싶을 때

## 읽는 법

- `drift_detected=false` 면
  - 새 snapshot 기준으로도 exclusion baseline key 집합이 직전과 동일합니다.
- `changed_keys=ai_zero_reason_buckets,...` 처럼 fresh target family key만 바뀌면
  - fresh window drift일 가능성이 큽니다.
- `changed_keys=dashboard_real_user_gate,breakdown_real_user_cohort_gate,...` 가 들어오면
  - `REAL_USER` 운영 비교 lane이 실제로 열렸는지 먼저 확인합니다.
