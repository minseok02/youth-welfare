# recommendation real-user baseline runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 로컬 synthetic/bounded seed가 아니라 **운영 `REAL_USER` 표본** 으로 recommendation review gate를 다시 읽을 때 따르는 절차를 정리합니다.

핵심은 두 가지입니다.

1. `REAL_USER` 표본이 실제로 생겼는지
2. 생긴 뒤에도 recommendation이 여전히 `2622` 같은 단일 서비스로 과하게 몰리는지

이 문서는 weight tuning 문서가 아니라, **운영에서 gate를 다시 열어도 되는지 판단하는 runbook** 입니다.

같이 보면 좋은 문서:

- [recommendation-pr-review-brief.md](./recommendation-pr-review-brief.md)
- [recommendation-pr-draft-exit-checklist.md](./recommendation-pr-draft-exit-checklist.md)
- [recommendation-post-merge-followup-checklist.md](./recommendation-post-merge-followup-checklist.md)
- [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)
- [recommendation-reopen-decision-runbook.md](./recommendation-reopen-decision-runbook.md)

현재 local 기준 기본 해석은:

- `WAIT_FOR_REAL_USER_TRAFFIC`
- `dashboard_real_user_gate=DEFERRED_NO_REAL_USER_TRAFFIC`
- `breakdown_real_user_cohort_gate=DEFERRED_NO_REAL_USER_COHORT`

즉 이 문서는 지금 당장 튜닝을 여는 문서가 아니라, **언제 reopen 판단으로 넘어갈 수 있는지**를 가르는 gate 문서에 가깝습니다.

## 전제

- 최신 서버 코드가 recommendation `realUserTrafficGateInWindow`, `recommendationReviewGate`, `latestBatchConcentration` 을 이미 노출하는 상태여야 합니다.
- admin dashboard summary/breakdowns API와 두 audit wrapper가 모두 실행 가능해야 합니다.
- 운영 admin 계정으로 admin smoke를 호출할 수 있어야 합니다.

관련 wrapper:

- `deploy/smoke/run-local-ctr-readiness-audit.sh`
- `deploy/smoke/run-local-recommendation-concentration-audit.sh`
- `deploy/smoke/run-local-admin-dashboard-smoke.sh`
- `deploy/smoke/run-local-admin-recommendation-breakdowns-smoke.sh`
- `deploy/smoke/run-local-real-user-readiness-check.sh`
- `deploy/smoke/run-local-recommendation-ai-zero-reason-distribution-audit.sh`
- `deploy/smoke/run-local-real-user-exclusion-readiness-check.sh`

## 어떤 사용자를 `REAL_USER` 로 보나

현재 기준은 `users.account_origin = REAL_USER` 입니다.

즉 아래는 `REAL_USER` 로 읽지 않습니다.

- `EXAMPLE_SMOKE`
- `BOUNDED_LOCAL`
- `LOCAL_REAL_NON_EXAMPLE_SEED`

운영에서 `REAL_USER` 기준선을 열고 싶으면, 실제 운영 사용자 계정이 recommendation log를 남겨야 합니다.

## 최소 표본

현재 gate 기준 최소 조건은 아래입니다.

1. summary window 안에 `REAL_USER logs > 0`
2. summary window 안에 `REAL_USER users >= 3`
3. summary window 안에 `REAL_USER clicked users >= 3`
4. latest recommendation batch 안에 `REAL_USER users >= 3`

즉 한두 명의 수동 테스트 계정으로는 reopen 근거가 되지 않습니다.

## 운영 수집 절차

가장 단순한 절차는 아래입니다.

1. 실제 운영 사용자 `3명 이상`이 정상 로그인
2. 각 사용자가 `/api/recommendations` 또는 앱 추천 화면 진입
3. 각 사용자가 추천 상세를 최소 `1회` 클릭
4. 가능하면 서로 다른 프로필/우선순위/지역 사용자를 섞음
5. 그 뒤 read-only readiness check 실행

주의:

- `@example.com`, `.local`, `.test`, `.invalid`, `cohortseed.app` 같은 도메인으로 만든 계정은 운영 기준선에 쓰지 않습니다.
- local bounded drill은 gate 전이 로직 확인용이고, 운영 baseline 대체물이 아닙니다.

## 실행

운영 read-only 확인은 아래 wrapper를 우선 씁니다.

```bash
ADMIN_EMAIL='<server admin email>' \
ADMIN_PASSWORD='<server admin password>' \
APP_BASE_URL='http://127.0.0.1:8082' \
deploy/smoke/run-local-real-user-readiness-check.sh
```

gate가 실제로 열렸는지까지 강하게 확인하려면:

```bash
ADMIN_EMAIL='<server admin email>' \
ADMIN_PASSWORD='<server admin password>' \
APP_BASE_URL='http://127.0.0.1:8082' \
REQUIRE_READY=true \
deploy/smoke/run-local-real-user-readiness-check.sh
```

이 wrapper는 아래 네 경로를 순서대로 읽습니다.

1. `USER_COHORT=real_user` CTR audit
2. `USER_COHORT=real_user` concentration audit
3. admin dashboard summary smoke
4. admin dashboard recommendation-breakdowns smoke

readiness와 zero-AI bucket 분포를 한 번에 보려면 아래 wrapper를 씁니다.

```bash
ADMIN_EMAIL='<server admin email>' \
ADMIN_PASSWORD='<server admin password>' \
APP_BASE_URL='http://127.0.0.1:8082' \
bash deploy/smoke/run-local-real-user-exclusion-readiness-check.sh
```

## 읽는 법

중요한 출력은 아래입니다.

- `ctr_real_user_scope_users`
- `ctr_real_user_clicked_users`
- `ctr_real_user_readiness`
- `concentration_real_user_users`
- `concentration_real_user_cohort_gate`
- `dashboard_real_user_gate`
- `dashboard_review_gate`
- `dashboard_top1_signal_summary`
- `breakdown_real_user_gate`
- `breakdown_review_gate`
- `breakdown_top1_signal_summary`

### 아직 reopen 금지

아래 상태면 아직 weight tuning을 열지 않습니다.

- `dashboard_real_user_gate=DEFERRED_NO_REAL_USER_TRAFFIC`
- `dashboard_real_user_gate=DEFERRED_REAL_USER_SAMPLE_THIN`
- `dashboard_real_user_gate=DEFERRED_REAL_USER_CLICK_SAMPLE_THIN`
- `concentration_real_user_cohort_gate=DEFERRED_EMPTY_REAL_USER_COHORT`
- `breakdown_real_user_cohort_gate=DEFERRED_NO_REAL_USER_COHORT`

### gate는 열렸지만 여전히 집중 심함

예:

- `dashboard_real_user_gate=READY_REAL_USER_TRAFFIC`
- `breakdown_real_user_cohort_gate=READY_REAL_USER_COHORT`
- `dashboard_review_gate=READY_CONCENTRATED_TOP1_REVIEW`
- `dashboard_top1_signal_summary=MIXED_REAL_USER_LEADER` 또는 `REAL_USER_ONLY_LEADER`

이 상태면 **reopen은 가능** 하지만, 다음 액션은 weight tuning보다 먼저
`top1 집중`, `fallback 무반응`, `diversity/balancing` 쪽을 같이 보는 편이 맞습니다.

### reopen 가능한 상태

최소한 아래는 만족해야 합니다.

- `dashboard_real_user_gate=READY_REAL_USER_TRAFFIC`
- `breakdown_real_user_gate=READY_REAL_USER_TRAFFIC`
- `breakdown_real_user_cohort_gate=READY_REAL_USER_COHORT`

그 뒤 `recommendationReviewGate` 해석으로 다음 분기를 봅니다.

- `READY_CONCENTRATED_TOP1_REVIEW`
- `READY_NO_PRIORITY_DOMINANT_REVIEW`
- `READY_BALANCED_LOGIC_REVIEW`

gate가 열린 뒤 zero-AI latest batch 패턴까지 더 보려면 아래를 추가로 실행합니다.

```bash
USER_COHORT=real_user \
TOP_N=20 \
bash deploy/smoke/run-local-recommendation-ai-zero-reason-distribution-audit.sh
```

여기서 `zero_ai_reason_buckets` 가 계속 `INCOME_MISMATCH`, `STUDENT_AUDIENCE_MISMATCH` 중심이면 현재 product exclusion이 운영 latest batch에도 반복된다는 뜻으로 읽습니다.

## 실행 후 남길 최소 기록

- 실행 시각
- `summary_window_days`
- `ctr_real_user_scope_users`
- `ctr_real_user_clicked_users`
- `dashboard_real_user_gate`
- `dashboard_review_gate`
- `concentration_real_user_cohort_gate`
- `breakdown_real_user_cohort_gate`
- `dashboard_top1_signal_summary`
- `breakdown_top1_signal_summary`
- 다음 액션

## 요약

1. 운영 `REAL_USER` 기준선은 `users.account_origin=REAL_USER` 로그로만 읽습니다.
2. 최소 `3명` 이상 사용자와 `3명` 이상 clicked user가 필요합니다.
3. read-only 확인은 `run-local-real-user-readiness-check.sh` 하나로 묶습니다.
4. gate가 열려도 `READY_CONCENTRATED_TOP1_REVIEW` 면 바로 weight tuning보다 집중/분산 해석을 먼저 봅니다.
5. gate가 아직 닫혀 있으면 current 기본 해석은 계속 `WAIT_FOR_REAL_USER_TRAFFIC` 입니다.
