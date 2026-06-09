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
  - 단, `terminal_failed_total=0`, `retryable_failed_due_now=0`, `stale_unread_14d=0` 이면 채널 장애나 즉시 hide 작업보다 digest/reminder cadence 관찰로 먼저 읽는다.

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
- unread가 모두 `RECOMMENDATION_DIGEST` 이고 14일 초과 target cluster가 없으면
  - `hide 후보보다 digest cadence/landing 기대 행동 관찰`
- retryable failed가 due 상태면
  - `retry runner / 채널 상태 확인 필요`
- terminal failed가 있으면
  - `채널 장애 또는 영구 실패 원인 확인 필요`

## 현재 server/RDS 기준

2026-06-09 최신 server/RDS audit 기준:

- `decision_class=STALE_UNREAD_ALERT_REVIEW_PRIORITY`
- `unread_total=24`
- `unread_digest=24`
- `unread_deadline=0`
- `unread_system=0`
- `stale_unread_7d=12`
- `stale_unread_14d=0`
- `retryable_failed_total=0`
- `retryable_failed_due_now=0`
- `terminal_failed_total=0`

즉 현재 알림 backlog는 전송 실패나 14일 이상 stale target cluster가 아니라 `7일 초과 recommendation digest unread tail` 로 읽는다.
이 상태에서는 `hide-stale` 을 바로 태우지 않고, sample/target audit로 digest cadence와 landing 기대 행동을 관찰한다.

## stale unread 7일/14일 해석

### 14일 초과

- 기본적으로 `hide-stale` 후보로 먼저 봅니다.
- 특히 같은 `title + deeplink` target cluster가 2주 이상 유지되면
  - broad unread 총량보다
  - stale target cluster maintenance 문제로 읽는 편이 맞습니다.

운영 메모 예시:

- `14일 초과 stale target cluster, hide 후보`
- `2주 이상 unread 유지, title/deeplink 단위 backlog 정리`

### 7일 초과 ~ 14일 미만

- 바로 hide하기보다 `deadline tail` 인지 먼저 봅니다.
- 특히 `DEADLINE_REMINDER` 는 정책이 아직 `ACTIVE` 인데도 7일 이상 unread가 남을 수 있으므로,
  - 잘못 발송된 알림으로 닫지 말고
  - cadence/가치 문제인지 먼저 본다.

우선순위:

1. 같은 정책 target에 여러 사용자가 몰렸는지
2. 정책이 이미 종료됐는지
3. digest가 아니라 deadline reminder인지

운영 메모 예시:

- `7일 초과 deadline tail, cadence 재검토 후보`
- `ACTIVE 정책 reminder unread tail, 즉시 hide보다 유지 검토`
- `7일 초과 unread이나 target cluster는 아님`

## 관련 문서

1. [ops-baseline-runbook.md](./ops-baseline-runbook.md)
2. [notification-channel-expansion-plan.md](./notification-channel-expansion-plan.md)
3. [../current-state.md](../current-state.md)
