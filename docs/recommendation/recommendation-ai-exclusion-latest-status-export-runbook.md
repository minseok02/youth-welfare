# recommendation ai exclusion latest status export runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

관련 문서:

- [recommendation-pr-review-brief.md](./recommendation-pr-review-brief.md)
- [recommendation-pr-draft-exit-checklist.md](./recommendation-pr-draft-exit-checklist.md)
- [recommendation-post-merge-followup-checklist.md](./recommendation-post-merge-followup-checklist.md)
- [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)
- [recommendation-ai-exclusion-latest-status-runbook.md](./recommendation-ai-exclusion-latest-status-runbook.md)

## 현재 단계 해석

현재 local 기본값은 full latest batch review gate `DEFERRED_NON_REAL_LEADER_SIGNAL`, recent-window supplemental reading `RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE`, latest recommendation reading `VOLATILE_ONLY_DRIFT` 입니다.

즉 이 export runbook은 새로운 tuning 결론을 만드는 문서가 아니라, **현재 latest refresh/drift artifact를 사람용 note와 machine-readable JSON으로 다시 포장해 handoff/운영 메모에 쓰기 쉽게 만드는 export helper** 로 읽는 것이 맞습니다.

## 목적

이 문서는 latest refresh summary와 latest drift summary를 읽어
바로 붙여넣기 가능한 Markdown 메모를 생성하는 entrypoint 입니다.

재실행 없이 현재 남아 있는 latest artifact 기준으로

- stable baseline
- latest volatile observation
- latest drift check

를 한 장짜리 note로 뽑고 싶을 때 씁니다.

## 기본 스크립트

```bash
bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-status-export.sh
```

## 주요 출력

- `latest-status-note.md`
- `latest-status.json`
- `latest_artifact_link`
- `latest_note_link`
- `latest_json_link`

`latest-status-note.md` 는 사람용 메모이고, `latest-status.json` 은 후속 스크립트나 자동화가 읽기 쉬운 machine-readable artifact 입니다.
둘 다 `generated_at_utc`, `generated_at_kst` 를 같이 남기므로, UTC artifact 경로(`...Z`)와 KST 실행 날짜를 한 화면에서 같이 읽을 수 있습니다.
이제 `operator_next_step`, `effective_operator_next_step`, `gate_action_class`, `gate_policy_status`, `gate_policy_reason` 을 같이 남기므로, historical baseline pointer와 current local 해석, 그리고 짧은 실행/정책 판정을 분리해서 읽을 수 있습니다.
이제는 `review_gate_context` 도 함께 남기므로, handoff note/json만 열어도 full latest batch primary gate(`MIXED_BATCH_NON_REAL_DOMINANCE_WITH_NO_REAL_USER_PATH`)와 recent-window supplemental reading(`RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE`)을 같이 볼 수 있습니다. 여기에 `review_gate_interpretation_class=HISTORICAL_PRIMARY_BLOCKER_CURRENT_WINDOW_CLEAR`, `review_gate_operating_mode=PRIMARY_BASELINE_WITH_SUPPLEMENTAL_RECENT_WINDOW`, `review_gate_policy_candidate_status=RECENT_WINDOW_POLICY_CANDIDATE`, `review_gate_policy_candidate_reason=PRIMARY_GATE_BLOCKED_BY_STALE_ALL_TIME_EXAMPLE_REFERENCE_BUT_RECENT_WINDOW_CLEAR`, `review_gate_policy_promotion_status=REQUIRES_EXPLICIT_POLICY_CHANGE_REVIEW`, `review_gate_policy_promotion_reason=RECENT_WINDOW_IS_A_CANDIDATE_BUT_PRIMARY_BASELINE_IS_STILL_ALL_TIME_LATEST`, `review_gate_policy_promotion_action_status=KEEP_PRIMARY_BASELINE`, `review_gate_policy_promotion_action_reason=PROMOTION_STILL_REQUIRES_EXPLICIT_POLICY_REVIEW`, `review_gate_policy_promotion_readiness_status=READY_FOR_BOUNDED_PROMOTION_REVIEW`, `review_gate_policy_promotion_readiness_reason=EXPLICIT_POLICY_REVIEW_PENDING_WITH_BOUNDED_REVIEW_PREREQUISITES_MET`, `review_gate_policy_promotion_execution_status=AWAIT_EXPLICIT_POLICY_REVIEW_DECISION`, `review_gate_policy_promotion_execution_reason=READINESS_MET_BUT_EXPLICIT_POLICY_REVIEW_DECISION_IS_STILL_PENDING`, `review_gate_policy_promotion_approval_criteria_status=READY_FOR_EXPLICIT_PROMOTION_APPROVAL`, `review_gate_policy_promotion_approval_criteria_reason=PRIMARY_STALENESS_AND_RECENT_WINDOW_SIGNAL_CONFIRMED`, `review_gate_policy_promotion_approval_status=PENDING_EXPLICIT_PROMOTION_APPROVAL`, `review_gate_policy_promotion_approval_reason=EXECUTION_READY_BUT_EXPLICIT_PROMOTION_APPROVAL_NOT_RECORDED`, `review_gate_policy_promotion_approval_decision_status=AWAIT_EXPLICIT_PROMOTION_APPROVAL_DECISION`, `review_gate_policy_promotion_approval_decision_reason=APPROVAL_CRITERIA_MET_BUT_EXPLICIT_APPROVAL_NOT_RECORDED`, `review_gate_policy_promotion_approval_record_status=PENDING_EXPLICIT_PROMOTION_APPROVAL_RECORD`, `review_gate_policy_promotion_approval_record_reason=APPROVAL_DECISION_PENDING_AND_RECORD_NOT_WRITTEN`, `review_gate_policy_promotion_review_run_status=PENDING_BOUNDED_PROMOTION_REVIEW_RUN`, `review_gate_policy_promotion_review_run_reason=EXPLICIT_APPROVAL_RECORD_NOT_WRITTEN_FOR_BOUNDED_REVIEW_RUN`, `review_gate_policy_promotion_review_run_decision_status=AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_DECISION`, `review_gate_policy_promotion_review_run_decision_reason=REVIEW_RUN_CRITERIA_MET_BUT_APPROVAL_RECORD_NOT_WRITTEN`, `review_gate_policy_promotion_review_run_approval_criteria_status=READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL`, `review_gate_policy_promotion_review_run_approval_criteria_reason=REVIEW_RUN_APPROVAL_PREREQUISITES_MET_BUT_APPROVAL_RECORD_PENDING`, `review_gate_policy_promotion_review_run_approval_decision_status=AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_DECISION`, `review_gate_policy_promotion_review_run_approval_decision_reason=REVIEW_RUN_APPROVAL_CRITERIA_MET_BUT_APPROVAL_RECORD_NOT_WRITTEN`, `review_gate_policy_promotion_review_run_approval_status=PENDING_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL`, `review_gate_policy_promotion_review_run_approval_reason=REVIEW_RUN_APPROVAL_DECISION_PENDING_BECAUSE_APPROVAL_RECORD_NOT_WRITTEN` 도 같이 남겨서, operator가 raw gate 값을 다시 합치지 않고도 current 운영 해석과 승격 후보/승격 보류/즉시 행동/검토 시작 readiness/실행 대기/승인 근거/승인 대기/승인 decision 대기/승인 record pending/실제 review run pending/마지막 run decision pending/마지막 run approval prerequisite ready/마지막 run approval decision pending/마지막 run approval pending 까지를 바로 읽을 수 있습니다.
현재 local 기준으로는 `gate_action_class=READ_PRIMARY_AND_SUPPLEMENTAL_REVIEW_GATES`, `gate_policy_status=PRIMARY_BLOCKED_SUPPLEMENTAL_CLEAR`, `gate_policy_reason=HISTORICAL_PRIMARY_BLOCKER_CURRENT_WINDOW_CLEAR`, `review_gate_policy_candidate_status=RECENT_WINDOW_POLICY_CANDIDATE`, `review_gate_policy_promotion_status=REQUIRES_EXPLICIT_POLICY_CHANGE_REVIEW`, `review_gate_policy_promotion_action_status=KEEP_PRIMARY_BASELINE`, `review_gate_policy_promotion_readiness_status=READY_FOR_BOUNDED_PROMOTION_REVIEW`, `review_gate_policy_promotion_execution_status=AWAIT_EXPLICIT_POLICY_REVIEW_DECISION`, `review_gate_policy_promotion_approval_criteria_status=READY_FOR_EXPLICIT_PROMOTION_APPROVAL`, `review_gate_policy_promotion_approval_status=PENDING_EXPLICIT_PROMOTION_APPROVAL`, `review_gate_policy_promotion_approval_decision_status=AWAIT_EXPLICIT_PROMOTION_APPROVAL_DECISION`, `review_gate_policy_promotion_approval_record_status=PENDING_EXPLICIT_PROMOTION_APPROVAL_RECORD`, `review_gate_policy_promotion_review_run_status=PENDING_BOUNDED_PROMOTION_REVIEW_RUN`, `review_gate_policy_promotion_review_run_decision_status=AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_DECISION`, `review_gate_policy_promotion_review_run_approval_criteria_status=READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL`, `review_gate_policy_promotion_review_run_approval_decision_status=AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_DECISION`, `review_gate_policy_promotion_review_run_approval_status=PENDING_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL`, `review_gate_policy_promotion_review_run_approval_record_status=PENDING_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD` 를 먼저 읽는 편이 맞습니다.

여기에 `review_gate_policy_promotion_review_run_approval_record_criteria_status=READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD`, `review_gate_policy_promotion_review_run_approval_record_criteria_reason=REVIEW_RUN_APPROVAL_RECORD_PREREQUISITES_MET_BUT_RECORD_PENDING` 도 같이 보면, final approval record는 아직 pending이지만 그 record를 남길 prerequisite은 이미 충족됐다는 점까지 한 화면에서 읽을 수 있습니다.
같은 마지막 transition 층과 final write 층도 note/json에 같이 남깁니다. 현재 local 기준 값은 `review_gate_policy_promotion_review_run_approval_record_transition_status=AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITE`, `review_gate_policy_promotion_review_run_approval_record_transition_reason=APPROVAL_RECORD_CRITERIA_MET_BUT_RECORD_NOT_WRITTEN`, `review_gate_policy_promotion_review_run_approval_record_write_status=PENDING_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITE`, `review_gate_policy_promotion_review_run_approval_record_write_reason=APPROVAL_RECORD_TRANSITION_READY_BUT_WRITE_NOT_EXECUTED` 입니다.

## 읽는 법

- note의 `Stable Baseline` 섹션은 고정 baseline 메모로 봅니다.
- `Latest Observation` 섹션은 fresh window 관찰값 메모로 봅니다.
- `Latest Drift Check` 섹션에서
  - `stable_baseline_changed=false`, `latest_observation_changed=true` 면
    fresh observation만 흔들린 것으로 읽습니다.
