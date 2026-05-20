# recommendation ai exclusion latest overview runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 현재 recommendation AI exclusion latest 상태를

- latest export 갱신
- latest status
- latest gate
- strict latest gate
- 필요하면 `REAL_USER` readiness

순서로 한 번에 다시 보는 daily operator entrypoint 입니다.

개별 wrapper를 따로 치기 전에 지금 상태를 한 화면에서 다시 확인하고 싶을 때 씁니다.

같이 보면 좋은 문서:

- [recommendation-pr-review-brief.md](./recommendation-pr-review-brief.md)
- [recommendation-pr-draft-exit-checklist.md](./recommendation-pr-draft-exit-checklist.md)
- [recommendation-post-merge-followup-checklist.md](./recommendation-post-merge-followup-checklist.md)
- [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)

현재 이 runbook의 기본 해석은 아래와 같습니다.

- full latest batch review gate:
  - `DEFERRED_NON_REAL_LEADER_SIGNAL`
- recent-window supplemental reading:
  - `RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE`
- `latest_drift_class=VOLATILE_ONLY_DRIFT`
- basic gate `PASS`
- strict gate `LATEST_OBSERVATION_CHANGED`

즉 이 runbook은 지금 단계에서 “모델 튜닝”보다 **current baseline 유지와 historical full latest batch / current recent-window signal 분리 해석** 을 다시 확인하는 entrypoint 입니다.

다만 `REAL_USER` readiness를 같이 포함한 실행에서는 live gate가 이미 열렸는데도 baseline artifact 기반 `latest status` 가 아직 `WAIT_FOR_REAL_USER_TRAFFIC` 로 남을 수 있습니다. 이 값은 **older pre-live baseline snapshot pointer** 로 읽는 편이 맞고, current live truth는 새 `effective_operator_next_step`, `readiness_override_detected`, `readiness_override_reason`, `real_user_review_gate` 를 같이 읽어야 합니다.

## 기본 스크립트

```bash
bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-overview.sh
```

기본값은 stale JSON이면 자동으로 다시 갱신하는 쪽입니다.

```bash
AUTO_REFRESH_STATUS_JSON_IF_STALE=true \
bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-overview.sh
```

기본 `auto` 모드에서는 `APP_BASE_URL` 이 비어 있으면 로컬 기본값 `http://127.0.0.1:8082` 를 사용하고, admin 자격을 발견하면 readiness도 같이 포함하려고 시도합니다.

`REAL_USER` readiness도 같이 묶고 싶으면:

```bash
APP_BASE_URL='http://127.0.0.1:8082' \
ADMIN_EMAIL='<local admin email>' \
ADMIN_PASSWORD='<local admin password>' \
INCLUDE_REAL_USER_READINESS=true \
bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-overview.sh
```

## 주요 출력

- `auto_refresh_status_json_if_stale`
- `[latest status export]`
- `[latest status]`
- `[latest gate]`
- `[latest gate (strict)]`
- `strict_gate_exit_code`
- `overview_artifact_dir`
- `overview_summary`
- `overview_note`
- `overview_json`
- `latest_artifact_link`
- `latest_summary_link`
- `latest_note_link`
- `latest_json_link`
- `[real-user readiness]`

## 읽는 법

- `latest status` 에서 `operator_next_step=WAIT_FOR_REAL_USER_TRAFFIC`
  - baseline artifact가 아직 older pre-live wait-state snapshot을 가리키는 경우입니다. current live truth는 readiness/review gate 결과를 같이 읽어야 합니다.
- `latest gate` 가 `PASS`
  - stable baseline/interpretation 기준으로는 현재 latest 상태가 허용 범위입니다.
- `latest gate (strict)` 가 `LATEST_OBSERVATION_CHANGED`
  - fresh window 관찰값 흔들림까지 막는 strict 정책에서는 아직 fail 이라는 뜻입니다.
- `strict_gate_exit_code=1`
  - strict gate fail을 wrapper가 요약한 값입니다. overview 자체는 여기서 중단하지 않습니다.
- `latest-overview-summary.txt`
  - daily handoff용 compact artifact 입니다. 핵심 gate/status/next-step 값만 다시 읽고 싶을 때 이 파일을 먼저 보면 됩니다.
- `latest-overview-note.md`
  - 사람용 handoff note 입니다.
- `latest-overview.json`
  - 후속 자동화나 machine-readable 확인이 필요할 때 읽는 artifact 입니다.
- `real_user_readiness_included=true`
  - overview에 readiness 결과도 같이 포함됐다는 뜻입니다.
- `real_user_readiness_skip_reason=AUTO_SKIP_MISSING_ADMIN_EMAIL`, `AUTO_SKIP_MISSING_ADMIN_PASSWORD`
  - 기본 `auto` 모드에서 무엇이 빠져 readiness를 생략했는지 더 구체적으로 보여 줍니다.
- `real_user_readiness_next_action=SET_ADMIN_PASSWORD_OR_ADMIN_PASSWORD_FILE`
  - auto discovery가 어디까지 됐는지 본 뒤 바로 다음 조치까지 한 줄로 안내합니다.
- `real_user_readiness_next_action=WAIT_FOR_REAL_USER_LEADER_SIGNAL`
  - readiness gate는 열렸지만 current local처럼 review gate가 `DEFERRED_NON_REAL_LEADER_SIGNAL` 인 경우의 실제 다음 행동입니다.
- `real_user_readiness_detected_admin_email`, `real_user_readiness_has_admin_password`
  - auto discovery가 어디까지 성공했는지 바로 확인하는 값입니다.
- `readiness_override_detected=true`
  - live readiness gate는 이미 열렸지만 baseline artifact 기반 `operator_next_step` 은 아직 old wait 상태라는 뜻입니다.
- `effective_operator_next_step=WAIT_FOR_REAL_USER_LEADER_SIGNAL`
  - current local처럼 `dashboard_real_user_gate=READY_REAL_USER_TRAFFIC`, `breakdown_real_user_cohort_gate=READY_REAL_USER_COHORT` 는 열렸지만 `real_user_review_gate=DEFERRED_NON_REAL_LEADER_SIGNAL` 인 경우의 실제 다음 행동입니다.
- `real_user_review_gate=DEFERRED_NON_REAL_LEADER_SIGNAL`
  - `REAL_USER` traffic/cohort는 확보됐지만 top1 leader가 아직 non-real signal 위주라 review gate까지는 안 열렸다는 뜻입니다.
- `real_user_top1_leader_real_user_users=0`, `real_user_top1_leader_signal_summary=LOCAL_SEED_WITHOUT_REAL_USER_LEADER`
  - review gate가 왜 deferred인지 같이 읽는 보조 설명입니다.
- `REAL_USER` gate가 실제로 열린 뒤 다음 순서를 다시 보려면
  - [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)
- 현재 draft 유지/해제와 merge 뒤 follow-up 판단을 같이 보려면
  - [recommendation-pr-draft-exit-checklist.md](./recommendation-pr-draft-exit-checklist.md)
  - [recommendation-post-merge-followup-checklist.md](./recommendation-post-merge-followup-checklist.md)
