# 현재 상태

이 문서는 긴 이력 저장소가 아니라, 지금 무엇을 먼저 읽고 무엇을 먼저 검증해야 하는지 빠르게 찾는 active 진입 문서입니다.

## 한 줄 요약

- 현재 active main track은 운영 인프라 확장보다 로컬 기능/구조 검증과 회귀 방지입니다.
- 최근 보안/운영 follow-up은 [core/security-hardening-current-state.md](./core/security-hardening-current-state.md) 를 먼저 봅니다.
- 실제 운영 전환 절차가 필요할 때만 [deployment.md](./deployment.md) 를 같이 봅니다.

## 지금 유지하는 active 기준선

- 보안: `Tomcat 10.1.55`, `pgjdbc 42.7.11`, `Bouncy Castle 1.84`, logout 후 older token까지 `401/A006`
- collect/runtime: 로컬 full collect, ops baseline, broad quality 재검사까지 다시 green
- recommendation/policy: 각 current-state 문서와 runbook을 기준으로 baseline 유지 단계
- 프론트: 기본 연동, lint, build, browser smoke까지 확인 완료

## 지금 먼저 할 일

1. 지금 작업 주제의 `*-docs-index.md` 와 `*-current-state.md` 를 먼저 확인합니다.
2. 필요한 smoke/test만 먼저 돌려 현재 기준선을 확인합니다.
3. 코드 수정 후 문서, 검증 결과, Git 정리를 같은 작업 단위로 마무리합니다.

## 지금 먼저 볼 문서

- 인증 문서군 진입점: [auth-docs-index.md](auth/auth-docs-index.md)
- 수집 문서군 진입점: [collect-docs-index.md](collect/collect-docs-index.md)
- 추천 문서군 진입점: [recommendation-docs-index.md](recommendation/recommendation-docs-index.md)
- 프론트 QA 문서군 진입점: [frontend-qa-docs-index.md](frontend/frontend-qa-docs-index.md)
- 정책 문서군 진입점: [policy-docs-index.md](policy/policy-docs-index.md)
- `Gov24` bounded lane closeout: [policy/policy-gov24-lane-closeout.md](policy/policy-gov24-lane-closeout.md)
- `Gov24` taxonomy validation smoke: `bash deploy/smoke/run-local-gov24-taxonomy-validation.sh`
- 성능 문서군 진입점: [performance-docs-index.md](performance/performance-docs-index.md)
- 성능 최적화 변경 로그: [performance-optimization-log.md](performance/performance-optimization-log.md)
- 공통 로컬 검증 문서군 진입점: [local-validation-docs-index.md](core/local-validation-docs-index.md)
- 서버 런타임 drift 체크리스트: [server-runtime-drift-checklist.md](core/server-runtime-drift-checklist.md)
- 보안/운영 hardening 현재 상태: [security-hardening-current-state.md](core/security-hardening-current-state.md)
- 운영 baseline wrapper: [ops-baseline-runbook.md](core/ops-baseline-runbook.md)
- 시스템 문서군 진입점: [system-docs-index.md](core/system-docs-index.md)
- OpenAI runtime 계약: [openai-runtime-contract.md](core/openai-runtime-contract.md)
- 히스토리 문서군 진입점: [history-docs-index.md](./history-docs-index.md)

## 작업 전 기본 검증 기준

- one-shot local active baseline: `bash deploy/smoke/run-local-active-baseline-suite.sh`
- one-shot server active baseline: `APP_BASE_URL='http://127.0.0.1:8082' FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' bash deploy/smoke/run-local-active-baseline-suite.sh`
  - 참고: `deployed-origin` 모드는 배포 번들에서 성립하지 않는 `@dev-only` admin forced-failure Playwright 2개를 자동 제외합니다.
  - latest artifact: `tmp/active-baseline-suite/latest-active-baseline-summary.txt`, `tmp/active-baseline-suite/latest-active-baseline-summary.json`
  - latest summary/json 에 `ops_attention_feed_*`, `ops_user_profile_standard_code_*`, `ops_recommendation_standard_code_*` 가 같이 포함됩니다.
  - `KEEP_ARTIFACTS=false` 기본값에서도 latest summary/json 과 `tmp/active-baseline-suite/latest/` snapshot은 남습니다.
- one-shot current priority suite: `bash deploy/smoke/run-local-current-priority-suite.sh`
- one-shot server current priority suite: `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' bash deploy/smoke/run-local-current-priority-suite.sh`
  - latest artifact: `tmp/current-priority-suite/latest-current-priority-summary.txt`, `tmp/current-priority-suite/latest-current-priority-summary.json`
  - latest summary/json 에 `active_baseline_attention_feed_*`, `active_baseline_user_profile_standard_code_*`, `recommendation_standard_code_*` 가 같이 포함됩니다.
  - `KEEP_ARTIFACTS=false` 기본값에서도 latest summary/json 과 `tmp/current-priority-suite/latest/` snapshot은 남습니다.
  - same-config `active_baseline` latest가 TTL 안에 있으면 재사용할 수 있고, summary/json 에 `active_baseline_reused=true` 로 남습니다.
- backend only: `cd backend && ./gradlew test --no-daemon`
- frontend only: `cd frontend && npm run lint && npm run build && npm run test:e2e`
- runtime read-only baseline only: `bash deploy/smoke/run-local-ops-baseline-suite.sh`
- collect governance observation only: `bash deploy/smoke/run-local-collect-governance-observation-suite.sh`
- collect legacy repair only: `bash deploy/smoke/run-local-collect-legacy-repair-suite.sh`

추천을 다시 열지 말지 빠르게 다시 보고 싶으면 아래 wrapper를 먼저 씁니다.

- recommendation reopen precheck: `APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-recommendation-reopen-precheck.sh`
- server/RDS recommendation reopen precheck: `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-recommendation-reopen-precheck.sh`
- recommendation observation suite: `APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-recommendation-observation-suite.sh`
- server/RDS recommendation observation suite: `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-recommendation-observation-suite.sh`
  - latest artifact: `tmp/recommendation-observation/latest-recommendation-observation-summary.txt`, `tmp/recommendation-observation/latest-recommendation-observation-note.md`, `tmp/recommendation-observation/latest-recommendation-observation.json`
  - latest housing effect stdout: `tmp/recommendation-observation/latest/housing-standard-code-effect.out`
  - latest welfare matrix stdout: `tmp/recommendation-observation/latest/welfare-standard-code-matrix.out`
  - `KEEP_ARTIFACTS=false` 기본값에서도 latest summary/json 과 `tmp/recommendation-observation/latest/` snapshot은 남습니다.
- housing standard code matrix audit: `bash deploy/smoke/run-local-housing-standard-code-matrix-audit.sh`
- welfare standard code matrix audit: `bash deploy/smoke/run-local-welfare-standard-code-matrix-audit.sh`
- user profile standard code coverage audit: `bash deploy/smoke/run-local-user-profile-standard-code-coverage-audit.sh`
- collect governance observation suite: `APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-collect-governance-observation-suite.sh`
- server/RDS collect governance observation suite: `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-collect-governance-observation-suite.sh`
  - latest artifact: `tmp/collect-governance-observation/latest-collect-governance-observation-summary.txt`, `tmp/collect-governance-observation/latest-collect-governance-observation-note.md`, `tmp/collect-governance-observation/latest-collect-governance-observation.json`
  - `KEEP_ARTIFACTS=false` 기본값에서도 latest summary/json 과 `tmp/collect-governance-observation/latest/` snapshot은 남습니다.
- auth observation suite: `APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-auth-observation-suite.sh`
- server/RDS auth observation suite: `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-auth-observation-suite.sh`
  - latest artifact: `tmp/auth-observation/latest-auth-observation-summary.txt`, `tmp/auth-observation/latest-auth-observation-note.md`, `tmp/auth-observation/latest-auth-observation.json`
  - `KEEP_ARTIFACTS=false` 기본값에서도 latest summary/json 과 `tmp/auth-observation/latest/` snapshot은 남습니다.
- ops observation suite: `APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-ops-observation-suite.sh`
- server/RDS ops observation suite: `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-ops-observation-suite.sh`
  - latest artifact: `tmp/ops-observation/latest-ops-observation-summary.txt`, `tmp/ops-observation/latest-ops-observation-note.md`, `tmp/ops-observation/latest-ops-observation.json`
  - attention feed is included in the same summary/json (`attention_feed_*`, `attention_feed.items`)
  - standard code coverage is included in the same summary/json (`user_profile_standard_code_*`)
  - recommendation standard code effect/matrix is included in the same summary/json (`recommendation_standard_code_*`)
  - `KEEP_ARTIFACTS=false` 기본값에서도 latest summary/json 과 `tmp/ops-observation/latest/` snapshot은 남습니다.
- admin attention feed: `GET /api/admin/dashboard/attention-feed`
  - collect drift, 표준코드 backlog, wrapper warning을 재사용 가능한 운영 알림 목록으로 반환합니다.
- frontend observation suite: `bash deploy/smoke/run-local-frontend-observation-suite.sh`
- server frontend observation suite: `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' bash deploy/smoke/run-local-frontend-observation-suite.sh`
  - 기본 deployed-origin 경계는 fresh e2e user/bootstrap을 먼저 준비하고 `@dev-only`, `@admin-required` 케이스를 제외합니다.
  - admin dashboard smoke까지 포함하려면 `RUN_FRONTEND_ADMIN_E2E=true` 를 명시합니다.
  - latest artifact: `tmp/frontend-observation/latest-frontend-observation-summary.txt`, `tmp/frontend-observation/latest-frontend-observation-note.md`, `tmp/frontend-observation/latest-frontend-observation.json`
  - `KEEP_ARTIFACTS=false` 기본값에서도 latest summary/json 과 `tmp/frontend-observation/latest/` snapshot은 남습니다.
- policy quality observation suite: `APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-policy-quality-observation-suite.sh`
- server/RDS policy quality observation suite: `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-policy-quality-observation-suite.sh`
  - latest artifact: `tmp/policy-quality-observation/latest-policy-quality-observation-summary.txt`, `tmp/policy-quality-observation/latest-policy-quality-observation-note.md`, `tmp/policy-quality-observation/latest-policy-quality-observation.json`
  - `KEEP_ARTIFACTS=false` 기본값에서도 latest summary/json 과 `tmp/policy-quality-observation/latest/` snapshot은 남습니다.

## 작업 전/후 읽는 법

- 상단 요약과 연결된 current-state 문서가 active source of truth입니다.
- 수치 기준선, 긴 판단 기록, 과거 closeout 맥락은 각 문서군의 current-state 또는 `history/` 문서에서 확인합니다.
- active 문서와 오래된 기록이 충돌하면 active 문서와 실제 코드를 우선합니다.

## 관련 기록

- 진행 기록: [phase-plan.md](./phase-plan.md)
- 문제/해결 로그: [core/troubleshooting-log.md](core/troubleshooting-log.md)
- 전체 길찾기: [documentation-map.md](./documentation-map.md)
