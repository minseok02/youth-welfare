# Admin Forced Logout Implementation Order

> Status note (2026-05-01)
>
> 이 문서는 현재 구현의 배경을 남긴 **design history** 입니다.
> 현재 제품/코드 계약은 [auth-session-revocation-current-state.md](../../auth-session-revocation-current-state.md) 와
> [auth-admin-forced-logout-closeout.md](../../auth-admin-forced-logout-closeout.md) 를 우선 기준으로 봅니다.


## 결정

future `admin forced logout` 구현 순서는 아래처럼 둔다.

1. `JwtUtil` 에 `iatm` write/read helper 추가
2. `UserSessionRevocationService` skeleton 추가
3. `JwtAuthenticationFilter` wiring
4. admin forced logout API write path 연결
5. integration smoke 추가

즉 **service skeleton보다 `JwtUtil` helper를 먼저** 넣는다.

## 이유

### 1. service skeleton이 먼저 생기면 핵심 claim 계약이 다시 임시화된다

`UserSessionRevocationService` 의 핵심 read path는:

- subject/userKey
- `iatm`
- cutoff 비교

다.

여기서 `JwtUtil` helper가 먼저 없으면 서비스 쪽에서:

- claims 직접 파싱
- 임시 fallback
- TODO 형태의 빈 비교

가 들어가기 쉽다.

즉 service skeleton부터 열면 결국 가장 중요한 `iatm` 계약이 다시 느슨해진다.

### 2. `JwtUtil` helper가 먼저 있어야 service dependency가 깔끔하다

이미 [auth-admin-forced-logout-package-dependencies-policy.md](./auth-admin-forced-logout-package-dependencies-policy.md) 에서 `UserSessionRevocationService` 의 최소 dependency로 `JwtUtil` 을 고정했다.

그렇다면 구현도:

- `JwtUtil` 이 `iatm` write/read를 책임지게 만든 뒤
- 서비스는 그 helper를 소비

하는 순서가 더 자연스럽다.

### 3. filter wiring은 가장 나중이 맞다

`JwtAuthenticationFilter` 에 강제 로그아웃 비교를 붙이는 순간 전체 보호 경로에 영향이 간다.

그래서 순서는:

1. helper/claim 준비
2. service skeleton
3. 마지막에 filter wiring

으로 두는 편이 회귀 반경이 작다.

## 권장 구현 순서 상세

### 1. `JwtUtil`

- `iatm` claim write
- `getIssuedAtMillis(...)`
- `getIssuedAtMillisAllowExpired(...)`

### 2. `UserSessionRevocationService`

- constructor dependency만 연결
- `isAccessAllowed(...)`
- `revokeUserSessions(...)`

### 3. `JwtAuthenticationFilter`

- exact revoke check 다음에 session revoke check 추가
- deny 시 기존과 같이 SecurityContext clear

### 4. admin API

- `POST /api/admin/users/forced-logout`
- `revokeUserSessions(...)` 호출

### 5. tests

- unit: `JwtUtil`
- unit/service: `UserSessionRevocationService`
- integration: forced logout baseline

## 선택하지 않는 순서

### 1. service skeleton 먼저

claim 계약이 다시 임시 구현으로 흐를 위험이 있다.

### 2. filter wiring 먼저

전체 auth gate에 너무 일찍 영향이 간다.

### 3. admin API 먼저

write path만 열리고 read gate가 비어 있으면 half-implemented 상태가 된다.

## next step

다음 작은 task는 이 순서 정책을 바탕으로, 실제 구현을 `JwtUtil` helper 추가부터 시작한다고 문서가 아니라 코드로 여는 것이다.
