# recommendation ai exclusion snapshot runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

관련 문서:

- [recommendation-pr-review-brief.md](./recommendation-pr-review-brief.md)
- [recommendation-pr-draft-exit-checklist.md](./recommendation-pr-draft-exit-checklist.md)
- [recommendation-post-merge-followup-checklist.md](./recommendation-post-merge-followup-checklist.md)
- [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)
- [recommendation-ai-exclusion-latest-overview-runbook.md](./recommendation-ai-exclusion-latest-overview-runbook.md)

## 현재 단계 해석

현재 local 기본값은 full latest batch review gate `DEFERRED_NON_REAL_LEADER_SIGNAL`, recent-window supplemental reading `RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE`, latest reading `VOLATILE_ONLY_DRIFT` 입니다.

즉 이 snapshot runbook은 recommendation을 지금 다시 튜닝하는 reopen entrypoint가 아니라, **현재 exclusion baseline을 날짜 붙은 summary로 재실행하고 비교 가능한 형태로 남기는 evidence capture helper** 로 읽는 것이 맞습니다.

## 목적

이 문서는 현재 local recommendation `AI exclusion` baseline을 **재실행 + 요약 캡처**까지 한 번에 남기는 wrapper 입니다.

`run-local-recommendation-ai-exclusion-suite.sh` 가 evidence 자체를 다시 태우는 entrypoint라면,
이 wrapper는 그 출력에서 지금 비교에 필요한 key만 다시 뽑아 `snapshot summary` 로 남깁니다.

## 기본 스크립트

```bash
bash deploy/smoke/run-local-recommendation-ai-exclusion-snapshot.sh
```

## 주요 입력

- `TARGET_USER_KEY`
- `USER_EMAIL`, `USER_PASSWORD` 또는 `USER_ACCESS_TOKEN`
- `ADMIN_EMAIL`, `ADMIN_PASSWORD`
- `TARGET_SERVICE_IDS_CSV`
- `TOP_REFRESH_LIMIT`
- `TOP_N`
- `SAMPLE_LIMIT`
- `BASELINE_COHORT`
- `TARGET_COHORT`
- `ARTIFACT_DIR`
- `KEEP_ARTIFACTS`

기본값:

- target family: `3288,3289,3290,5837`
- baseline cohort: `non_example`
- target cohort: `real_user`
- `KEEP_ARTIFACTS=true`

## 남는 산출물

artifact dir 아래 두 파일이 남습니다.

- `ai-exclusion-suite.out`
- `ai-exclusion-snapshot-summary.txt`

summary file에는 아래 key가 고정 포맷으로 저장됩니다.

- `target_service_ids_csv`
- `fresh_top_ai_zero_count`
- `ai_zero_count`
- `ai_zero_reason_buckets`
- `baseline_scope_users`
- `baseline_zero_ai_reason_buckets`
- `target_scope_users`
- `target_zero_ai_reason_buckets`
- `real_user_distribution_executed`
- `dashboard_real_user_gate`
- `breakdown_real_user_cohort_gate`

## 언제 쓰나

다음 중 하나일 때 먼저 씁니다.

1. 현재 local exclusion baseline을 날짜 붙은 summary로 남기고 싶을 때
2. 코드/프롬프트/문서 변경 전후 drift를 같은 key 포맷으로 비교하고 싶을 때
3. `REAL_USER` gate가 열리기 전과 열린 뒤를 같은 템플릿으로 비교하고 싶을 때

## 읽는 법

- `ai_zero_reason_buckets` 가 계속 `INCOME_MISMATCH`, `STUDENT_AUDIENCE_MISMATCH` 중심이면
  - current local truth는 broad signal 부족보다 product exclusion 유지 쪽입니다.
- `target_scope_users=0` 이고 `real_user_distribution_executed=false` 면
  - 아직 `REAL_USER` 운영 분포 비교를 열지 않습니다.
- `dashboard_real_user_gate=READY_REAL_USER_TRAFFIC` 와 `breakdown_real_user_cohort_gate=READY_REAL_USER_COHORT` 이 함께 열리면
  - 같은 wrapper를 다시 태워 `target_zero_ai_reason_buckets` drift를 baseline과 직접 비교합니다.
