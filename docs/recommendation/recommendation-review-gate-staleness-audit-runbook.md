# recommendation review-gate staleness audit runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

관련 문서:

- [recommendation-pr-review-brief.md](./recommendation-pr-review-brief.md)
- [recommendation-pr-draft-exit-checklist.md](./recommendation-pr-draft-exit-checklist.md)
- [recommendation-post-merge-followup-checklist.md](./recommendation-post-merge-followup-checklist.md)
- [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)
- [recommendation-review-gate-blocker-audit-runbook.md](./recommendation-review-gate-blocker-audit-runbook.md)
- [recommendation-same-profile-fresh-saved-differential-audit-runbook.md](./recommendation-same-profile-fresh-saved-differential-audit-runbook.md)

현재 단계 해석:

- current local blocker는 단순 `REAL_USER` 부족이 아닙니다.
- current question은 mixed latest batch leader `2622` 가 **현재 flow** 때문에 유지되는지, 아니면 **historical example latest batch inertia** 때문에 유지되는지 입니다.
- 이 문서는 mixed leader를 만드는 origin별 latest batch recency를 읽는 보조 audit 입니다.

## 목적

이 문서는 아래 질문을 한 번에 확인합니다.

- origin별 latest batch가 최근 1시간/24시간 안에 얼마나 갱신됐는지
- target leader `2622` top1 users가 최근 배치인지 오래된 배치인지
- review gate blocker가 current flow보다 historical example batch 관성에 더 가까운지

## 실행

```bash
bash deploy/smoke/run-local-recommendation-review-gate-staleness-audit.sh
```

artifact:

- `tmp/recommendation-review-gate-staleness-audit/latest-review-gate-staleness-summary.txt`
- `tmp/recommendation-review-gate-staleness-audit/latest-stale-top1-users.tsv`

## 먼저 볼 값

- `example_smoke_latest_users_last_24h`
- `example_smoke_target_top1_users`
- `example_smoke_target_top1_last_24h`
- `example_smoke_target_oldest_top1`
- `example_smoke_target_newest_top1`
- `real_user_latest_users_last_24h`
- `real_user_target_top1_users`
- `blocker_class`
- `operator_next_step`

## 해석 규칙

### 1. `blocker_class=HISTORICAL_EXAMPLE_LATEST_BATCH_DOMINANCE`

- current local truth입니다.
- `2622` top1 example users는 많지만 recent batch에는 없습니다.
- 즉 mixed leader 해석이 current flow differential보다 **historical example latest batch 관성** 에 더 크게 끌리고 있다는 뜻입니다.

### 2. `blocker_class=NON_HISTORICAL_TARGET_LEADER_OR_SHARED_RECENCY`

- target leader가 recent batch에서도 유지되거나 origin 간 recency가 비슷한 상태입니다.
- 이 경우에는 stale persistence보다 current flow differential 추적이 우선입니다.

## 현재 local truth

- `2622` leader staleness:
  - `example_smoke_target_top1_users=272`
  - `example_smoke_target_top1_last_24h=0`
  - `example_smoke_target_oldest_top1=2026-05-13 13:39:31`
  - `example_smoke_target_newest_top1=2026-05-17 11:49:50`
- recent latest batches:
  - `example_smoke_latest_users=464`
  - `example_smoke_latest_users_last_24h=3`
  - `real_user_latest_users=80`
  - `real_user_latest_users_last_24h=80`
- current classification:
  - `HISTORICAL_EXAMPLE_LATEST_BATCH_DOMINANCE`
  - `operator_next_step=REFRESH_OR_EXPIRE_STALE_EXAMPLE_BATCHES_BEFORE_REVIEWING_LEADER`

즉 지금 mixed leader `2622` 는 “real-user가 아직 못 이긴다”보다, **오래된 example latest batch가 review gate 해석에 계속 남아 있는 상태** 로 읽는 편이 맞습니다.
