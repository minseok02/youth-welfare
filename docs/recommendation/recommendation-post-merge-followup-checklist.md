# recommendation post-merge follow-up checklist

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 recommendation / Gov24 closeout PR이 merge된 뒤

- 바로 남겨야 하는 기록
- 계속 관찰해야 하는 값
- `REAL_USER` traffic/cohort가 생겼을 때 다시 열 작업

을 한 장으로 고정합니다.

이 문서는 “merge 전 draft/reviewer-ready 판단”이 아니라 **merge 후 follow-up** 용입니다.

## merge 직후 바로 할 일

### 1. top-level 상태 문서 확인

아래 문서가 merge된 truth와 맞는지 다시 확인합니다.

- [start.md](../start.md)
- [current-state.md](../current-state.md)
- [phase-plan.md](../phase-plan.md)

핵심은 recommendation 관찰 진입점이 계속 `latest-overview` 인지, 그리고 main blocker가 여전히 **full latest batch historical inertia vs recent-window current signal 분리 해석** 인지 확인하는 것입니다.

### 2. daily one-shot 한 번 재실행

```bash
bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-overview.sh
```

가능하면 readiness 포함:

```bash
APP_BASE_URL='http://127.0.0.1:8082' \
ADMIN_EMAIL='<local admin email>' \
ADMIN_PASSWORD='<local admin password>' \
INCLUDE_REAL_USER_READINESS=true \
bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-overview.sh
```

이 단계는 merge 직후에도 baseline 해석이 그대로 유지되는지 확인하는 최소 smoke 입니다.

### 3. 최신 handoff artifact 남기기

```bash
bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-status-export.sh
```

최소 산출물:

- `tmp/recommendation-ai-exclusion-latest-status/latest-status-note.md`
- `tmp/recommendation-ai-exclusion-latest-status/latest-status.json`
- `tmp/recommendation-ai-exclusion-latest-overview/latest-overview-note.md`

## merge 후 계속 유지할 current 해석

merge 전 현재 PR 상태 reference:

- PR review readiness status:
  - `REVIEWER_READY`
- PR draft maintenance status:
  - `DRAFT_MAINTAINED_BY_POLICY_GATE`

현재 merge 뒤에도 기본 해석은 이것입니다.

- full latest batch review gate:
  - `DEFERRED_NON_REAL_LEADER_SIGNAL`
- full latest batch reading:
  - historical example latest batch dominance
- recent-window supplemental gate:
  - `RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE`
- review gate interpretation class:
  - `HISTORICAL_PRIMARY_BLOCKER_CURRENT_WINDOW_CLEAR`
- review gate operating mode:
  - `PRIMARY_BASELINE_WITH_SUPPLEMENTAL_RECENT_WINDOW`
- gate policy status:
  - `PRIMARY_BLOCKED_SUPPLEMENTAL_CLEAR`
- gate policy reason:
  - `HISTORICAL_PRIMARY_BLOCKER_CURRENT_WINDOW_CLEAR`
- current next step:
  - `USE_RECENT_WINDOW_AS_SUPPLEMENTAL_REVIEW_CONTEXT`
- `latest_drift_class=VOLATILE_ONLY_DRIFT`
- basic gate `PASS`
- strict gate `LATEST_OBSERVATION_CHANGED`

즉 merge가 곧 reopen을 뜻하지는 않습니다.

## merge 후 바로 reopen 하지 않는 조건

아래 조합이면 merge 뒤에도 계속 관찰만 합니다.

1. `REAL_USER` readiness gate가 deferred
2. `stable_baseline_changed=false`
3. latest 관찰 변화가 fresh window 흔들림 범위에 머묾
4. full latest batch gate는 stale historical example inertia를, recent-window gate는 current live signal을 보여 주는 상태
5. review gate decision class가 계속 `HISTORICAL_PRIMARY_BLOCKER_CURRENT_WINDOW_CLEAR` 로 유지됨
6. `gate_status=PASS` 여도 `gate_policy_status=PRIMARY_BLOCKED_SUPPLEMENTAL_CLEAR` 가 유지됨

이때 current decision은 계속:

- `keep observing`

## merge 후 reopen 후보가 되는 조건

아래 중 하나가 생기면 recommendation lane을 다시 엽니다.

1. `REAL_USER` traffic/cohort가 실제로 생김
2. `stable_baseline_changed=true`
3. zero-AI bucket이 기존 `INCOME_MISMATCH / STUDENT_AUDIENCE_MISMATCH` 중심에서 벗어나 반복됨
4. `latest-overview`, `latest-status`, `latest-gate`, readiness output과 active 문서가 다시 어긋남

## `REAL_USER` gate가 열리면 다시 할 일

이때부터는 아래 문서를 기준으로 움직입니다.

- [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)
- [recommendation-reopen-decision-runbook.md](./recommendation-reopen-decision-runbook.md)
- [recommendation-next-lane-brief.md](./recommendation-next-lane-brief.md)
- [recommendation-primary-audience-exclusion-decision-memo.md](./recommendation-primary-audience-exclusion-decision-memo.md)

실행 순서는 그대로:

1. `latest-overview`
2. readiness 단독 확인
3. `baseline-refresh-drift-check`
4. `latest-status-export`

## post-merge에서 트러블슈팅에 남길 최소 항목

- merge 직후 overview 결과
- readiness 포함 실행 여부
- `latest_drift_class`
- `stable_baseline_changed`
- `changed_keys`
- current decision:
  - `keep observing`
  - `reopen product decision`

## 한 줄 요약

merge 뒤의 기본값은 **closeout 기준선 유지 + `HISTORICAL_PRIMARY_BLOCKER_CURRENT_WINDOW_CLEAR` / `PRIMARY_BASELINE_WITH_SUPPLEMENTAL_RECENT_WINDOW` 운영 클래스 유지** 이고, stable baseline이 바뀌거나 recent/current signal 해석 경계가 다시 흔들릴 때만 reopen 판단으로 넘어갑니다.
