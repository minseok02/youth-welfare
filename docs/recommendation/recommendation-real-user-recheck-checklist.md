# recommendation real-user recheck checklist

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 `REAL_USER` traffic/cohort가 실제로 생긴 뒤 recommendation exclusion baseline을 **어떤 순서로 다시 확인할지** 한 장으로 고정합니다.

질문은 이것입니다.

- 지금 gate가 실제로 열렸는가
- local baseline과 다른 drift가 생겼는가
- 지금 단계가 관찰인지, 제품 판단 reopen인지

## 실행 순서

### 1. daily one-shot 재실행

```bash
APP_BASE_URL='http://127.0.0.1:8082' \
ADMIN_EMAIL='<local admin email>' \
ADMIN_PASSWORD='<local admin password>' \
INCLUDE_REAL_USER_READINESS=true \
bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-overview.sh
```

먼저 볼 값:

- `operator_next_step`
- `latest_drift_class`
- `gate_status`
- `strict_gate_reason`
- `real_user_dashboard_gate`
- `real_user_breakdown_cohort_gate`
- `real_user_distribution_executed`

### 2. readiness가 실제로 열렸는지 단독 확인

```bash
APP_BASE_URL='http://127.0.0.1:8082' \
ADMIN_EMAIL='<local admin email>' \
ADMIN_PASSWORD='<local admin password>' \
bash deploy/smoke/run-local-real-user-exclusion-readiness-check.sh
```

이 단계는 `latest-overview` artifact에 readiness가 포함됐더라도, gate 해석만 따로 다시 고정하고 싶을 때 씁니다.

### 2-1. review-gate blocker를 mixed/latest batch 기준으로 분리 확인

```bash
bash deploy/smoke/run-local-recommendation-review-gate-blocker-audit.sh
```

이 단계는 readiness가 이미 열렸는데도 review gate가 계속 `DEFERRED_NON_REAL_LEADER_SIGNAL` 인 경우, blocker가 `REAL_USER` 부족이 아니라 mixed latest batch leader dominance 인지 따로 고정하고 싶을 때 씁니다.

### 2-2. historical latest batch와 recent-window current signal을 분리 확인

```bash
bash deploy/smoke/run-local-recommendation-review-gate-staleness-audit.sh
bash deploy/smoke/run-local-recommendation-review-gate-recent-window-audit.sh
```

이 단계는 full latest batch review gate가 stale historical example inertia를 얼마나 포함하는지, recent 24h current signal은 이미 달라졌는지 따로 고정하고 싶을 때 씁니다.

### 3. baseline refresh drift check

```bash
RUN_COUNT=2 \
bash deploy/smoke/run-local-recommendation-ai-exclusion-baseline-refresh-drift-check.sh
```

먼저 볼 값:

- `interpretation_changed`
- `stable_baseline_changed`
- `latest_observation_changed`
- `changed_keys`

핵심 해석:

- `stable_baseline_changed=false`
  - 아직 stable baseline 회귀는 아님
- `stable_baseline_changed=true`
  - 운영 기준선이 실제로 흔들렸을 가능성이 큼

### 4. latest status export 남기기

```bash
bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-status-export.sh
```

산출물:

- `tmp/recommendation-ai-exclusion-latest-status/latest-status-note.md`
- `tmp/recommendation-ai-exclusion-latest-status/latest-status.json`

이 단계는 handoff와 reopen 판단 메모용입니다.

## reopen 판단 전 최소 체크

아래 넷을 같이 봅니다.

1. `REAL_USER` readiness gate가 실제로 열렸는가
2. stable baseline drift가 생겼는가
3. full latest batch review gate와 recent-window supplemental gate가 같은 방향인가
4. zero-AI bucket이 여전히 `INCOME_MISMATCH / STUDENT_AUDIENCE_MISMATCH` 중심인가

## 해석 규칙

### 1. gate가 아직 deferred

- 아직 제품 reopen 단계가 아닙니다.
- current 해석 유지:
  - readiness gate deferred면 `WAIT_FOR_REAL_USER_TRAFFIC`
  - readiness는 열렸지만 review gate가 stale historical latest batch에 묶여 있으면 `USE_RECENT_WINDOW_AS_SUPPLEMENTAL_REVIEW_CONTEXT`

### 2. gate는 열렸지만 stable baseline unchanged

- current 해석 유지:
  - 관찰 계속
- fresh observation만 바뀌면
  - `VOLATILE_ONLY_DRIFT` 로 읽습니다.
- full latest batch gate와 recent-window gate가 다르면
  - stale historical inertia와 current live signal을 분리해서 같이 기록합니다.

### 3. gate가 열리고 stable baseline changed

- 그때부터가 실제 reopen 후보입니다.
- 아래 문서를 같이 봅니다:
  - [recommendation-primary-audience-exclusion-decision-memo.md](./recommendation-primary-audience-exclusion-decision-memo.md)
  - [recommendation-reopen-decision-runbook.md](./recommendation-reopen-decision-runbook.md)
  - [recommendation-next-lane-brief.md](./recommendation-next-lane-brief.md)

## 실행 후 남길 최소 기록

- 실행 시각: UTC / KST 둘 다
- `latest_drift_class`
- `gate_status`
- `strict_gate_reason`
- `real_user_dashboard_gate`
- `real_user_breakdown_cohort_gate`
- `real_user_distribution_executed`
- `full_latest_batch_review_reading`
- `recent_window_review_reading`
- `stable_baseline_changed`
- `changed_keys`
- current decision:
  - `keep observing`
  - `reopen product decision`

## 한 줄 요약

`REAL_USER` 표본이 생기면 **latest-overview -> readiness -> baseline-refresh-drift-check -> latest-status-export** 순서로 다시 태우고, stable baseline이 바뀐 경우에만 reopen 판단으로 넘어갑니다.
