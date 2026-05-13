# Auth 문서 묶음

## 목적

`auth-*` 문서가 많아진 이유와, 지금 어디부터 읽어야 하는지를 한 문서에서 정리합니다.

## 왜 문서가 많아졌나

`auth` 문서군은 한 번에 큰 설계를 끝낸 결과물이 아니라, 아래 과정을 거치며 쪼개졌습니다.

1. `logout`, `withdraw`, `admin allowlist revoke`, `admin forced logout` 를 한 번에 열지 않고 작은 task 단위로 나눠 검증했습니다.
2. 각 단계에서 실제 코드와 테스트 계약을 먼저 고정하고, 그때그때 판단 근거를 별도 문서로 남겼습니다.
3. 특히 `admin forced logout` 은 `entrypoint -> Redis shape -> iatm -> legacy token -> helper interface -> API contract` 순서로 잘게 설계돼 design history 문서가 많아졌습니다.

즉 현재 `auth-*` 문서 다수는 "지금 당장 읽어야 하는 현재 계약" 이 아니라, 구현 배경을 남긴 작업 로그에 가깝습니다.

## 지금 먼저 볼 문서

### 현재 코드 계약

- [auth-session-revocation-current-state.md](./auth-session-revocation-current-state.md)
- [auth-admin-forced-logout-closeout.md](./auth-admin-forced-logout-closeout.md)

이 두 문서가 현재 구현 기준의 source of truth 입니다.

### 로컬/런타임 확인

- [auth-operation-checklist.md](./auth-operation-checklist.md)
- [auth-incident-template.md](./auth-incident-template.md)
- [runtime-api-smoke-commands.md](../core/runtime-api-smoke-commands.md)
- [testing.md](../core/testing.md)

## design history 로 읽을 문서

### forced logout cluster

- `auth-admin-forced-logout-*`

읽는 이유:

- 왜 `iatm` 이 필요해졌는지
- 왜 `JwtAuthenticationFilter` 에서 차단하는지
- 왜 `UserSessionRevocationService` 와 `AccessTokenRevocationService` 를 분리했는지

### 기타 auth policy

- [auth-logout-revocation-scope-policy.md](../history/auth/auth-logout-revocation-scope-policy.md)
- [auth-withdraw-revocation-next-step.md](../history/auth/auth-withdraw-revocation-next-step.md)
- [auth-admin-refresh-revoke-policy.md](../history/auth/auth-admin-refresh-revoke-policy.md)
- [auth-admin-revoke-boundary-policy.md](../history/auth/auth-admin-revoke-boundary-policy.md)
- [auth-revocation-reopen-order.md](../history/auth/auth-revocation-reopen-order.md)

읽는 이유:

- 현재 구현 범위 밖으로 어디까지 일부러 안 열었는지 확인할 때

## 지금 기준으로 기억할 핵심

1. 현재 구현 확인은 개별 `auth-admin-forced-logout-*` 문서가 아니라 [auth-session-revocation-current-state.md](./auth-session-revocation-current-state.md)부터 봅니다.
2. 실제 확인 순서는 [auth-operation-checklist.md](./auth-operation-checklist.md)를 따릅니다.
3. 실행 결과 기록은 [auth-incident-template.md](./auth-incident-template.md)를 복사해서 씁니다.
4. 개별 `auth-*` 문서는 대부분 판단 근거를 남긴 design history 입니다.
5. 코드와 문서가 충돌하면 코드와 current-state 문서가 우선입니다.
