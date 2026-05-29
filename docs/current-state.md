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
- 공통 로컬 검증 문서군 진입점: [local-validation-docs-index.md](core/local-validation-docs-index.md)
- 서버 런타임 drift 체크리스트: [server-runtime-drift-checklist.md](core/server-runtime-drift-checklist.md)
- 보안/운영 hardening 현재 상태: [security-hardening-current-state.md](core/security-hardening-current-state.md)
- 운영 baseline wrapper: [ops-baseline-runbook.md](core/ops-baseline-runbook.md)
- 시스템 문서군 진입점: [system-docs-index.md](core/system-docs-index.md)
- 히스토리 문서군 진입점: [history-docs-index.md](./history-docs-index.md)

## 작업 전 기본 검증 기준

- one-shot local active baseline: `bash deploy/smoke/run-local-active-baseline-suite.sh`
- one-shot server active baseline: `APP_BASE_URL='http://127.0.0.1:8082' FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' bash deploy/smoke/run-local-active-baseline-suite.sh`
  - 참고: `deployed-origin` 모드는 배포 번들에서 성립하지 않는 `@dev-only` admin forced-failure Playwright 2개를 자동 제외합니다.
- one-shot current priority suite: `bash deploy/smoke/run-local-current-priority-suite.sh`
- one-shot server current priority suite: `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' bash deploy/smoke/run-local-current-priority-suite.sh`
- backend only: `cd backend && ./gradlew test --no-daemon`
- frontend only: `cd frontend && npm run lint && npm run build && npm run test:e2e`
- runtime read-only baseline only: `bash deploy/smoke/run-local-ops-baseline-suite.sh`
- collect legacy repair only: `bash deploy/smoke/run-local-collect-legacy-repair-suite.sh`

추천을 다시 열지 말지 빠르게 다시 보고 싶으면 아래 wrapper를 먼저 씁니다.

- recommendation reopen precheck: `APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-recommendation-reopen-precheck.sh`
- server/RDS recommendation reopen precheck: `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-recommendation-reopen-precheck.sh`
- recommendation observation suite: `APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-recommendation-observation-suite.sh`
- server/RDS recommendation observation suite: `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-recommendation-observation-suite.sh`

## 작업 전/후 읽는 법

- 상단 요약과 연결된 current-state 문서가 active source of truth입니다.
- 수치 기준선, 긴 판단 기록, 과거 closeout 맥락은 각 문서군의 current-state 또는 `history/` 문서에서 확인합니다.
- active 문서와 오래된 기록이 충돌하면 active 문서와 실제 코드를 우선합니다.

## 관련 기록

- 진행 기록: [phase-plan.md](./phase-plan.md)
- 문제/해결 로그: [core/troubleshooting-log.md](core/troubleshooting-log.md)
- 전체 길찾기: [documentation-map.md](./documentation-map.md)
