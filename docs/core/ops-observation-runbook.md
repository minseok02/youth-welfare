# Ops Observation Runbook

문서군 진입점: [system-docs-index.md](./system-docs-index.md)

## 목적

daily operator가 `health -> admin dashboard -> collect failures -> recommendation breakdowns` read-only baseline을 raw stdout 대신 compact artifact로 다시 읽게 합니다.

entrypoint는 아래 wrapper입니다.

- `bash deploy/smoke/run-local-ops-observation-suite.sh`
- 운영 서버/RDS:
  - `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-ops-observation-suite.sh`

이 wrapper는 기존 `run-local-ops-baseline-suite.sh` child artifact를 재사용합니다.

## latest artifact

- `tmp/ops-observation/latest-ops-observation-summary.txt`
- `tmp/ops-observation/latest-ops-observation-note.md`
- `tmp/ops-observation/latest-ops-observation.json`

`KEEP_ARTIFACTS=false` 기본값에서도 stable snapshot은 남습니다.

## summary/json에서 먼저 볼 값

- `decision_class`
- `collect_failed_jobs_in_window`
- `collect_partial_success_jobs_in_window`
- `open_collect_circuits`
- `recommendation_real_user_traffic_gate_in_window`
- `recommendation_review_gate`
- `recommendation_top1_leader_signal_summary`
- `recommendation_recent_window_review_reading`
- `suite_duration_ms`
- `next_action`

## 현재 해석

### `decision_class=BASELINE_HEALTHY`

- collect failure/partial/circuit 기준선이 건강하다는 뜻입니다.
- admin dashboard summary와 recommendation breakdown surface는 current-state 계약을 유지하는 상태로 읽습니다.

### `decision_class=INVESTIGATE_COLLECT_DRIFT`

- collect 실패/partial/circuit이 생겼다는 뜻입니다.
- 먼저 collect failures child artifact를 보고, 그 다음 recommendation/admin surface가 같은 시점에 함께 흔들렸는지 확인합니다.

## 다음 액션

- `next_action=docs/core/ops-baseline-runbook.md` 이면 raw child stdout 세 개와 운영 baseline runbook으로 내려갑니다.
- collect lane inventory/latestRun detail이 필요하면 [../collect/collect-current-state.md](../collect/collect-current-state.md) 를 같이 봅니다.
