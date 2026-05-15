# 로컬 검증 문서 묶음

## 목적

로컬 검증 관련 문서가 흩어져 있어도

- 어떤 검증을 언제 실행하는지
- 브라우저 없이 API smoke를 어디서 확인하는지
- 데모/기능 검수 때 어떤 순서로 따라가면 되는지

를 한 문서에서 바로 찾게 정리합니다.

## 지금 먼저 볼 문서

### 공통 실행 기준

- [testing.md](./testing.md)
- [runtime-api-smoke-commands.md](./runtime-api-smoke-commands.md)
- runtime preflight shell 진입점: `deploy/smoke/preflight-integration-runtime.sh`

### 같이 보면 좋은 문서

- [demo-scenario.md](./demo-scenario.md)
- [policy-quality-summary-runbook.md](../policy/policy-quality-summary-runbook.md)
- [policy-gov24-runtime-audit-runbook.md](../policy/policy-gov24-runtime-audit-runbook.md)
- [policy-admin-runtime-runbook.md](../policy/policy-admin-runtime-runbook.md)
- [recommendation-ctr-readiness-runbook.md](../recommendation/recommendation-ctr-readiness-runbook.md)
- [collect-docs-index.md](../collect/collect-docs-index.md)
- [recommendation-docs-index.md](../recommendation/recommendation-docs-index.md)
- [frontend-qa-docs-index.md](../frontend/frontend-qa-docs-index.md)
- [phase-plan.md](../phase-plan.md)

## 문서 역할

### 1. 테스트 실행 기준

- [testing.md](./testing.md)

이 문서는

- `./gradlew test`
- `./gradlew integrationTest`
- Gmail SMTP smoke
- split-account 계정 복구

를 언제 어떻게 실행하는지 정리한 공통 실행 기준입니다.

### 2. 런타임 API smoke

- [runtime-api-smoke-commands.md](./runtime-api-smoke-commands.md)

이 문서는

- 로그인
- refresh
- 추천 조회/북마크
- admin status
- logout

을 curl 기준으로 바로 복사해 돌리는 최소 API smoke 모음입니다.

현재 auth/session revoke closeout의 기본 진입점은
`deploy/smoke/run-local-auth-session-smoke.sh` 입니다.

현재 로컬 검증 전체 기준선을 다시 확인할 때는
`deploy/smoke/run-local-validation-from-env.sh` 를 우선 사용합니다.
이 wrapper는 `.env`, local admin smoke 계정 파일, API base URL override를 같이 정규화하므로
직접 `run-local-validation-suite.sh` 를 치는 것보다 현재 로컬 환경에서 덜 틀리게 재현됩니다.

`deploy/smoke/run-local-validation-suite.sh` 는
- env를 이미 명시적으로 정규화한 경우
- wrapper 없이 raw suite를 호출해야 하는 경우
의 보조 진입점으로 봅니다.

짧은 재검증은 `--quick`, 전체 기준선은 `--full` 을 사용합니다.

### 3. 데모 시나리오

- [demo-scenario.md](./demo-scenario.md)

이 문서는

- 회원가입
- 로그인
- 프로필/우선순위
- 정책 목록/검색/상세
- 추천 생성/조회
- 북마크/알림/refresh/logout

을 발표/기능 검수 순서대로 따라가는 데모 시나리오입니다.

### 4. bounded runtime quality / audit

- [policy-quality-summary-runbook.md](../policy/policy-quality-summary-runbook.md)
- [policy-gov24-runtime-audit-runbook.md](../policy/policy-gov24-runtime-audit-runbook.md)
- [policy-admin-runtime-runbook.md](../policy/policy-admin-runtime-runbook.md)
- [recommendation-ctr-readiness-runbook.md](../recommendation/recommendation-ctr-readiness-runbook.md)

이 문서군은

- retrieval/category quality baseline
- Gov24 runtime closeout / deferred inventory audit
- bounded admin runtime 응답 baseline
- CTR readiness baseline

을 one-shot smoke/runbook 기준으로 다시 확인할 때 먼저 봅니다.

## 읽는 순서

### 빠르게 빌드/테스트만 확인할 때

1. [testing.md](./testing.md)

### 런타임 API를 바로 찍어볼 때

1. [runtime-api-smoke-commands.md](./runtime-api-smoke-commands.md)
2. 전체 baseline 재확인은 `deploy/smoke/run-local-validation-from-env.sh --quick|--full`
3. 필요하면 각 도메인 문서군 index

### 발표/기능 검수 순서를 준비할 때

1. [demo-scenario.md](./demo-scenario.md)
2. 필요하면 [runtime-api-smoke-commands.md](./runtime-api-smoke-commands.md)

### bounded runtime baseline을 다시 확인할 때

1. [policy-quality-summary-runbook.md](../policy/policy-quality-summary-runbook.md)
2. [policy-gov24-runtime-audit-runbook.md](../policy/policy-gov24-runtime-audit-runbook.md)
3. [policy-admin-runtime-runbook.md](../policy/policy-admin-runtime-runbook.md)
4. [recommendation-ctr-readiness-runbook.md](../recommendation/recommendation-ctr-readiness-runbook.md)

## 요약

1. 단위/통합 테스트 실행 기준은 [testing.md](./testing.md) 부터 봅니다.
2. curl 기반 API smoke는 [runtime-api-smoke-commands.md](./runtime-api-smoke-commands.md) 를 보고, 전체 baseline은 가능하면 `run-local-validation-from-env.sh` wrapper부터 사용합니다.
3. 데모/검수 순서는 [demo-scenario.md](./demo-scenario.md) 를 기준으로 잡습니다.
4. bounded runtime quality/audit baseline은 policy/recommendation runbook 4종을 먼저 봅니다.
