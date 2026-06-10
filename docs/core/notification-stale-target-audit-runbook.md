# notification stale target audit runbook

## 목적

이 문서는 `2주 이상 unread` 알림이 특정 deeplink/정책 target에 몰리는지 확인하는 절차를 고정합니다.

총량만으로는 backlog가 왜 생기는지 모르기 때문에,

- deadline reminder가 특정 정책 한 건에 몰리는지
- digest가 특정 진입 링크로 반복되는지

를 같이 봅니다.

## 실행

로컬:

```bash
bash deploy/smoke/run-local-notification-stale-target-audit.sh
```

server/RDS:

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' \
bash deploy/smoke/run-local-notification-stale-target-audit.sh
```

## 현재 읽는 기준

- `STALE_DEADLINE_TARGET_CLUSTER_REVIEW`
  - stale unread가 특정 deadline reminder target에 몰려 있음
- `STALE_DIGEST_TARGET_CLUSTER_REVIEW`
  - stale unread가 digest target에 몰려 있음
- `NO_STALE_TARGETS`
  - 2주 이상 unread target cluster 없음

## 운영 해석

- deadline target cluster가 크면
  - 같은 정책에 대한 오래된 reminder가 방치된 상태로 본다
  - 우선 해당 정책 reminder sample을 hide/유지 기준으로 확인한다
- digest target cluster가 크면
  - digest cadence 또는 deeplink 기대 행동을 먼저 본다

## stale deadline triage 기준

### 1. 정책이 이미 사실상 끝난 경우

- reminder target 정책의 `apply_end_date` 가 이미 지났거나
- status/date sync 관점에서 더 이상 사용자-facing ACTIVE 후보가 아닌 경우

이 경우는 오래된 reminder를 backlog tail로 보고 `hide 후보`로 먼저 본다.

운영 메모 예시:

- `정책 마감 후 남은 stale reminder, hide 후보`
- `status/date tail 확인 후 reminder backlog 정리`

### 2. 정책은 아직 ACTIVE인데 reminder가 2주 이상 unread 로 남는 경우

- 같은 `deeplink_url` / 같은 policy target 에
- 여러 사용자 unread 가 2주 이상 남아 있으면
- 전송 실패라기보다 **reminder UX 가치가 낮거나 반복성이 높은 상태**로 읽는다.

이 경우는 채널 장애가 아니라 아래를 먼저 본다.

- reminder 발송 시점이 너무 이른지
- 같은 정책이 반복적으로 reminder cluster를 만드는지
- 사용자가 bookmark를 유지한 채 알림만 오래 방치하는 구조인지

운영 메모 예시:

- `ACTIVE 정책 stale reminder cluster, cadence/가치 재검토 필요`
- `동일 정책 reminder가 여러 사용자에게 장기 unread`

### 3. digest가 stale cluster를 만드는 경우

- digest는 특정 정책 1건보다 recommendation landing 자체의 문제일 수 있다.
- 이 경우는 reminder보다 `digest cadence / landing 기대 행동` review가 먼저다.

운영 메모 예시:

- `stale digest cluster, digest cadence 확인 필요`
- `landing action 대비 unread 장기 잔존`

## hide-stale 운영 경로

장기 unread cluster를 실제로 backlog tail에서 정리할 때는 아래 admin 경로를 쓴다.

- `POST /api/admin/dashboard/notification-backlog/hide-stale`

입력:

- `kind`
- `title`
- `deeplinkUrl`
- `olderThanDays`

의도:

- 같은 kind/title/deeplink target 에 몰린 오래된 unread를
- 사용자가 아직 직접 읽지 않았더라도
- 운영 backlog tail 기준으로 `HIDDEN` 처리하는 bounded maintenance 경로다.

주의:

- broad unread 전체를 한 번에 숨기지 않는다.
- stale target audit로 cluster를 먼저 좁힌 뒤, title/deeplink 단위로만 쓴다.

## 현재 local sample 해석

첫 stale triage target은 아래였다.

- `DEADLINE_REMINDER`
- `북마크한 정책 마감이 임박했어요`
- `deeplink=/policies/2622`
- `5 rows / 5 users`

이 cluster는 `hide-stale` 로 실제 정리했고, 현재 latest local 결과는:

- `stale_14d_total=0`
- `deadline_groups=0`
- `digest_groups=0`
- `decision_class=NO_STALE_TARGETS`

즉 `2주 이상 unread target cluster` 는 더 이상 남아 있지 않다.

현재 server/RDS 기준 남은 unread backlog는 broad stale cluster가 아니라:

- `unread_total=24`
- `unread_digest=24`
- `unread_deadline=0`
- `stale_unread_7d=10`
- `stale_unread_14d=0`

수준의 `7일 초과 recommendation digest tail` 로 읽는 편이 맞다.

2026-06-10 최신 server/RDS target audit 기준:

- `stale_14d_total=0`
- `stale_14d_groups=0`
- `deadline_groups=0`
- `digest_groups=0`
- `decision_class=NO_STALE_TARGETS`

즉 현재는 `hide-stale` 대상이 없다.

## 7일 초과 tail 운영 기준

`2주 이상` stale target cluster가 없는 상태에서 남는 unread는 보통

- `DEADLINE_REMINDER`
- 또는 `RECOMMENDATION_DIGEST`
- `7일 초과`
- target cluster까지는 아닌 tail

수준이다.

이 상태는 broad backlog가 아니라 아래 기준으로 읽는다.

1. `정책이 아직 ACTIVE`
   - hide보다 유지 검토를 먼저 한다.
   - reminder cadence가 너무 이른지, 사용자가 bookmark를 유지한 채 unread로 남긴 것인지 본다.
2. `정책이 종료/마감`
   - stale tail 정리 후보로 본다.
3. `같은 target으로 여러 사용자에게 다시 몰리기 시작함`
   - 14일을 기다리기보다 stale target cluster 재발 조짐으로 본다.
4. `RECOMMENDATION_DIGEST` 만 남은 경우
   - hide보다 digest cadence/landing 기대 행동 관찰을 먼저 한다.

운영 메모 예시:

- `7일 초과 deadline tail, ACTIVE 정책이라 유지`
- `7일 초과 unread tail, 종료 정책이라 hide 후보`
- `7일 초과 reminder가 동일 target에 다시 누적되는지 관찰`
- `7일 초과 digest tail, hide 대상 없음`

## 관련 문서

1. [notification-backlog-audit-runbook.md](./notification-backlog-audit-runbook.md)
2. [notification-backlog-sample-audit-runbook.md](./notification-backlog-sample-audit-runbook.md)
3. [../current-state.md](../current-state.md)
