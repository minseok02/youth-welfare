# recommendation ai exclusion baseline refresh compare runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

관련 문서:

- [recommendation-pr-review-brief.md](./recommendation-pr-review-brief.md)
- [recommendation-pr-draft-exit-checklist.md](./recommendation-pr-draft-exit-checklist.md)
- [recommendation-post-merge-followup-checklist.md](./recommendation-post-merge-followup-checklist.md)
- [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)
- [recommendation-ai-exclusion-baseline-refresh-runbook.md](./recommendation-ai-exclusion-baseline-refresh-runbook.md)

## 현재 단계 해석

현재 local 기본 해석은 full latest batch review gate `DEFERRED_NON_REAL_LEADER_SIGNAL`, recent-window supplemental reading `RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE`, latest recommendation reading `VOLATILE_ONLY_DRIFT` 입니다.

즉 이 compare runbook은 새 제품 판단을 여는 문서가 아니라, **이미 만들어 둔 baseline refresh summary 두 개를 놓고 stable baseline과 latest observation 중 무엇이 바뀌었는지 compact하게 재분류할 때 쓰는 read-only helper** 로 읽는 것이 맞습니다.

## 목적

이 문서는 `baseline-refresh-summary.txt` 두 개만 놓고

- stable baseline이 바뀌었는지
- latest volatile observation만 바뀌었는지
- 해석(`drift_class`, `recommended_reading`) 자체가 바뀌었는지

를 high-signal key 기준으로 바로 비교하는 runbook 입니다.

긴 `baseline-report.out` 전체를 다시 읽지 않고,
핸드오프/운영 메모용 compact summary끼리만 빠르게 비교할 때 씁니다.

## 기본 스크립트

```bash
bash deploy/smoke/run-local-recommendation-ai-exclusion-baseline-refresh-compare.sh
```

명시적으로 두 파일을 비교하려면:

```bash
BASELINE_REFRESH_SUMMARY=/abs/path/older/baseline-refresh-summary.txt \
TARGET_REFRESH_SUMMARY=/abs/path/newer/baseline-refresh-summary.txt \
bash deploy/smoke/run-local-recommendation-ai-exclusion-baseline-refresh-compare.sh
```

## 주요 출력

- `drift_detected`
- `interpretation_changed`
- `stable_baseline_changed`
- `latest_observation_changed`
- `changed_keys`
- `interpretation_changed_keys`
- `stable_changed_keys`
- `latest_changed_keys`

그리고 tracked key 전체에 대해 `baseline_*`, `target_*` 값을 같이 출력합니다.

## 읽는 법

- `stable_baseline_changed=false`, `latest_observation_changed=true`
  - fresh window 관찰값만 흔들린 것입니다.
- `stable_baseline_changed=true`
  - 운영 메모/current-state에 올린 stable baseline 자체를 다시 봐야 합니다.
- `interpretation_changed=true`
  - `drift_class` 또는 `recommended_reading` 이 바뀐 것이므로, 이번 비교의 결론 문구도 같이 갱신해야 합니다.
