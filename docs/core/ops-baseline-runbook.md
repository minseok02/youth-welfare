# ops baseline runbook

문서군 진입점: [system-docs-index.md](./system-docs-index.md)

## 목적

이 문서는 운영 서버에서 앱 상태를 건드리지 않고

- health
- admin dashboard summary
- admin collect failures
- admin recommendation breakdowns

를 한 번에 다시 확인하는 read-only baseline 경로를 고정합니다.

프론트 browser smoke는 이 문서의 범위가 아닙니다. 로컬 전체 baseline은 아래 순서를 기준으로 읽습니다.

1. `cd backend && ./gradlew test --no-daemon`
2. `cd frontend && npm run lint && npm run build && npm run test:e2e`
3. `bash deploy/smoke/run-local-ops-baseline-suite.sh`

운영 cutover 확인은 별도 wrapper를 사용합니다.

- `ENV_FILE=.env.production PUBLIC_BASE_URL='https://youthmoa.kr' bash deploy/smoke/run-prod-cutover-verification.sh`

## 기본 wrapper

```bash
bash deploy/smoke/run-local-ops-baseline-suite.sh
```

이 wrapper는 아래 3개 smoke를 순서대로 실행합니다.

1. `run-local-admin-dashboard-smoke.sh`
2. `run-local-admin-collect-failures-smoke.sh`
3. `run-local-admin-recommendation-breakdowns-smoke.sh`

## 언제 쓰나

아래 상황이면 이 runbook이 맞습니다.

1. 서버 재기동 직후 admin 운영 기준선 확인
2. collect/runtime governance 변경 후 회귀 확인
3. recommendation admin-only read surface 회귀 확인
4. “앱은 떠 있는데 운영 화면 contract가 깨지지 않았는지”를 빠르게 확인할 때

브라우저 복귀/query/session/admin 접근 회귀는 먼저 Playwright smoke로 보고, 이 wrapper는 그 다음 read-only runtime contract 확인에 사용합니다.

## 주요 입력

- `APP_BASE_URL`
- `ADMIN_EMAIL`
- `ADMIN_PASSWORD`
- `SUMMARY_WINDOW_DAYS`
- `TREND_WINDOW_DAYS_CSV`
- `COLLECT_LIMIT`
- `BREAKDOWN_LIMIT`
- `KEEP_ARTIFACTS=true`

예:

```bash
APP_BASE_URL='http://127.0.0.1:8082' \
ADMIN_EMAIL='<admin email>' \
ADMIN_PASSWORD='<admin password>' \
KEEP_ARTIFACTS=true \
bash deploy/smoke/run-local-ops-baseline-suite.sh
```

## 기대 결과

성공하면 suite summary에 아래가 남습니다.

- `app_base_url`
- `summary_window_days`
- `artifact_dir`
- `admin_dashboard_stdout`
- `admin_collect_failures_stdout`
- `admin_recommendation_breakdowns_stdout`

서버 기준 최신 확인(`2026-05-18`)에서는 suite가 실제로 끝까지 통과했고,

- `failed_jobs_in_window=0`
- `partial_success_jobs_in_window=0`
- `open_collect_circuits=0`
- `lane_count=10`

까지 한 번에 다시 확인됐습니다.

각 하위 smoke는 자체적으로 contract를 검증합니다.

### dashboard summary

- `generatedAt`
- collect/recommendation/search/notification 요약 필드
- trend window

### collect failures

- `failedJobsInWindow`
- `partialSuccessJobsInWindow`
- `collectSourceLanes`
- lane별 `latestRun`
- lane별 `configEntries`

### recommendation breakdowns

- `trafficMixInWindow`
- `latestBatchConcentration`
- `youthOfficialFacetGroups`
- `gov24FacetGroups`

## 해석

이 wrapper는 feature correctness보다 **운영 surface contract** 를 먼저 봅니다.

- health가 정상인지
- admin auth가 정상인지
- collect lane inventory/latestRun/config summary가 깨지지 않았는지
- recommendation breakdown/facet 카드 데이터가 정상인지

를 한 번에 다시 확인하는 용도입니다.

## 관련 문서

1. [current-state.md](../current-state.md)
2. [collect-ops.md](../collect/collect-ops.md)
3. [recommendation-operation-checklist.md](../recommendation/recommendation-operation-checklist.md)

## 요약

1. 이 wrapper는 read-only ops baseline suite입니다.
2. health + admin summary + collect failures + recommendation breakdowns를 묶습니다.
3. 서버 재기동 직후나 bounded 운영 변경 뒤 회귀 확인에 씁니다.
