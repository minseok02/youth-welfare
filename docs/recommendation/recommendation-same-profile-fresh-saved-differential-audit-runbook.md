# recommendation same-profile fresh-saved differential audit runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

관련 문서:

- [recommendation-pr-review-brief.md](./recommendation-pr-review-brief.md)
- [recommendation-pr-draft-exit-checklist.md](./recommendation-pr-draft-exit-checklist.md)
- [recommendation-post-merge-followup-checklist.md](./recommendation-post-merge-followup-checklist.md)
- [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)
- [recommendation-same-profile-origin-differential-audit-runbook.md](./recommendation-same-profile-origin-differential-audit-runbook.md)
- [recommendation-same-profile-path-differential-audit-runbook.md](./recommendation-same-profile-path-differential-audit-runbook.md)

현재 단계 해석:

- current local blocker는 same-profile differential의 존재 자체를 넘어서, 그 차이가 stale saved batch인지 current flow 차이인지 가르는 단계입니다.
- 이 문서는 exact same profile 대표 example/real-user에 `personal=true` fresh refresh를 직접 태워 **saved batch path가 refresh 뒤에도 남는지** 확인하는 보조 audit 입니다.

## 목적

이 문서는 아래 질문을 한 번에 확인합니다.

- example 대표 user의 `2622` 가 current latest saved batch에만 남는 stale path인지
- real-user 대표 user도 refresh 전후로 계속 `NOT_IN_SQL_RETRIEVAL` 인지
- current flow 차이보다 old saved batch persistence가 더 큰 원인인지

## 실행

```bash
APP_BASE_URL='http://127.0.0.1:8082' \
ADMIN_EMAIL='<local admin email>' \
ADMIN_PASSWORD='<local admin password>' \
bash deploy/smoke/run-local-recommendation-same-profile-fresh-saved-differential-audit.sh
```

artifact:

- `tmp/recommendation-same-profile-fresh-saved-differential-audit/latest-same-profile-fresh-saved-differential-summary.txt`
- `tmp/recommendation-same-profile-fresh-saved-differential-audit/latest-target-before-after.tsv`

## 먼저 볼 값

- `example_before_saved`
- `example_before_drop_stage`
- `example_after_saved`
- `example_after_drop_stage`
- `real_user_before_saved`
- `real_user_after_saved`
- `real_user_after_drop_stage`
- `classification`
- `operator_next_step`

## 해석 규칙

### 1. `classification=STALE_EXAMPLE_SAVED_BATCH_PATH`

- current local truth입니다.
- example 대표 user는 refresh 전에는 `2622` 를 `saved batch` 에 갖고 있었지만, fresh refresh 뒤에는 `NOT_IN_SQL_RETRIEVAL` 로 내려갑니다.
- real-user 대표 user는 refresh 전후 모두 `NOT_IN_SQL_RETRIEVAL` 입니다.
- 즉 same-profile 차이의 1차 원인은 current real-user flow가 `2622` 를 특별히 막아서가 아니라, **example 쪽 old saved batch path가 refresh 전까지 남아 있던 상태** 라고 읽는 편이 맞습니다.

### 2. `classification=CURRENT_FLOW_ORIGIN_DIFFERENTIAL`

- example 쪽은 refresh 뒤에도 `2622` 가 남고, real-user는 계속 빠지는 상태입니다.
- 이 경우에만 current flow/account_origin differential을 더 직접 추적합니다.

### 3. `classification=FRESH_FLOW_SHARED_ABSENCE`

- 둘 다 fresh refresh 뒤 `2622` 가 사라집니다.
- 이 경우 mixed latest batch blocker는 current flow보다는 **historical saved batch / stale persisted state** 쪽을 먼저 의심하는 편이 맞습니다.

## 현재 local truth

- target:
  - `2622 청년월세 지원사업`
- representative example:
  - before:
    - `saved=true`
    - `drop_stage=PRESENT_IN_SAVED_BATCH`
    - `saved_rank=1`
  - after fresh refresh:
    - `saved=false`
    - `drop_stage=NOT_IN_SQL_RETRIEVAL`
- representative real-user:
  - before:
    - `saved=false`
    - `drop_stage=NOT_IN_SQL_RETRIEVAL`
  - after fresh refresh:
    - `saved=false`
    - `drop_stage=NOT_IN_SQL_RETRIEVAL`
- current classification:
  - `STALE_EXAMPLE_SAVED_BATCH_PATH`
  - `operator_next_step=TRACE_WHY_EXAMPLE_SAVED_BATCH_PERSISTED_BEFORE_REFRESH`

즉 현재 남은 recommendation blocker는 “same-profile current flow가 real-user만 막는다”보다, **example saved batch가 refresh 전까지 stale하게 남아 mixed leader 해석을 왜곡하고 있었는지** 를 추적하는 문제에 더 가깝습니다.
