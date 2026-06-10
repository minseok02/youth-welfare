# notification backlog sample audit runbook

## 목적

이 문서는 장기 미열람 unread 알림이 실제로 어떤 제목/종류로 쌓이는지 sample 수준으로 다시 읽는 절차를 고정합니다.

총량만으로는 운영 판단이 어려워서,

- digest 알림이 반복되는지
- deadline reminder가 누적되는지
- 일부 사용자에게 unread가 몰리는지

를 같이 봅니다.

## 실행

로컬:

```bash
bash deploy/smoke/run-local-notification-backlog-sample-audit.sh
```

server/RDS:

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' \
bash deploy/smoke/run-local-notification-backlog-sample-audit.sh
```

## 현재 읽는 기준

- `STALE_UNREAD_14D_SAMPLE_REVIEW`
  - 2주 이상 unread sample이 남아 있는 상태
- `STALE_UNREAD_7D_SAMPLE_REVIEW`
  - 1주 이상 unread sample review가 필요한 상태
  - 현재 sample이 모두 `RECOMMENDATION_DIGEST` 이고 failed/14일 cluster가 없으면, 장애보다 digest cadence/landing 관찰 tail 로 읽는다.
- `RECENT_UNREAD_ONLY`
  - 장기 미열람은 없고 최근 unread만 남은 상태

## sample에서 먼저 볼 것

1. `DEADLINE_REMINDER`
   - 오래된 마감 알림이 unread로 계속 남는지
2. `RECOMMENDATION_DIGEST`
   - 같은 성격 digest가 반복되는지
3. `users_with_5plus_unread`
   - 특정 사용자에 backlog가 몰리는지

## 운영 해석

- `deadline_unread` 가 stale sample 상위면
  - reminder 주기/노출/숨김 흐름 점검이 우선
- `digest_unread` 가 stale sample 상위면
  - digest 빈도나 기대 행동이 맞는지 점검
  - 14일 초과 target cluster가 없으면 broad hide보다 cadence/landing 관찰을 우선한다.
- `system_unread` 가 stale로 남으면
  - 시스템성 알림 자체를 다시 봐야 한다

## 현재 server/RDS 기준

2026-06-10 최신 server/RDS sample audit 기준:

- `decision_class=STALE_UNREAD_7D_SAMPLE_REVIEW`
- `unread_total=24`
- `unique_users_with_unread=2`
- `digest_unread=24`
- `deadline_unread=0`
- `system_unread=0`
- `users_with_2plus_unread=2`
- `users_with_5plus_unread=2`
- `stale_unread_7d=10`
- `stale_unread_14d=0`

즉 현재 sample은 deadline reminder 정리나 시스템 알림 장애가 아니라, 소수 사용자에게 남은 recommendation digest unread tail 이다.

## 관련 문서

1. [notification-backlog-audit-runbook.md](./notification-backlog-audit-runbook.md)
2. [ops-baseline-runbook.md](./ops-baseline-runbook.md)
3. [../current-state.md](../current-state.md)
