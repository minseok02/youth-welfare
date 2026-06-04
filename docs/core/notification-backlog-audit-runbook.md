# notification backlog audit runbook

## 목적

이 문서는 알림 운영 backlog를

- 안 읽은 알림
- 장기 미열람 알림
- 재시도 대기 failed notification
- 종결 failed notification

으로 나눠 읽는 절차를 고정합니다.

## 어디서 보나

- smoke:
  - `bash deploy/smoke/run-local-notification-backlog-audit.sh`
- admin dashboard summary / attention:
  - `unreadAlerts`
  - `retryableFailedNotifications`
  - `terminalFailedNotifications`
  - `notification-backlog`

## 실행

로컬:

```bash
bash deploy/smoke/run-local-notification-backlog-audit.sh
```

server/RDS:

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' \
bash deploy/smoke/run-local-notification-backlog-audit.sh
```

## 현재 읽는 기준

- `terminal_failed_total > 0`
  - `TERMINAL_NOTIFICATION_FAILURE_PRIORITY`
  - 채널 자체 실패, 잘못된 주소, 영구 오류를 먼저 본다.

- `retryable_failed_due_now > 0`
  - `RETRYABLE_NOTIFICATION_RETRY_PRIORITY`
  - retry runner / 채널 상태 / `next_retry_at` 경계를 먼저 본다.

- `stale_unread_7d > 0`
  - `STALE_UNREAD_ALERT_REVIEW_PRIORITY`
  - unread 알림이 장기 적체된 상태다.

- `unread_total > 0`
  - `UNREAD_ALERT_BACKLOG`
  - 실패는 없지만 읽지 않은 알림이 남아 있다.

- 그 외
  - `BASELINE_HEALTHY`

## 세부 해석

### unread alerts

- `RECOMMENDATION_DIGEST`
  - 추천 digest가 실제로 사용자에게 도달했는지, 너무 잦은지 본다.
- `DEADLINE_REMINDER`
  - 마감 임박 알림이 unread로 남는지 본다.
- `SYSTEM`
  - 시스템 공지성 알림이 불필요하게 쌓이는지 본다.

### retryable failed notifications

- `next_retry_at <= now`
  - 바로 다시 봐야 하는 backlog
- `next_retry_at > now`
  - 이미 스케줄된 상태

### terminal failed notifications

- 더 이상 자동 retry되지 않는 실패다.
- 채널별(`email`, `kakao`)로 먼저 나눠 본다.

## 운영 메모 기준

- unread가 많은데 failed가 없으면
  - `전송 실패보다 사용자 미열람 backlog로 해석`
- retryable failed가 due 상태면
  - `retry runner / 채널 상태 확인 필요`
- terminal failed가 있으면
  - `채널 장애 또는 영구 실패 원인 확인 필요`

## 관련 문서

1. [ops-baseline-runbook.md](./ops-baseline-runbook.md)
2. [notification-channel-expansion-plan.md](./notification-channel-expansion-plan.md)
3. [../current-state.md](../current-state.md)
