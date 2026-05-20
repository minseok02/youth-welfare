# recommendation real-user exclusion readiness check runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 운영 `REAL_USER` gate readiness와 zero-AI reason bucket 분포를 **한 번에** 확인합니다.

질문은 이것입니다.

- `REAL_USER` gate가 열렸는가
- 열렸다면 latest batch top N의 zero-AI bucket이 무엇인가

같이 보면 좋은 문서:

- [recommendation-pr-review-brief.md](./recommendation-pr-review-brief.md)
- [recommendation-pr-draft-exit-checklist.md](./recommendation-pr-draft-exit-checklist.md)
- [recommendation-post-merge-followup-checklist.md](./recommendation-post-merge-followup-checklist.md)
- [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)

현재 local 기본 해석은:

- `dashboard_real_user_gate=READY_REAL_USER_TRAFFIC`
- `breakdown_real_user_cohort_gate=READY_REAL_USER_COHORT`
- full latest batch review gate:
  - `DEFERRED_NON_REAL_LEADER_SIGNAL`
- recent-window supplemental gate:
  - `RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE`

즉 이 runbook은 이제 “gate가 아직 안 열렸다”보다, **gate는 열렸지만 distribution과 review 해석을 어떤 순서로 볼지**를 가르는 runbook에 더 가깝습니다.

## 기본 스크립트

```bash
ADMIN_EMAIL='<server admin email>' \
ADMIN_PASSWORD='<server admin password>' \
APP_BASE_URL='http://127.0.0.1:8082' \
bash deploy/smoke/run-local-real-user-exclusion-readiness-check.sh
```

필요하면:

- `REQUIRE_READY=true`
- `TOP_N=20`
- `SAMPLE_LIMIT=10`

## 동작

이 wrapper는 순서대로 아래를 실행합니다.

1. `run-local-real-user-readiness-check.sh`
2. gate가 준비된 경우에만
   - `USER_COHORT=real_user run-local-recommendation-ai-zero-reason-distribution-audit.sh`

## 읽는 법

### 1. `real_user_distribution_executed=false`

이 경우는 아직 `REAL_USER` gate가 닫혀 있어 bucket 분포를 읽을 단계가 아닙니다.

이때 current 기본 해석은 readiness는 아직 deferred이고, closeout baseline 유지 / product reopen 아님 입니다.

### 2. `real_user_distribution_executed=true`

이 경우는 readiness와 latest batch zero-AI bucket을 같은 출력에서 바로 읽으면 됩니다.

이 다음 순서는:

- [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)
- 필요하면 [recommendation-reopen-decision-runbook.md](./recommendation-reopen-decision-runbook.md)
