# collect source resilience audit runbook

문서군 진입점: [collect-docs-index.md](./collect-docs-index.md)

## 목적

이 문서는 source별 collect lane이 현재 어떤 보호 장치를 가지고 있는지 compact하게 다시 읽는 절차입니다.

운영자가 알고 싶은 핵심은 보통 아래 셋입니다.

1. source별 retry/duplicate-run guard가 빠지지 않았는가
2. rate-limit 보호가 필요한 lane에 아직 붙어 있는가
3. recent failed job/open circuit 때문에 baseline 자체가 흔들리고 있지는 않은가

## 실행

```bash
bash deploy/smoke/run-local-collect-source-resilience-audit.sh
```

server/RDS에서는 아래를 사용합니다.

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' \
bash deploy/smoke/run-local-collect-source-resilience-audit.sh
```

## 요약 항목

- `retry_lane_keys`
- `rate_limit_lane_keys`
- `lock_guard_lane_keys`
- `missing_retry_lane_keys`
- `missing_rate_limit_lane_keys`
- `missing_lock_guard_lane_keys`
- `fail_on_empty_snapshot_lane_keys`
- `failed_jobs_in_window`
- `open_collect_circuit_keys`
- `decision_class`

## 해석

- `BASELINE_HEALTHY`
  - source별 보호 장치 metadata가 기대대로 남아 있고, 현재 failed/open circuit baseline도 안정적입니다.
- `ATTENTION_REQUIRED`
  - recent failed job 또는 open circuit가 있습니다. lane health부터 다시 확인해야 합니다.
- `RESILIENCE_DRIFT`
  - retry/rate-limit/duplicate-run guard metadata 중 하나 이상이 빠졌습니다. source resilience contract drift로 봅니다.
