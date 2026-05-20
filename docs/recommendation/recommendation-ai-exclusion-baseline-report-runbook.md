# recommendation ai exclusion baseline report runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

관련 문서:

- [recommendation-pr-review-brief.md](./recommendation-pr-review-brief.md)
- [recommendation-pr-draft-exit-checklist.md](./recommendation-pr-draft-exit-checklist.md)
- [recommendation-post-merge-followup-checklist.md](./recommendation-post-merge-followup-checklist.md)
- [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)
- [recommendation-ai-exclusion-stability-report-runbook.md](./recommendation-ai-exclusion-stability-report-runbook.md)

## 현재 단계 해석

현재 local recommendation current truth는 full latest batch review gate `DEFERRED_NON_REAL_LEADER_SIGNAL`, recent-window supplemental reading `RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE`, latest reading `VOLATILE_ONLY_DRIFT` 입니다.

즉 이 baseline report는 새로운 제품 판단을 여는 문서가 아니라, **current evidence를 사람이 바로 읽는 한 장짜리 report로 다시 묶어 stable baseline과 latest volatile observation을 구분하는 summary helper** 로 보는 편이 맞습니다.

## 목적

이 문서는 current AI exclusion evidence를 사람이 바로 읽는 한 장의 report로 정리합니다.

내부적으로는

- stability report
- drift classify

를 같이 읽어서,

- stable baseline
- latest volatile observation
- recommended reading

을 한 번에 출력합니다.

## 기본 스크립트

```bash
bash deploy/smoke/run-local-recommendation-ai-exclusion-baseline-report.sh
```

## 주요 출력

- `drift_class`
- `recommended_reading`
- `stable_keys`
- `volatile_keys`
- `stable_baseline_zero_ai_reason_buckets`
- `stable_dashboard_real_user_gate`
- `stable_breakdown_real_user_cohort_gate`
- `latest_fresh_top_ai_zero_count`
- `latest_ai_zero_count`
- `latest_ai_zero_reason_buckets`

## 읽는 법

- `recommended_reading=READ_LATEST_AS_VOLATILE_OBSERVATION`
  - 최신 fresh target window 값은 관찰값으로만 읽고 stable baseline을 바꾸지 않습니다.
- `recommended_reading=UPDATE_STABLE_BASELINE`
  - stable baseline 계열 key가 실제로 바뀌었습니다.
- `recommended_reading=INVESTIGATE_BEFORE_BASELINE_UPDATE`
  - stable/volatile key가 같이 움직여 바로 결론내리기 어렵습니다.
