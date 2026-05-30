# Frontend Observation Runbook

문서군 진입점: [frontend-qa-docs-index.md](./frontend-qa-docs-index.md)

## 목적

daily operator가 프런트 lint/build/Playwright baseline을 raw stdout 대신 compact artifact로 다시 읽게 합니다.

entrypoint는 아래 wrapper입니다.

- `bash deploy/smoke/run-local-frontend-observation-suite.sh`
- 운영 서버:
  - `FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' bash deploy/smoke/run-local-frontend-observation-suite.sh`

## latest artifact

- `tmp/frontend-observation/latest-frontend-observation-summary.txt`
- `tmp/frontend-observation/latest-frontend-observation-note.md`
- `tmp/frontend-observation/latest-frontend-observation.json`

`KEEP_ARTIFACTS=false` 기본값에서도 stable snapshot은 남습니다.

## summary/json에서 먼저 볼 값

- `decision_class`
- `enabled_smoke_steps`
- `suite_duration_ms`
- `frontend_e2e_mode`
- `frontend_lint_duration_ms`
- `frontend_build_duration_ms`
- `frontend_e2e_duration_ms`
- `next_action`

## 현재 해석

### `decision_class=BASELINE_HEALTHY`

- lint/build/browser smoke baseline이 현재 기준선을 유지한다는 뜻입니다.
- UI flow를 다시 열기보다 current-state/checklist 기준을 그대로 유지합니다.

## 다음 액션

- `next_action=docs/frontend/frontend-qa-current-state.md` 이면 현재 브라우저 경계를 다시 읽는 쪽이 우선입니다.
- 실제 수동 브라우저 순서를 다시 따라가야 하면 [frontend-qa-checklist.md](./frontend-qa-checklist.md) 로 내려갑니다.
