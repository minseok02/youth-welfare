# Frontend Observation Runbook

문서군 진입점: [frontend-qa-docs-index.md](./frontend-qa-docs-index.md)

## 목적

daily operator가 프런트 lint/build/Playwright baseline을 raw stdout 대신 compact artifact로 다시 읽게 합니다.

entrypoint는 아래 wrapper입니다.

- `bash deploy/smoke/run-local-frontend-observation-suite.sh`
- 운영 서버:
  - `ENV_FILE=.env.runtime.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' bash deploy/smoke/run-local-frontend-observation-suite.sh`

`deployed-origin` 기본 경계:

- backend `APP_BASE_URL` / `HEALTH_URL` 기준으로 `bootstrap-playwright-smoke-data.sh` 를 먼저 실행해 fresh e2e user와 검색 fixture를 준비합니다.
- `ENV_FILE` 은 Playwright helper까지 그대로 전달하므로 reset-password helper도 production DB 접속값을 같은 env file에서 읽습니다.
- 기본값에서는 `@dev-only` 와 `@admin-required` 케이스를 제외합니다.
- API route mock을 전제로 UI state 전이를 고정하는 smoke는 `@dev-only` 로 표시해 deployed-origin 기본 경계에서 제외합니다. 예: 마이페이지 동의 철회 mock 테스트.
- 실제 admin credential이 있고 admin dashboard smoke까지 포함해 확인하려면 `RUN_FRONTEND_ADMIN_E2E=true` 를 명시합니다.
- `RUN_FRONTEND_ADMIN_E2E=true` 일 때는 `ADMIN_EMAIL`/`ADMIN_PASSWORD` 또는 `E2E_ADMIN_EMAIL`/`E2E_ADMIN_PASSWORD` 가 필요합니다. wrapper/bootstrap/Playwright helper는 더 이상 기본 `admin@example.com` / `password123!` 로 조용히 fallback 하지 않습니다.
- 기본 admin credential은 의도적인 local-only smoke에서만 `ALLOW_DEFAULT_ADMIN_CREDENTIALS=true` 로 열 수 있습니다.

`local-dev` 경계도 `RUN_FRONTEND_ADMIN_E2E=false` 이면 `@admin-required` 케이스를 명시 제외합니다. `ALLOW_DEFAULT_ADMIN_CREDENTIALS=true` 를 shell에 둔 상태라도 wrapper가 admin dashboard smoke를 조용히 포함하지 않게 막습니다. local-only admin dashboard를 확인할 때는 `RUN_FRONTEND_ADMIN_E2E=true ALLOW_DEFAULT_ADMIN_CREDENTIALS=true` 를 함께 명시합니다.

## latest artifact

- `tmp/frontend-observation/latest-frontend-observation-summary.txt`
- `tmp/frontend-observation/latest-frontend-observation-note.md`
- `tmp/frontend-observation/latest-frontend-observation.json`

`KEEP_ARTIFACTS=false` 기본값에서도 stable snapshot은 남습니다.

## summary/json에서 먼저 볼 값

- `decision_class`
- `flow_families`
- `enabled_smoke_steps`
- `suite_duration_ms`
- `frontend_e2e_mode`
- `frontend_lint_duration_ms`
- `frontend_build_duration_ms`
- `frontend_e2e_duration_ms`
- `run_frontend_admin_e2e`
- `playwright_grep`
- `playwright_grep_invert`
- `next_action`

## 현재 해석

### `decision_class=BASELINE_HEALTHY`

- lint/build/browser smoke baseline이 현재 기준선을 유지한다는 뜻입니다.
- UI flow를 다시 열기보다 current-state/checklist 기준을 그대로 유지합니다.

### `flow_families`

- summary/json/note에는 현재 프론트 smoke를 어떤 큰 축으로 읽어야 하는지가 같이 남습니다.
- 현재 기본 축:
  - `public-core`
  - `search-detail`
  - `authenticated-home`
  - `protected-routes`
  - `session-recovery`
  - `retention`
  - `account-lifecycle`
  - `admin-operator`
  - `recommendation-chat`
  - `help-surface`
- 각 축은 대응 runbook 경로와 함께 note/json에 들어갑니다.

## 다음 액션

- `next_action=docs/frontend/frontend-qa-current-state.md` 이면 현재 브라우저 경계를 다시 읽는 쪽이 우선입니다.
- 실제 수동 브라우저 순서를 다시 따라가야 하면 [frontend-qa-checklist.md](./frontend-qa-checklist.md) 로 내려갑니다.
- nightly/handoff에서 특정 축만 다시 보고 싶으면 해당 `flow_families` 에 대응하는 runbook부터 엽니다.
