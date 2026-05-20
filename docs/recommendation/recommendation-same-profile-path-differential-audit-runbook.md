# recommendation same-profile path differential audit runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

관련 문서:

- [recommendation-pr-review-brief.md](./recommendation-pr-review-brief.md)
- [recommendation-pr-draft-exit-checklist.md](./recommendation-pr-draft-exit-checklist.md)
- [recommendation-post-merge-followup-checklist.md](./recommendation-post-merge-followup-checklist.md)
- [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)
- [recommendation-review-gate-blocker-audit-runbook.md](./recommendation-review-gate-blocker-audit-runbook.md)
- [recommendation-same-profile-origin-differential-audit-runbook.md](./recommendation-same-profile-origin-differential-audit-runbook.md)

현재 단계 해석:

- current local blocker는 더 이상 `REAL_USER` traffic/cohort 부족이 아닙니다.
- current question은 exact same profile에서 `EXAMPLE_SMOKE` 와 generic-domain `REAL_USER` 가 왜 다른 recommendation path를 타느냐 입니다.
- 이 문서는 reopen 결정보다 **same-profile saved path vs retrieval path differential** 을 읽는 보조 audit 입니다.

## 목적

이 문서는 아래 질문을 한 번에 확인합니다.

- exact same profile 대표 example user와 대표 real-user가 누구인지
- target service `2622(청년월세 지원사업)` 가 각 대표 user에서 어느 stage에 있는지
- divergence가 retrieval/filter/window/scoring/save 중 어디에서 시작되는지

## 실행

```bash
APP_BASE_URL='http://127.0.0.1:8082' \
ADMIN_EMAIL='<local admin email>' \
ADMIN_PASSWORD='<local admin password>' \
bash deploy/smoke/run-local-recommendation-same-profile-path-differential-audit.sh
```

artifact:

- `tmp/recommendation-same-profile-path-differential-audit/latest-same-profile-path-differential-summary.txt`
- `tmp/recommendation-same-profile-path-differential-audit/latest-focus-services-comparison.tsv`

## 먼저 볼 값

- `representative_example_user_key`
- `representative_real_user_key`
- `target_service_id`
- `example_target_in_latest_saved_batch`
- `real_user_target_in_latest_saved_batch`
- `example_target_drop_stage`
- `real_user_target_drop_stage`
- `differential_stage`
- `blocker_class`
- `operator_next_step`

## 해석 규칙

### 1. `differential_stage=SAVED_ONLY_EXAMPLE_VS_REAL_SQL_GAP`

- current local exact-profile truth입니다.
- example 대표 user에서는 target service가 retrieval/filter/window에는 안 보이지만 `latest saved batch` 에는 남아 있습니다.
- real-user 대표 user에서는 같은 target service가 `NOT_IN_SQL_RETRIEVAL` 입니다.
- 즉 지금 질문은 “real-user가 later filter에서 떨어지느냐”보다 **example 쪽 saved path와 real-user SQL retrieval path가 왜 다르냐** 입니다.

### 2. `differential_stage=BASE_RETRIEVAL_DIFFERENTIAL` 또는 `LATEST_RETRIEVAL_DIFFERENTIAL`

- target service가 retrieval 층에서부터 갈리는 상태입니다.
- 이 경우 profile/origin/seed contract 차이가 retrieval SQL이나 lane 구성에 직접 영향을 준다고 읽습니다.

### 3. `differential_stage=LATEST_FILTER_DIFFERENTIAL` 또는 `WINDOW_RETENTION_DIFFERENTIAL`

- retrieval은 공유하지만 eligibility/window에서 갈리는 상태입니다.
- 이 경우 age/youth/primary audience/window retain 쪽을 먼저 확인합니다.

### 4. `differential_stage=SAVED_BATCH_SHARED_PATH`

- 둘 다 saved batch에는 들어오는 상태입니다.
- 이 경우 문제는 path 부재가 아니라 rank/score/AI delta 입니다.

## 현재 local truth

- profile:
  - `인천광역시 / 중구 / income=5 / 미취업 / 1인 가구`
- representative example:
  - `top1=2622 청년월세 지원사업`
  - `score=1.06400`
- representative real-user:
  - `top1=3688 드림나래(인천청년 면접복장 지원)`
  - `score=1.05500`
- target `2622`:
  - example:
    - `in_latest_saved_batch=true`
    - `drop_stage=PRESENT_IN_SAVED_BATCH`
    - `latest_saved_rank=1`
  - real-user:
    - `in_latest_saved_batch=false`
    - `drop_stage=NOT_IN_SQL_RETRIEVAL`
- current classification:
  - `differential_stage=SAVED_ONLY_EXAMPLE_VS_REAL_SQL_GAP`
  - `blocker_class=SAME_PROFILE_PATH_SAVED_ONLY_EXAMPLE_VS_REAL_SQL_GAP`
  - `operator_next_step=TRACE_SAVED_BATCH_ORIGIN_PATH_AND_SQL_RETRIEVAL_DIFF`

즉 현재 남은 recommendation blocker는 “주거형 real-user를 더 많이 넣으면 되나”가 아니라, **같은 프로필인데 왜 example은 saved batch에 `2622` 를 갖고 있고 generic-domain real-user는 SQL retrieval에도 못 들어가느냐** 를 추적하는 문제입니다.
