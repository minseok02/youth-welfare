# recommendation ai exclusion baseline refresh drift check runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

관련 문서:

- [recommendation-pr-review-brief.md](./recommendation-pr-review-brief.md)
- [recommendation-pr-draft-exit-checklist.md](./recommendation-pr-draft-exit-checklist.md)
- [recommendation-post-merge-followup-checklist.md](./recommendation-post-merge-followup-checklist.md)
- [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)
- [recommendation-ai-exclusion-latest-overview-runbook.md](./recommendation-ai-exclusion-latest-overview-runbook.md)

## 현재 단계 해석

현재 local recommendation current truth는 full latest batch review gate `DEFERRED_NON_REAL_LEADER_SIGNAL`, recent-window supplemental reading `RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE`, basic gate `PASS`, strict gate `LATEST_OBSERVATION_CHANGED`, `VOLATILE_ONLY_DRIFT` 입니다.

즉 이 drift-check는 “reopen 여부를 오늘 바로 결정하는 gate”라기보다, **baseline refresh를 다시 태운 뒤 변화가 stable baseline drift인지 fresh observation 흔들림인지 더 명확히 분리할 때 쓰는 deeper compare wrapper** 로 보는 편이 맞습니다.

## 목적

이 문서는

- 새 `baseline refresh`
- 직전 `baseline-refresh-summary.txt` 대비 compact compare

를 한 번에 끝내는 one-shot entrypoint 입니다.

즉 새 refresh를 실제로 다시 태운 뒤, 직전 refresh와 비교했을 때

- stable baseline이 바뀌었는지
- latest observation만 흔들렸는지
- 해석 자체가 바뀌었는지

를 바로 보고 싶을 때 씁니다.

## 기본 스크립트

```bash
RUN_COUNT=2 bash deploy/smoke/run-local-recommendation-ai-exclusion-baseline-refresh-drift-check.sh
```

명시적으로 baseline refresh summary를 고정하려면:

```bash
BASELINE_REFRESH_SUMMARY=/abs/path/older/baseline-refresh-summary.txt \
RUN_COUNT=2 \
bash deploy/smoke/run-local-recommendation-ai-exclusion-baseline-refresh-drift-check.sh
```

## 주요 출력

- `baseline_refresh_summary`
- `target_refresh_summary`
- `drift_detected`
- `changed_keys`
- `interpretation_changed`
- `stable_baseline_changed`
- `latest_observation_changed`
- `artifact_dir`
- `latest_artifact_link`
- `latest_compare_link`
- `summary_output`
- `latest_summary_link`

`summary_output` 은 compact drift summary artifact 입니다. 긴 compare 출력 전체를 다시 열지 않고 아래 high-signal key만 바로 읽고 싶을 때 씁니다.

- `drift_detected`
- `interpretation_changed`
- `stable_baseline_changed`
- `latest_observation_changed`
- `changed_keys`
- `stable_changed_keys`
- `latest_changed_keys`
- baseline/target `drift_class`
- baseline/target stable baseline key
- baseline/target latest observation key

## 읽는 법

- `stable_baseline_changed=false`, `latest_observation_changed=true`
  - stable baseline은 그대로고 fresh window 관찰값만 흔들린 것입니다.
- `stable_baseline_changed=true`
  - current-state / operation checklist에 올린 stable baseline 자체를 다시 봐야 합니다.
- `interpretation_changed=true`
  - `drift_class` 또는 `recommended_reading` 이 바뀐 것이므로 운영 메모 결론도 같이 갱신해야 합니다.

가장 최근 drift-check artifact와 compare 출력만 바로 열고 싶으면 `latest_artifact_link`, `latest_compare_link` 를 보고, compact key만 바로 읽고 싶으면 `latest_summary_link` 를 보면 됩니다.
