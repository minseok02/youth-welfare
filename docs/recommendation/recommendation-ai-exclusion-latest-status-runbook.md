# recommendation ai exclusion latest status runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

관련 문서:

- [recommendation-pr-review-brief.md](./recommendation-pr-review-brief.md)
- [recommendation-pr-draft-exit-checklist.md](./recommendation-pr-draft-exit-checklist.md)
- [recommendation-post-merge-followup-checklist.md](./recommendation-post-merge-followup-checklist.md)
- [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)
- [recommendation-ai-exclusion-latest-overview-runbook.md](./recommendation-ai-exclusion-latest-overview-runbook.md)

## 현재 단계 해석

현재 local 기본값은 full latest batch review gate `DEFERRED_NON_REAL_LEADER_SIGNAL`, recent-window supplemental reading `RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE`, latest reading `VOLATILE_ONLY_DRIFT` 입니다.

즉 이 runbook은 새 refresh를 다시 태우는 reopen entrypoint가 아니라, **이미 남아 있는 latest artifact를 기준으로 current stable baseline / latest volatile observation / drift 판정을 read-only로 확인하는 daily status helper** 로 읽는 편이 맞습니다.

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
- `review_gate_policy_promotion_readiness_status`
- `review_gate_policy_promotion_readiness_reason`
- `review_gate_policy_promotion_execution_status`
- `review_gate_policy_promotion_execution_reason`
- `review_gate_policy_promotion_approval_criteria_status`
- `review_gate_policy_promotion_approval_criteria_reason`
- `review_gate_policy_promotion_approval_status`
- `review_gate_policy_promotion_approval_reason`
- `review_gate_policy_promotion_approval_decision_status`
- `review_gate_policy_promotion_approval_decision_reason`
- `review_gate_policy_promotion_approval_record_status`
- `review_gate_policy_promotion_approval_record_reason`
- `review_gate_policy_promotion_review_run_status`
- `review_gate_policy_promotion_review_run_reason`
- `review_gate_policy_promotion_review_run_criteria_status`
- `review_gate_policy_promotion_review_run_criteria_reason`
- `review_gate_policy_promotion_review_run_decision_status`
- `review_gate_policy_promotion_review_run_decision_reason`
- `review_gate_policy_promotion_review_run_approval_criteria_status`
- `review_gate_policy_promotion_review_run_approval_criteria_reason`
- `review_gate_policy_promotion_review_run_approval_decision_status`
- `review_gate_policy_promotion_review_run_approval_decision_reason`
- `review_gate_policy_promotion_review_run_approval_status`
- `review_gate_policy_promotion_review_run_approval_reason`
- `review_gate_policy_promotion_review_run_approval_record_criteria_status`
- `review_gate_policy_promotion_review_run_approval_record_criteria_reason`
- `review_gate_policy_promotion_review_run_approval_record_status`
- `review_gate_policy_promotion_review_run_approval_record_reason`
- `review_gate_policy_promotion_review_run_approval_record_transition_status`
- `review_gate_policy_promotion_review_run_approval_record_transition_reason`
- `review_gate_policy_promotion_review_run_approval_record_write_status`
- `review_gate_policy_promotion_review_run_approval_record_write_reason`
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
- `primary_review_gate_blocker_class`
- `review_gate_interpretation_class`
- `review_gate_operating_mode`
- `review_gate_policy_candidate_status`
- `review_gate_policy_candidate_reason`
- `review_gate_policy_promotion_status`
- `review_gate_policy_promotion_reason`
- `review_gate_policy_promotion_action_status`
- `review_gate_policy_promotion_action_reason`
- `recent_window_recommendation_review_reading`
- `historical_example_dominance_detected`

## 읽는 법

- `generated_at_utc`, `generated_at_kst`
  - latest export JSON이 있으면 같은 latest artifact의 UTC/KST 실행 시각을 같이 보여 줍니다.
- `operator_next_step`
  - older baseline artifact가 들고 있던 historical pointer입니다.
- `effective_operator_next_step=USE_RECENT_WINDOW_AS_SUPPLEMENTAL_REVIEW_CONTEXT`
  - current local truth 기준 실제 다음 해석입니다. full latest batch gate가 stale historical example inertia를 포함하므로, recent-window current signal을 같이 읽으라는 뜻입니다.
- `gate_action_class=READ_PRIMARY_AND_SUPPLEMENTAL_REVIEW_GATES`
  - operator가 한 줄로 먼저 읽을 실행 분류입니다. 현재 local 기준으로는 full latest batch historical baseline과 recent-window current-live signal을 같이 보라는 뜻입니다.
- `gate_policy_status=PRIMARY_BLOCKED_SUPPLEMENTAL_CLEAR`
  - drift 여부와 별도로, current 운영 정책 상태를 짧게 읽는 값입니다. primary full latest batch gate는 blocker지만 supplemental recent-window signal은 clear라는 뜻입니다.
- `gate_policy_reason=HISTORICAL_PRIMARY_BLOCKER_CURRENT_WINDOW_CLEAR`
  - 위 policy 상태를 만든 운영 해석 클래스입니다.
- `review_gate_policy_candidate_status=RECENT_WINDOW_POLICY_CANDIDATE`
  - primary full latest batch gate는 아직 blocker지만, recent-window current-live signal은 실제 policy gate 후보로 볼 수 있는 상태라는 뜻입니다.
- `review_gate_policy_candidate_reason=PRIMARY_GATE_BLOCKED_BY_STALE_ALL_TIME_EXAMPLE_REFERENCE_BUT_RECENT_WINDOW_CLEAR`
  - 위 candidate 상태를 만든 직접 이유입니다.
- `review_gate_policy_promotion_status=REQUIRES_EXPLICIT_POLICY_CHANGE_REVIEW`
  - recent-window는 후보이지만, primary baseline이 아직 `ALL_TIME_LATEST_PER_USER` 이라 즉시 승격이 아니라 explicit policy change review가 필요하다는 뜻입니다.
- `review_gate_policy_promotion_reason=RECENT_WINDOW_IS_A_CANDIDATE_BUT_PRIMARY_BASELINE_IS_STILL_ALL_TIME_LATEST`
  - 위 promotion 보류 상태를 만든 직접 이유입니다.
- `review_gate_policy_promotion_action_status=KEEP_PRIMARY_BASELINE`
  - current 시점에 operator가 즉시 취할 행동을 더 짧게 접은 값입니다. 지금은 승격 검토를 열기보다 primary baseline을 유지하는 편이 맞다는 뜻입니다.
- `review_gate_policy_promotion_action_reason=PROMOTION_STILL_REQUIRES_EXPLICIT_POLICY_REVIEW`
  - 위 action status를 만든 직접 이유입니다.
- `review_gate_policy_promotion_readiness_status=READY_FOR_BOUNDED_PROMOTION_REVIEW`
  - 지금 당장 primary baseline을 바꾸진 않더라도, bounded promotion review를 시작할 prerequisite 자체는 충족됐는지 보여 주는 값입니다. 현재 local 기준으로는 `READY_FOR_BOUNDED_PROMOTION_REVIEW` 입니다.
- `review_gate_policy_promotion_readiness_reason=EXPLICIT_POLICY_REVIEW_PENDING_WITH_BOUNDED_REVIEW_PREREQUISITES_MET`
  - 위 readiness 상태를 만든 직접 이유입니다. 현재 local 기준으로는 explicit policy review는 아직 남아 있지만 bounded review를 열 prerequisite은 이미 충족된 상태라는 뜻입니다.
- `review_gate_policy_promotion_execution_status=AWAIT_EXPLICIT_POLICY_REVIEW_DECISION`
  - readiness는 충족됐지만 explicit policy review decision이 아직 남아 있어 bounded review를 바로 실행하진 않는다는 뜻입니다.
- `review_gate_policy_promotion_execution_reason=READINESS_MET_BUT_EXPLICIT_POLICY_REVIEW_DECISION_IS_STILL_PENDING`
- `review_gate_policy_promotion_approval_status=PENDING_EXPLICIT_PROMOTION_APPROVAL`
  - execution readiness와 별도로, bounded promotion review 승인 자체는 아직 기록되지 않았다는 뜻입니다.
- `review_gate_policy_promotion_approval_reason=EXECUTION_READY_BUT_EXPLICIT_PROMOTION_APPROVAL_NOT_RECORDED`
- `review_gate_policy_promotion_approval_decision_status=AWAIT_EXPLICIT_PROMOTION_APPROVAL_DECISION`
  - approval criteria는 충족됐지만 explicit approval decision record는 아직 없다는 뜻입니다.
- `review_gate_policy_promotion_approval_decision_reason=APPROVAL_CRITERIA_MET_BUT_EXPLICIT_APPROVAL_NOT_RECORDED`
- `review_gate_policy_promotion_approval_record_status=PENDING_EXPLICIT_PROMOTION_APPROVAL_RECORD`
  - explicit approval decision이 아직 기록되지 않아 approval record도 아직 남기지 못한 상태라는 뜻입니다.
- `review_gate_policy_promotion_approval_record_reason=APPROVAL_DECISION_PENDING_AND_RECORD_NOT_WRITTEN`
- `review_gate_policy_promotion_review_run_status=PENDING_BOUNDED_PROMOTION_REVIEW_RUN`
  - explicit approval record가 아직 없어 actual bounded promotion review run도 아직 시작할 수 없다는 뜻입니다.
- `review_gate_policy_promotion_review_run_reason=EXPLICIT_APPROVAL_RECORD_NOT_WRITTEN_FOR_BOUNDED_REVIEW_RUN`
- `review_gate_policy_promotion_review_run_criteria_status=READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN`
  - actual run은 아직 pending이지만 bounded promotion review run을 열 prerequisite 자체는 이미 충족됐다는 뜻입니다.
- `review_gate_policy_promotion_review_run_criteria_reason=BOUNDED_REVIEW_RUN_PREREQUISITES_MET_BUT_APPROVAL_RECORD_PENDING`
- `review_gate_policy_promotion_review_run_decision_status=AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_DECISION`
  - prerequisite은 이미 충족됐지만, 마지막 review run decision은 아직 explicit approval record 미작성 때문에 pending이라는 뜻입니다.
- `review_gate_policy_promotion_review_run_decision_reason=REVIEW_RUN_CRITERIA_MET_BUT_APPROVAL_RECORD_NOT_WRITTEN`
- `review_gate_policy_promotion_review_run_approval_criteria_status=READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL`
  - 마지막 review run approval prerequisite 자체는 이미 충족됐고, approval record만 pending인 상태라는 뜻입니다.
- `review_gate_policy_promotion_review_run_approval_criteria_reason=REVIEW_RUN_APPROVAL_PREREQUISITES_MET_BUT_APPROVAL_RECORD_PENDING`
- `review_gate_policy_promotion_review_run_approval_decision_status=AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_DECISION`
  - 마지막 review run approval prerequisite은 이미 충족됐지만, approval decision 자체는 explicit approval record 미작성 때문에 아직 pending이라는 뜻입니다.
- `review_gate_policy_promotion_review_run_approval_decision_reason=REVIEW_RUN_APPROVAL_CRITERIA_MET_BUT_APPROVAL_RECORD_NOT_WRITTEN`
- `review_gate_policy_promotion_review_run_approval_status=PENDING_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL`
  - 마지막 review run decision 뒤 approval 자체도 아직 explicit approval record 미작성 때문에 pending이라는 뜻입니다.
- `review_gate_policy_promotion_review_run_approval_reason=REVIEW_RUN_DECISION_PENDING_BECAUSE_APPROVAL_RECORD_NOT_WRITTEN`
- `review_gate_policy_promotion_review_run_approval_record_criteria_status=READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD`
  - 마지막 review run approval record를 남길 prerequisite 자체는 이미 충족됐다는 뜻입니다.
- `review_gate_policy_promotion_review_run_approval_record_criteria_reason=REVIEW_RUN_APPROVAL_RECORD_PREREQUISITES_MET_BUT_RECORD_PENDING`
- `review_gate_policy_promotion_review_run_approval_record_status=PENDING_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD`
  - 마지막 review run approval decision이 아직 pending이라 approval record 자체도 아직 남기지 못한 상태라는 뜻입니다.
- `review_gate_policy_promotion_review_run_approval_record_reason=REVIEW_RUN_APPROVAL_DECISION_PENDING_AND_RECORD_NOT_WRITTEN`
- `review_gate_policy_promotion_review_run_approval_record_transition_status=AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITE`
  - 마지막 approval record write를 막는 것이 prerequisite 부족이 아니라 explicit record 미작성이라는 뜻입니다.
- `review_gate_policy_promotion_review_run_approval_record_transition_reason=APPROVAL_RECORD_CRITERIA_MET_BUT_RECORD_NOT_WRITTEN`
- `review_gate_policy_promotion_review_run_approval_record_write_status=PENDING_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITE`
  - transition prerequisite은 이미 닫혔고 final write 자체만 아직 실행되지 않았다는 뜻입니다.
- `review_gate_policy_promotion_review_run_approval_record_write_reason=APPROVAL_RECORD_TRANSITION_READY_BUT_WRITE_NOT_EXECUTED`
- `review_gate_interpretation_class=HISTORICAL_PRIMARY_BLOCKER_CURRENT_WINDOW_CLEAR`
  - primary full latest batch gate는 아직 historical blocker인데, supplemental recent-window current signal은 이미 clear 쪽으로 움직였다는 뜻입니다.
- `review_gate_operating_mode=PRIMARY_BASELINE_WITH_SUPPLEMENTAL_RECENT_WINDOW`
  - operator는 full latest batch gate를 historical baseline으로 두고, current live signal은 recent-window gate를 같이 보라는 뜻입니다.
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
- `primary_review_gate_blocker_class=MIXED_BATCH_NON_REAL_DOMINANCE_WITH_NO_REAL_USER_PATH`
  - full latest batch review gate는 현재 historical example inertia가 섞인 primary baseline으로 읽습니다.
- `recent_window_recommendation_review_reading=RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE`
  - recent 24h latest batch current-live signal은 이미 old `2622` dominance를 벗어난 상태라는 뜻입니다.
- `stable_dashboard_real_user_gate` 와 `stable_breakdown_real_user_cohort_gate` 가 deferred면
  - 아직 `REAL_USER` 운영 분포 비교는 열리지 않은 상태입니다.
