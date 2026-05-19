# recommendation ai exclusion drift classify runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

관련 문서:

- [recommendation-pr-review-brief.md](./recommendation-pr-review-brief.md)
- [recommendation-pr-draft-exit-checklist.md](./recommendation-pr-draft-exit-checklist.md)
- [recommendation-post-merge-followup-checklist.md](./recommendation-post-merge-followup-checklist.md)
- [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)
- [recommendation-ai-exclusion-volatility-audit-runbook.md](./recommendation-ai-exclusion-volatility-audit-runbook.md)

## 현재 단계 해석

현재 local 기본 해석은 `WAIT_FOR_REAL_USER_TRAFFIC`, `VOLATILE_ONLY_DRIFT` 입니다.

즉 이 drift-classify helper는 recommendation lane을 즉시 다시 여는 판정기가 아니라, **이미 관측된 snapshot drift를 stable baseline drift와 volatile fresh-window drift로 나눠 읽기 쉽게 만드는 해석 보조 도구** 로 보는 편이 맞습니다.

## 목적

이 문서는 snapshot compare 결과를 `stable baseline drift` 인지, `volatile fresh-window drift` 인지 자동 판정하는 helper 입니다.

즉 단순히 `drift_detected=true` 만 보는 대신,

- `VOLATILE_ONLY_DRIFT`
- `STABLE_BASELINE_DRIFT`
- `MIXED_DRIFT`

중 무엇인지 바로 보고 싶을 때 씁니다.

## 기본 스크립트

```bash
bash deploy/smoke/run-local-recommendation-ai-exclusion-drift-classify.sh
```

기본 동작:

- 최신 `volatility-summary.txt` 를 읽어 volatile reference key를 뽑음
- 최신 snapshot summary 2개를 compare
- changed key를 stable/volatile 로 분리

명시적으로 지정하려면:

```bash
VOLATILITY_SUMMARY=/path/to/volatility-summary.txt \
BASELINE_SUMMARY=/path/to/old/ai-exclusion-snapshot-summary.txt \
TARGET_SUMMARY=/path/to/new/ai-exclusion-snapshot-summary.txt \
bash deploy/smoke/run-local-recommendation-ai-exclusion-drift-classify.sh
```

## 주요 출력

- `drift_class=NO_DRIFT|VOLATILE_ONLY_DRIFT|STABLE_BASELINE_DRIFT|MIXED_DRIFT`
- `stable_changed_keys`
- `volatile_changed_keys`

## 읽는 법

- `VOLATILE_ONLY_DRIFT`
  - fresh target family window 흔들림으로만 읽습니다.
- `STABLE_BASELINE_DRIFT`
  - cohort baseline 또는 `REAL_USER` gate 같은 고정 truth가 바뀐 것입니다.
- `MIXED_DRIFT`
  - stable baseline과 fresh window가 같이 움직였으므로 더 조심해서 읽습니다.
