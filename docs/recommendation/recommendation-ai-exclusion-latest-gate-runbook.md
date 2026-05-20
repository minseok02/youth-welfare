# recommendation ai exclusion latest gate runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

관련 문서:

- [recommendation-pr-review-brief.md](./recommendation-pr-review-brief.md)
- [recommendation-pr-draft-exit-checklist.md](./recommendation-pr-draft-exit-checklist.md)
- [recommendation-post-merge-followup-checklist.md](./recommendation-post-merge-followup-checklist.md)
- [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)
- [recommendation-ai-exclusion-latest-status-runbook.md](./recommendation-ai-exclusion-latest-status-runbook.md)

## 현재 단계 해석

현재 local recommendation current truth는 full latest batch review gate `DEFERRED_NON_REAL_LEADER_SIGNAL`, recent-window supplemental reading `RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE`, basic gate `PASS`, strict gate `LATEST_OBSERVATION_CHANGED`, `VOLATILE_ONLY_DRIFT` 입니다.

즉 이 gate runbook은 recommendation reopen 여부를 오늘 바로 결정하는 문서가 아니라, **latest artifact 기준 해석 변화 / stable baseline 변화 / strict observation 변화를 자동 판정하는 운영 gate helper** 로 보는 편이 맞습니다.

## 목적

이 문서는 latest `latest-status.json` 을 읽어

- 해석이 바뀌었는지
- stable baseline이 바뀌었는지
- 필요하면 latest observation 변화까지 fail로 볼지

를 자동 판정하는 gate runbook 입니다.

사람이 읽는 요약이 아니라, CI/자동화/운영 체크에서 pass/fail 신호가 필요할 때 씁니다.

## 기본 스크립트

```bash
bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-gate.sh
```

latest JSON이 summary보다 오래됐을 때 자동으로 export를 다시 태우고 싶으면:

```bash
AUTO_REFRESH_STATUS_JSON_IF_STALE=true \
bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-gate.sh
```

기본값은 아래일 때만 fail 합니다.

- `interpretation_changed=true`
- `stable_baseline_changed=true`

latest observation 변화도 fail로 보고 싶으면:

```bash
FAIL_ON_LATEST_OBSERVATION_CHANGE=true \
bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-gate.sh
```

## 주요 출력

- `gate_status`
- `gate_reason`
- `generated_at_utc`
- `generated_at_kst`
- `operator_next_step`
- `effective_operator_next_step`
- `gate_action_class`
- `gate_policy_status`
- `gate_policy_reason`
- `review_gate_policy_candidate_status`
- `review_gate_policy_candidate_reason`
- `review_gate_policy_promotion_status`
- `review_gate_policy_promotion_reason`
- `review_gate_policy_promotion_action_status`
- `review_gate_policy_promotion_action_reason`
- `status_json_stale_relative_to_summaries`
- `status_json_recommended_action`
- `latest_drift_class`
- `latest_recommended_reading`
- `interpretation_changed`
- `stable_baseline_changed`
- `latest_observation_changed`
- `changed_keys`
- `review_gate_interpretation_class`
- `review_gate_operating_mode`
- `review_gate_policy_candidate_status`
- `review_gate_policy_candidate_reason`
- `review_gate_policy_promotion_status`
- `review_gate_policy_promotion_reason`
- `review_gate_policy_promotion_action_status`
- `review_gate_policy_promotion_action_reason`

## 읽는 법

- `gate_status=PASS`
  - 현재 latest status는 허용 범위입니다.
- `gate_policy_status=PRIMARY_BLOCKED_SUPPLEMENTAL_CLEAR`
  - drift gate는 PASS여도, 운영 정책 해석은 아직 primary historical gate가 blocker이고 recent-window는 supplemental clear라는 뜻입니다.
- `gate_reason=INTERPRETATION_CHANGED`
  - latest 해석이 바뀌었으므로 운영 메모/current-state 결론도 다시 봐야 합니다.
- `gate_reason=STABLE_BASELINE_CHANGED`
  - stable baseline 자체가 바뀌었으므로 기준선 갱신이 필요합니다.
- `gate_reason=LATEST_OBSERVATION_CHANGED`
  - strict mode에서만 쓰는 fail reason입니다.
- `generated_at_utc`, `generated_at_kst`
  - 현재 gate가 읽은 latest-status JSON이 언제 생성된 것인지 UTC/KST 둘 다 바로 확인할 수 있습니다.
- `operator_next_step`
  - older baseline artifact가 들고 있던 historical pointer입니다.
- `effective_operator_next_step`
  - gate가 PASS여도 실제 current 해석이 무엇인지 보여 주는 값입니다. 현재 local 기준으로는 `USE_RECENT_WINDOW_AS_SUPPLEMENTAL_REVIEW_CONTEXT` 를 먼저 읽는 편이 맞습니다.
- `gate_action_class`
  - gate 한 줄만 보고도 바로 취할 실행 분류입니다. 현재 local 기준으로는 `READ_PRIMARY_AND_SUPPLEMENTAL_REVIEW_GATES` 입니다.
- `gate_policy_status`
  - drift 여부와 별도로, current 운영 정책 상태를 짧게 요약한 값입니다. 현재 local 기준으로는 `PRIMARY_BLOCKED_SUPPLEMENTAL_CLEAR` 를 먼저 읽는 편이 맞습니다.
- `gate_policy_reason`
  - 위 policy 상태를 만든 운영 해석 클래스입니다. 현재 local 기준으로는 `HISTORICAL_PRIMARY_BLOCKER_CURRENT_WINDOW_CLEAR` 입니다.
- `review_gate_policy_candidate_status`
  - recent-window gate를 실제 policy gate 후보로 볼 수 있는 current 상태인지 보여 주는 값입니다. 현재 local 기준으로는 `RECENT_WINDOW_POLICY_CANDIDATE` 입니다.
- `review_gate_policy_candidate_reason`
  - 위 candidate 상태를 만든 직접 이유입니다. 현재 local 기준으로는 `PRIMARY_GATE_BLOCKED_BY_STALE_ALL_TIME_EXAMPLE_REFERENCE_BUT_RECENT_WINDOW_CLEAR` 입니다.
- `review_gate_policy_promotion_status`
  - candidate와 별도로, current 시점에 recent-window를 바로 primary gate로 승격해도 되는지 보여 주는 값입니다. 현재 local 기준으로는 `REQUIRES_EXPLICIT_POLICY_CHANGE_REVIEW` 입니다.
- `review_gate_policy_promotion_reason`
  - 위 promotion 보류 상태를 만든 직접 이유입니다. 현재 local 기준으로는 `RECENT_WINDOW_IS_A_CANDIDATE_BUT_PRIMARY_BASELINE_IS_STILL_ALL_TIME_LATEST` 입니다.
- `review_gate_policy_promotion_action_status`
  - promotion pending 상태에서 지금 바로 취할 action을 더 짧게 접은 값입니다. 현재 local 기준으로는 `KEEP_PRIMARY_BASELINE` 입니다.
- `review_gate_policy_promotion_action_reason`
  - 위 action status를 만든 직접 이유입니다. 현재 local 기준으로는 `PROMOTION_STILL_REQUIRES_EXPLICIT_POLICY_REVIEW` 입니다.
- `review_gate_interpretation_class`
  - primary full latest batch gate와 supplemental recent-window gate를 합친 운영 해석 클래스입니다. 현재 local 기준으로는 `HISTORICAL_PRIMARY_BLOCKER_CURRENT_WINDOW_CLEAR` 입니다.
- `review_gate_operating_mode`
  - primary/supplemental gate를 어떤 조합으로 실제 운영 해석에 써야 하는지 보여 주는 값입니다. 현재 local 기준으로는 `PRIMARY_BASELINE_WITH_SUPPLEMENTAL_RECENT_WINDOW` 입니다.
- `status_json_stale_relative_to_summaries=true`
  - gate가 보고 있는 JSON이 latest summary보다 오래됐다는 뜻입니다.
- `status_json_recommended_action=RERUN_LATEST_STATUS_EXPORT`
  - gate를 다시 믿기 전에 latest export JSON부터 새로 뽑는 편이 맞습니다.
- `AUTO_REFRESH_STATUS_JSON_IF_STALE=true`
  - stale JSON이면 gate가 `latest-status-export` 를 먼저 다시 태운 뒤 fresh latest JSON 기준으로 판정합니다.
