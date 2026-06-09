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

1. `bash deploy/smoke/run-local-active-baseline-suite.sh`
   - 서버에서는 `APP_BASE_URL='http://127.0.0.1:8082' FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' bash deploy/smoke/run-local-active-baseline-suite.sh`
   - 이 모드는 배포 번들에서 불가능한 `@dev-only` admin forced-failure Playwright 2개를 자동 제외합니다.
2. 필요하면 하위 step만 개별 실행
   - `cd backend && ./gradlew test --no-daemon`
   - `cd frontend && npm run lint && npm run build && npm run test:e2e`
   - `bash deploy/smoke/run-local-ops-baseline-suite.sh`
   - `bash deploy/smoke/run-local-collect-legacy-repair-suite.sh`

운영 cutover 확인은 별도 wrapper를 사용합니다.

- `ENV_FILE=.env.production PUBLIC_BASE_URL='https://youthmoa.kr' bash deploy/smoke/run-prod-cutover-verification.sh`

daily operator가 raw child stdout 대신 compact handoff를 먼저 보려면 아래 wrapper를 먼저 씁니다.

```bash
bash deploy/smoke/run-local-ops-observation-suite.sh
```

프론트 baseline을 같이 읽을 때는 `frontend observation` summary의 `flow_families` 를 먼저 봅니다. 이 값은 공개/검색상세/로그인/보호/세션복구/개인유지/admin/추천챗 같은 큰 축을 요약해서, 어떤 runbook을 먼저 열어야 하는지 바로 알려줍니다.

운영 서버/RDS:

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-ops-observation-suite.sh
```

이 observation wrapper는 아래 두 가지도 같이 돌립니다.

- `user profile standard code coverage`
- `recommendation standard code observation`

즉 운영 handoff에서 아래를 같은 summary/json 에서 바로 읽습니다.

- `attention_feed.items`
- `attention_feed_item_count`
- `attention_feed_item_titles`
- `users_missing_all_standard_codes`
- `users_with_any_standard_code`
- `safe_reconcile_candidate_rows`
- `conflicting_value_gap_rows`
- `wrapper_promoted_alert_severity`
- `wrapper_promoted_alert_message`
- `housing_standard_code_effect_positive_rule_delta_rows`
- `welfare_standard_code_matrix_positive_rule_scenarios`
- `welfare_standard_code_matrix_max_rule_delta`
- `recommendation_standard_code_adoption_latest_batch_users_with_any_standard_code_share_pct`
- `recommendation_standard_code_adoption_latest_batch_users_missing_all_standard_codes`

`standard-code-backlog` attention item은 단순 미입력 잔량이면 `info` 로 읽고,
`safe_reconcile_candidate_rows > 0` 또는 `conflicting_value_gap_rows > 0` 일 때만 운영자가 바로 처리할 `warning` 으로 봅니다.

nightly server wrapper가 필요하면 아래를 씁니다.

```bash
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
FRONTEND_E2E_MODE=deployed-origin \
FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' \
bash deploy/smoke/run-nightly-standard-code-observation.sh
```

여러 nightly handoff를 cron 한 줄로 묶을 때는 아래 wrapper를 씁니다.

```bash
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
FRONTEND_E2E_MODE=deployed-origin \
FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' \
bash deploy/smoke/run-nightly-ops-handoff.sh
```

이 nightly handoff에는 이제 `policy data triage observation` 도 같이 들어갑니다. 즉 운영자는 policy 쪽에서 raw audit 3개를 따로 열기 전에 `duplicate-first인지, link-first인지` 를 nightly summary 한 줄에서 바로 읽을 수 있습니다.

관리자 대시보드 summary도 같은 경계를 노출합니다. 운영자는 `policy triage` 카드에서 `exact duplicate`, `mirror variant`, `급부형 링크 review` 수치와 함께 현재 처리 순서를 바로 읽을 수 있어, nightly artifact를 열기 전에도 backlog 우선순위를 빠르게 판단할 수 있습니다.

cron 등록과 cleanup은 [nightly-ops-handoff-cron-runbook.md](./nightly-ops-handoff-cron-runbook.md) 기준으로 맞춥니다.

직접 등록 대신 idempotent block install이 필요하면 아래를 사용합니다.

```bash
bash deploy/smoke/install-nightly-ops-handoff-cron.sh
```

## 기본 wrapper

```bash
bash deploy/smoke/run-local-ops-baseline-suite.sh
```

이 wrapper는 아래 3개 smoke를 순서대로 실행합니다.

1. `run-local-admin-dashboard-smoke.sh`
2. `run-local-admin-collect-failures-smoke.sh`
3. `run-local-admin-recommendation-breakdowns-smoke.sh`

collect governance를 compact하게 다시 읽고 싶으면 아래 wrapper를 추가로 쓴다.

```bash
bash deploy/smoke/run-local-collect-governance-observation-suite.sh
```

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
- notification 운영 품질 핵심 backlog
  - `unreadAlerts`
  - `staleUnread7d`
  - `staleUnread14d`
  - `retryableFailedNotifications`
  - `terminalFailedNotifications`
  - unread/failed triage는 [notification-backlog-audit-runbook.md](./notification-backlog-audit-runbook.md) 기준으로 다시 본다.
  - `staleUnread14d` 는 hide 후보로, `staleUnread7d` 는 deadline tail/cadence 재검토 대상으로 먼저 읽는다.
  - stale target cluster maintenance가 필요하면 `POST /api/admin/dashboard/notification-backlog/hide-stale` 로 title/deeplink 단위 hide를 수행한다.

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
