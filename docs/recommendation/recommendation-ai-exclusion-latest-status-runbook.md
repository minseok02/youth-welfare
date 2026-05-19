# recommendation ai exclusion latest status runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 최신 `baseline refresh` 요약과 최신 `drift-check` 요약을 한 번에 읽는 read-only entrypoint 입니다.

즉 재실행 없이 지금 남아 있는 latest artifact 기준으로

- stable baseline
- latest volatile observation
- latest drift 판정

을 한 화면에서 바로 보고 싶을 때 씁니다.

## 기본 스크립트

```bash
bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-status.sh
```

latest JSON이 summary보다 오래됐을 때 자동으로 export를 다시 태우고 싶으면:

```bash
AUTO_REFRESH_STATUS_JSON_IF_STALE=true \
bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-status.sh
```

## 주요 출력

- `generated_at_utc`
- `generated_at_kst`
- `operator_next_step`
- `status_json_stale_relative_to_summaries`
- `status_json_recommended_action`
- `latest_drift_class`
- `latest_recommended_reading`
- `stable_baseline_zero_ai_reason_buckets`
- `stable_dashboard_real_user_gate`
- `stable_breakdown_real_user_cohort_gate`
- `latest_fresh_top_ai_zero_count`
- `latest_ai_zero_count`
- `latest_ai_zero_reason_buckets`
- `latest_volatility_reference_frequency`
- `latest_drift_detected`
- `latest_interpretation_changed`
- `latest_stable_baseline_changed`
- `latest_observation_changed`
- `latest_changed_keys`

## 읽는 법

- `generated_at_utc`, `generated_at_kst`
  - latest export JSON이 있으면 같은 latest artifact의 UTC/KST 실행 시각을 같이 보여 줍니다.
- `operator_next_step=WAIT_FOR_REAL_USER_TRAFFIC`
  - 현재 stable baseline보다 먼저 해결할 일은 real-user traffic/cohort 확보라는 뜻입니다.
- `operator_next_step=OBSERVE_FRESH_WINDOW_VOLATILITY`
  - stable baseline은 유지되고 있고 fresh window 관찰값만 더 보면 되는 상태입니다.
- `operator_next_step=INVESTIGATE_STABLE_BASELINE_DRIFT`
  - 해석 또는 stable baseline 자체가 바뀌어 current-state/기준선 재검토가 필요한 상태입니다.
- `status_json_stale_relative_to_summaries=true`
  - latest JSON이 최신 refresh/drift summary보다 오래된 상태입니다.
- `status_json_recommended_action=RERUN_LATEST_STATUS_EXPORT`
  - note/json/latest-gate 기준선도 summary와 맞추려면 export를 다시 태우는 편이 맞습니다.
- `AUTO_REFRESH_STATUS_JSON_IF_STALE=true`
  - stale JSON이면 `latest-status-export` 를 먼저 다시 태운 뒤 fresh latest JSON으로 출력합니다.
- `latest_stable_baseline_changed=false`, `latest_observation_changed=true`
  - latest compare는 fresh window 관찰값만 흔들린 것입니다.
- `latest_recommended_reading=READ_LATEST_AS_VOLATILE_OBSERVATION`
  - latest fresh window 값은 고정 baseline이 아니라 관찰값으로 읽는 편이 맞습니다.
- `stable_dashboard_real_user_gate` 와 `stable_breakdown_real_user_cohort_gate` 가 deferred면
  - 아직 `REAL_USER` 운영 분포 비교는 열리지 않은 상태입니다.
