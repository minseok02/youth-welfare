# collect governance observation runbook

문서군 진입점: [collect-docs-index.md](./collect-docs-index.md)

## 목적

이 문서는 collect/admin runtime을 다시 설계하는 문서가 아닙니다.
현재 scheduled/rotation/manual lane inventory와 latest run summary를
operator가 **하루 단위로 어떻게 읽을지** 를 짧게 고정하는 runbook 입니다.

핵심 질문은 아래입니다.

- 지금 collect governance baseline이 건강한가
- 아니라면 실패인지, partial인지, circuit인지 무엇을 먼저 봐야 하는가

## 기본 명령

로컬/기본:

```bash
APP_BASE_URL='http://127.0.0.1:8082' \
bash deploy/smoke/run-local-collect-governance-observation-suite.sh
```

운영 서버/RDS:

```bash
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
bash deploy/smoke/run-local-collect-governance-observation-suite.sh
```

## 먼저 볼 artifact

- `tmp/collect-governance-observation/latest-collect-governance-observation-summary.txt`
- `tmp/collect-governance-observation/latest-collect-governance-observation-note.md`
- `tmp/collect-governance-observation/latest-collect-governance-observation.json`

사람이 먼저 읽을 때는 `note.md`, 자동 파싱은 `summary/json` 을 우선합니다.

## 먼저 볼 값

- `decision_class`
- `failed_jobs_in_window`
- `partial_success_jobs_in_window`
- `open_collect_circuits`
- `scheduled_lane_count`
- `rotation_lane_count`
- `manual_lane_count`
- `latest_failed_lane_keys`
- `latest_partial_lane_keys`
- `missing_latest_lane_keys`
- `next_action`

## 상태 해석

### `decision_class=BASELINE_HEALTHY`

- collect governance baseline은 건강합니다.
- scheduled/rotation/manual lane inventory를 그대로 유지합니다.
- 다음 액션:
  - current-state 문서만 유지 관찰

### `decision_class=PARTIAL_SUCCESS_REVIEW`

- hard failure는 없지만 최근 partial success가 있습니다.
- lane 최신 실행 요약과 config summary를 먼저 봅니다.
- 다음 문서:
  - [collect-ops.md](./collect-ops.md)

### `decision_class=ATTENTION_REQUIRED`

- failed job 또는 open circuit가 있습니다.
- collect governance surface를 healthy로 읽지 않습니다.
- 다음 액션:
  - `bash deploy/smoke/run-local-admin-collect-failures-smoke.sh`

## 운영 handoff 최소 기록

- 실행 시각
- `decision_class`
- `failed_jobs_in_window`
- `partial_success_jobs_in_window`
- `open_collect_circuits`
- `scheduled_lane_count`
- `rotation_lane_count`
- `manual_lane_count`
- `latest_failed_lane_keys`
- `latest_partial_lane_keys`
- `missing_latest_lane_keys`
- `next_action`

## 한 줄 요약

`collect governance observation suite` 는 collect lane inventory를 daily operator가 빠르게 읽는 entrypoint이고,
`ATTENTION_REQUIRED` 가 아니면 기본 해석은 **baseline 유지** 입니다.
