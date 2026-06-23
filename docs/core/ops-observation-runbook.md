# Ops Observation Runbook

문서군 진입점: [system-docs-index.md](./system-docs-index.md)

## 목적

daily operator가 `health -> admin dashboard -> collect failures -> recommendation breakdowns` read-only baseline을 raw stdout 대신 compact artifact로 다시 읽게 합니다.

entrypoint는 아래 wrapper입니다.

- `bash deploy/smoke/run-local-ops-observation-suite.sh`
- 운영 서버/RDS:
  - `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-ops-observation-suite.sh`

이 wrapper는 기존 `run-local-ops-baseline-suite.sh` child artifact를 재사용합니다.

운영 서버/RDS 경로에서는 admin password file이 없어도 됩니다.

- `ADMIN_ACCESS_TOKEN` 이 있으면 그대로 재사용합니다.
- 없으면 `ENV_FILE` 의 `JWT_SECRET` 과 DB query credential로 short-lived admin access token을 bounded mint 합니다.
- 즉 기본 서버 명령은 운영 비밀번호 파일 없이도 닫히는 계약입니다.

## latest artifact

- `tmp/ops-observation/latest-ops-observation-summary.txt`
- `tmp/ops-observation/latest-ops-observation-note.md`
- `tmp/ops-observation/latest-ops-observation.json`
- `tmp/performance/app-log-observability/latest-app-log-observability-summary.txt`
- `tmp/performance/nginx-log-observability/latest-nginx-log-observability-summary.txt`

`KEEP_ARTIFACTS=false` 기본값에서도 stable snapshot은 남습니다.

## summary/json에서 먼저 볼 값

- `decision_class`
- `collect_failed_jobs_in_window`
- `collect_partial_success_jobs_in_window`
- `open_collect_circuits`
- `policy_data_triage_decision_class`
- `policy_data_triage_next_action`
- `recommendation_real_user_traffic_gate_in_window`
- `recommendation_review_gate`
- `recommendation_top1_leader_signal_summary`
- `recommendation_recent_window_review_reading`
- `suite_duration_ms`
- `next_action`

## log observation에서 먼저 볼 값

ops baseline이 흔들리면 app/nginx 로그 관찰을 같이 봅니다.

```bash
bash deploy/performance/run-local-app-log-observability-baseline.sh
bash deploy/performance/run-local-nginx-log-observability-baseline.sh
bash deploy/performance/evaluate-log-alert-thresholds.sh
```

- app: `api_request_count`, `api_status_*`, `api_error_code_*`, `raw_error_lines`, `auth_audit`, `recommendation_run`, `notification_attempt`, `user_action`
- app endpoint latency: `api_duration_by_path`, `api_slow_samples`
- nginx: `request_time_available`, `upstream_time_available`, `status_class_*`, `request_time p95/p99`

초기 warning/critical 기준은 [log-alert-thresholds.md](./log-alert-thresholds.md)를 따릅니다.

웹훅까지 붙인 운영 cron은 아래 wrapper를 사용합니다.

```bash
LOG_ALERT_APP_SINCE=10m \
LOG_ALERT_NGINX_TAIL_LINES=2000 \
bash deploy/ops/send-log-alert.sh
```

24시간 threshold 재산출은 아래 명령으로 고정합니다.

```bash
LOG_ALERT_TUNE_WINDOW=24h \
NGINX_LOG_TUNE_TAIL_LINES=50000 \
bash deploy/performance/tune-log-alert-thresholds.sh
```

관리자 dashboard는 DB 기반 로그 테이블 요약을 보여주고, app/nginx 파일 artifact는 host ops script와 webhook에서 다룹니다. 운영 app 컨테이너는 read-only이고 host `tmp/performance` 를 mount하지 않으므로 dashboard API가 파일 artifact를 직접 읽는 구조로 확장하지 않습니다.

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
