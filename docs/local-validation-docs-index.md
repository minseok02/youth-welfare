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

### 같이 보면 좋은 문서

- [demo-scenario.md](./demo-scenario.md)
- [collect-docs-index.md](./collect-docs-index.md)
- [recommendation-docs-index.md](./recommendation-docs-index.md)
- [frontend-qa-docs-index.md](./frontend-qa-docs-index.md)
- [phase-plan.md](./phase-plan.md)

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

현재 로컬 검증 전체 기준선을 빠르게 다시 확인하는 기본 진입점은
`deploy/smoke/run-local-validation-suite.sh` 입니다.

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

## 읽는 순서

### 빠르게 빌드/테스트만 확인할 때

1. [testing.md](./testing.md)

### 런타임 API를 바로 찍어볼 때

1. [runtime-api-smoke-commands.md](./runtime-api-smoke-commands.md)
2. 필요하면 각 도메인 문서군 index

### 발표/기능 검수 순서를 준비할 때

1. [demo-scenario.md](./demo-scenario.md)
2. 필요하면 [runtime-api-smoke-commands.md](./runtime-api-smoke-commands.md)

## 요약

1. 단위/통합 테스트 실행 기준은 [testing.md](./testing.md) 부터 봅니다.
2. curl 기반 API smoke는 [runtime-api-smoke-commands.md](./runtime-api-smoke-commands.md) 를 봅니다.
3. 데모/검수 순서는 [demo-scenario.md](./demo-scenario.md) 를 기준으로 잡습니다.
