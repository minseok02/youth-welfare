# recommendation review-gate recent-window audit runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

관련 문서:

- [recommendation-pr-review-brief.md](./recommendation-pr-review-brief.md)
- [recommendation-pr-draft-exit-checklist.md](./recommendation-pr-draft-exit-checklist.md)
- [recommendation-post-merge-followup-checklist.md](./recommendation-post-merge-followup-checklist.md)
- [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)
- [recommendation-review-gate-blocker-audit-runbook.md](./recommendation-review-gate-blocker-audit-runbook.md)
- [recommendation-review-gate-staleness-audit-runbook.md](./recommendation-review-gate-staleness-audit-runbook.md)

현재 단계 해석:

- full latest batch review gate는 historical example latest batch에 크게 끌리고 있습니다.
- 이 문서는 stale historical batch를 그대로 폐기하자는 뜻이 아니라, **recent 24h latest batch를 같이 봤을 때 운영 해석이 달라지는지** 확인하는 보조 audit 입니다.

## 목적

이 문서는 아래 질문을 한 번에 확인합니다.

- recent 24시간 latest batch만 보면 mixed leader가 여전히 `2622` 인지
- stale example latest batch를 빼면 review signal이 real-user 쪽으로 이미 이동했는지
- full latest batch gate와 recent-window 보조 gate를 함께 읽어야 하는지

## 실행

```bash
bash deploy/smoke/run-local-recommendation-review-gate-recent-window-audit.sh
```

artifact:

- `tmp/recommendation-review-gate-recent-window-audit/latest-review-gate-recent-window-summary.txt`
- `tmp/recommendation-review-gate-recent-window-audit/latest-recent-top1-origin-mix.tsv`

## 먼저 볼 값

- `recent_latest_batch_users`
- `recent_example_users`
- `recent_real_user_users`
- `recent_top1_leader_service_id`
- `recent_top1_leader_title`
- `recent_top1_leader_real_user_users`
- `recent_target_top1_users`
- `recent_top1_leader_origin_mix`
- `blocker_class`
- `operator_next_step`

## 해석 규칙

### 1. `blocker_class=RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE`

- current local truth입니다.
- recent 24시간 latest batch만 보면 `2622` 는 top1 leader가 아니고, target top1 users도 `0` 입니다.
- 이 경우 full latest batch gate를 그대로 제품 판단 기준으로 읽기보다, **recent-window 보조 gate를 같이 읽는 편** 이 맞습니다.

### 2. `blocker_class=RECENT_WINDOW_STILL_TARGET_DOMINANT`

- stale historical batch를 빼도 `2622` 지배가 유지되는 상태입니다.
- 이 경우에는 recent-window 보조 gate도 full latest batch와 같은 결론을 지지합니다.

### 3. `blocker_class=RECENT_WINDOW_INCONCLUSIVE`

- recent window 표본이 너무 얇거나 leader가 불안정한 상태입니다.
- 이 경우에는 staleness audit과 full latest batch gate를 같이 봅니다.

## 현재 local truth

- `recent_window_hours=24`
- `recent_latest_batch_users=83`
- `recent_example_users=3`
- `recent_real_user_users=80`
- `recent_top1_leader_service_id=3284`
- `recent_top1_leader_title=인천 청년도약기지(취업아카데미)`
- `recent_top1_leader_users=6`
- `recent_top1_leader_real_user_users=5`
- `recent_top1_leader_share_pct=7.23`
- `recent_target_top1_users=0`
- `recent_target_top1_real_user_users=0`
- `recent_top1_leader_origin_mix=EXAMPLE_SMOKE:1,REAL_USER:5`
- current classification:
  - `RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE`
  - `operator_next_step=USE_RECENT_WINDOW_AS_SUPPLEMENTAL_REVIEW_CONTEXT`

즉 current local 운영 해석은 **full latest batch gate는 stale example inertia를 보여 주고, recent-window gate는 current live signal이 이미 `2622` dominance에서 벗어났음을 보여 주는 상태** 로 읽는 편이 맞습니다.
