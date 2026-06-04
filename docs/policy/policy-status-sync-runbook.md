# policy status sync runbook

## 목적

이 문서는 `ACTIVE/UPCOMING` 인데 이미 신청 종료일이 지난 row를 실제로 정리하는 수동 운영 경로입니다.

현재 `policy-application-period-quality-audit` 의 핵심 actionable 축은 broad 누락이 아니라 아래 mismatch 입니다.

- `ACTIVE/UPCOMING` + 과거 `apply_end_date`
- `CLOSED` + 미래 `apply_end_date`

## 수동 실행 API

```bash
POST /api/admin/policies/status-sync
```

응답:

- `closedCount`
- `activatedCount`
- `reopenedCount`
- `clusterAiCacheCleanupExecuted`

이 API는 내부적으로 `StatusUpdateService` 를 즉시 실행해:

1. 만료된 `ACTIVE` 정책을 `CLOSED` 로 내리고
2. 시작된 `UPCOMING` 정책을 `ACTIVE` 로 올리고
3. 미래 신청기간이 남아 있는 `CLOSED` 정책을 `ACTIVE/UPCOMING` 으로 다시 열고
4. cluster AI cache TTL cleanup을 같이 실행합니다.

## smoke

```bash
bash deploy/smoke/run-local-policy-status-sync-smoke.sh
```

server/RDS에서는 아래를 사용합니다.

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' \
bash deploy/smoke/run-local-policy-status-sync-smoke.sh
```

## 해석

- `before_active_past_end_* > after_active_past_end_*`
  - 수동 sync가 실제로 mismatch를 줄였습니다.
- `closedCount > 0`
  - 상태가 stale 했던 정책이 즉시 정리됐습니다.
- `reopenedCount > 0`
  - `CLOSED + 미래 apply_end_date` row 중 reopen 가능한 정책이 다시 노출 상태로 복구됐습니다.
- `clusterAiCacheCleanupExecuted=true`
  - scheduled path와 같은 TTL cleanup도 함께 실행됐습니다.
