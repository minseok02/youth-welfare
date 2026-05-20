# recommendation same-profile origin differential audit runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

관련 문서:

- [recommendation-review-gate-blocker-audit-runbook.md](./recommendation-review-gate-blocker-audit-runbook.md)
- [recommendation-real-user-cohort-library-manifest.md](./recommendation-real-user-cohort-library-manifest.md)
- [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)

현재 단계 해석:

- local current blocker는 더 이상 `REAL_USER` traffic/cohort 부족이 아닙니다.
- live readiness는 이미 `READY_REAL_USER_TRAFFIC / READY_REAL_USER_COHORT` 이고, 이 문서는 **같은 프로필인데 `EXAMPLE_SMOKE` 와 `REAL_USER` 에서 왜 target service path가 갈리는지** 를 읽는 보조 audit 입니다.

## 목적

이 문서는 아래 질문을 한 번에 확인합니다.

- exact same profile에서 `EXAMPLE_SMOKE` 와 `REAL_USER` 의 target service path가 실제로 갈리는가
- target service `2622(청년월세 지원사업)` 가 example 쪽에서는 몇 위까지 자주 보이고, real-user 쪽에서는 왜 안 보이는가
- 다음 단계가 “표본 더 만들기”인지, “origin/flow differential 추적”인지

## 실행

기본 exact profile:

```bash
bash deploy/smoke/run-local-recommendation-same-profile-origin-differential-audit.sh
```

필요하면 profile/target service를 바꿔 재실행:

```bash
PROFILE_SIDO='인천광역시' \
PROFILE_SGG='중구' \
PROFILE_INCOME_LEVEL='5' \
PROFILE_EMPLOYMENT_STATUS='미취업' \
PROFILE_HOUSEHOLD_TYPE='1인 가구' \
TARGET_SERVICE_ID='2622' \
bash deploy/smoke/run-local-recommendation-same-profile-origin-differential-audit.sh
```

artifact:

- `tmp/recommendation-same-profile-origin-differential-audit/latest-same-profile-origin-differential-summary.txt`
- `tmp/recommendation-same-profile-origin-differential-audit/latest-top1-distribution.tsv`
- `tmp/recommendation-same-profile-origin-differential-audit/latest-target-rank-distribution.tsv`

## 먼저 볼 값

- `exact_profile_total_users`
- `exact_profile_example_users`
- `exact_profile_real_user_users`
- `example_smoke_top1_leader_title`
- `example_smoke_target_top1_count`
- `example_smoke_target_top3_count`
- `example_smoke_target_top10_count`
- `real_user_top1_leader_title`
- `real_user_target_top1_count`
- `real_user_target_top10_count`
- `blocker_class`
- `operator_next_step`

## 해석 규칙

### 1. `blocker_class=SAME_PROFILE_EXAMPLE_REAL_USER_DIFFERENTIAL`

- current local 기본 해석입니다.
- same profile 안에서도 example 쪽은 target service path가 강하게 보이고, real-user 쪽은 아예 안 보입니다.
- 이 경우 다음 질문은 “real-user를 더 만들까”가 아니라 `account_origin`, signup flow, latest batch composition 차이가 recommendation path를 어떻게 바꾸는가 입니다.

### 2. `blocker_class=SAME_PROFILE_SHARED_PATH`

- example 와 real-user 둘 다 target service path가 존재하는 상태입니다.
- 그때부터는 단순 path 존재 여부보다 rank/score 차이를 비교하는 쪽이 맞습니다.

### 3. `blocker_class=TARGET_SERVICE_ABSENT_FOR_ALL_ORIGINS`

- target service나 profile 선택이 틀렸을 가능성이 큽니다.
- 이 경우는 현재 blocker를 읽는 문서가 아니라 profile/target service selection 자체를 다시 봐야 합니다.

## 현재 local truth

- exact profile: `인천광역시 / 중구 / income=5 / 미취업 / 1인 가구`
- target service: `2622(청년월세 지원사업)`
- exact-profile latest batch user 수:
  - `EXAMPLE_SMOKE=454`
  - `REAL_USER=14`
  - `LOCAL_REAL_NON_EXAMPLE_SEED=3`
  - `BOUNDED_LOCAL=1`
- example 쪽:
  - `top1_leader=청년월세 지원사업`
  - `top1_share_pct=59.91`
  - `target_top1_count=272`
  - `target_top3_count=434`
  - `target_top10_count=442`
- real-user 쪽:
  - `top1_leader=드림나래(인천청년 면접복장 지원)`
  - `top1_share_pct=42.86`
  - `target_top1/top3/top5/top10/any-rank=0`

즉 현재 local truth는 **same-profile example vs real-user differential** 이고, 다음 기술적 단계는 random 표본 확대보다 recommendation path 차이를 직접 추적하는 쪽입니다.
