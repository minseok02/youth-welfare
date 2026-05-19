# recommendation review-gate blocker audit runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

관련 문서:

- [recommendation-pr-review-brief.md](./recommendation-pr-review-brief.md)
- [recommendation-pr-draft-exit-checklist.md](./recommendation-pr-draft-exit-checklist.md)
- [recommendation-post-merge-followup-checklist.md](./recommendation-post-merge-followup-checklist.md)
- [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)

현재 단계 해석:

- local current blocker는 `WAIT_FOR_REAL_USER_TRAFFIC` 자체가 아니라, live readiness가 열린 뒤에도 mixed latest batch review gate가 `DEFERRED_NON_REAL_LEADER_SIGNAL` 로 남는지 확인하는 단계입니다.
- latest artifact reading은 계속 `VOLATILE_ONLY_DRIFT` 이고, 이 문서는 reopen 결정문이 아니라 **mixed batch leader blocker를 읽는 보조 audit** 입니다.

## 목적

이 문서는 아래 질문을 한 번에 확인합니다.

- mixed latest batch top1 leader가 아직 non-real 중심인가
- `REAL_USER` only cohort에서는 top1이 실제로 분산되는가
- review gate blocker가 traffic 부족인지, mixed batch leader dominance 인지

## 실행

```bash
bash deploy/smoke/run-local-recommendation-review-gate-blocker-audit.sh
```

artifact:

- `tmp/recommendation-review-gate-blocker-audit/latest-review-gate-blocker-summary.txt`

## 먼저 볼 값

- `mixed_latest_batch_users`
- `mixed_example_users`
- `mixed_real_user_users`
- `mixed_top1_leader_title`
- `mixed_top1_leader_share_pct`
- `mixed_top1_leader_real_user_users`
- `real_user_mixed_leader_top1_count`
- `real_user_mixed_leader_top3_count`
- `real_user_mixed_leader_top5_count`
- `real_user_mixed_leader_top10_count`
- `mixed_concentration_readiness`
- `real_user_top1_leader_title`
- `real_user_top1_leader_share_pct`
- `real_user_concentration_readiness`
- `blocker_class`
- `operator_next_step`

## 해석 규칙

### 1. `blocker_class=MIXED_BATCH_NON_REAL_DOMINANCE`

- current local 기본 해석입니다.
- `REAL_USER` traffic/cohort는 충분하지만, mixed latest batch top1 leader는 아직 example/local seed가 주도합니다.
- 이 경우 바로 제품 reopen으로 가지 말고 mixed leader transition을 더 관찰합니다.

### 1-1. `blocker_class=MIXED_BATCH_NON_REAL_DOMINANCE_WITH_NO_REAL_USER_PATH`

- current local 30-user 샘플의 더 정확한 해석입니다.
- mixed latest batch top1 leader 서비스가 real-user 쪽 top10 안에도 한 번도 안 들어온 상태입니다.
- 현재 local 값:
  - `real_user_mixed_leader_top1_count=0`
  - `real_user_mixed_leader_top3_count=0`
  - `real_user_mixed_leader_top5_count=0`
  - `real_user_mixed_leader_top10_count=0`
- 즉 지금은 단순히 “real-user top1에 아직 안 붙었다”보다, **현재 real-user 샘플 구성으로는 mixed leader 전이 경로가 안 보이는 상태** 입니다.

### 2. `mixed_top1_leader_real_user_users=0` 이고 `real_user_concentration_readiness=NO_PRIORITY_DOMINANT`

- real-user 쪽 자체는 충분히 분산됐는데, mixed batch에서는 example 비중이 너무 커서 review gate가 안 열린다는 뜻입니다.
- current local 30-user 샘플이 여기에 해당합니다.
- 여기에 `real_user_mixed_leader_top10_count=0` 까지 붙으면, mixed leader 서비스 자체가 real-user 후보 상단에 없다는 뜻이므로 targeted housing-like sample이 없으면 gate 전이가 계속 늦어질 수 있습니다.

### 3. `mixed_top1_leader_real_user_users>0`

- real-user leader signal이 mixed batch top1에도 올라오기 시작한 상태입니다.
- 그때부터 readiness/runbook을 다시 같이 보고 review gate 전이 여부를 확인합니다.

## 현재 local truth

- mixed latest batch:
  - `latest_batch_users=498`
  - `example_users=464`
  - `real_user_users=30`
  - `top1_leader=청년월세 지원사업`
  - `top1_leader_share_pct=55.62`
  - `top1_leader_real_user_users=0`
  - `real_user_mixed_leader_top10_count=0`
- real-user only latest batch:
  - `latest_batch_users=30`
  - `top1_leader=드림나래(인천청년 면접복장 지원)`
  - `top1_leader_share_pct=10.00`
  - `concentration_readiness=NO_PRIORITY_DOMINANT`

즉 현재 review gate blocker는 **real-user 부족**보다 **mixed latest batch non-real dominance + 현재 real-user sample에 mixed leader transition path 부재** 로 읽는 편이 맞습니다.
