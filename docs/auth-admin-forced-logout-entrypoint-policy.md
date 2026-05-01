# Admin Forced Logout Entrypoint Policy

> Status note (2026-05-01)
>
> 이 문서는 현재 구현의 배경을 남긴 **design history** 입니다.
> 현재 제품/코드 계약은 [auth-session-revocation-current-state.md](./auth-session-revocation-current-state.md) 와
> [auth-admin-forced-logout-closeout.md](./auth-admin-forced-logout-closeout.md) 를 우선 기준으로 봅니다.


## 결정

future `admin forced logout` 의 **운영자 진입점** 은 1차로 **admin API** 로 두고, revoke state의 **즉시 source of truth** 는 **Redis cutoff/revocation key** 로 둔다.

즉 1차 설계는 아래 조합이다.

1. 운영자 명시 액션
   - admin API
2. 즉시 revoke state
   - Redis
3. 장기 정책 source
   - 기존 user row / allowlist / account state와 별도

다시 말해, `forced logout` 은 DB role/source를 바꾸는 기능이 아니라 **운영자가 API로 트리거하는 session/token revoke** 로 본다.

## 이유

### 1. 운영자 명시 액션은 API가 가장 제품 의미가 분명하다

이번 기능은 아래 의미를 가진다.

- 특정 사용자/admin의 기존 세션을 지금 끊는다
- offboarding/incident response에서 운영자가 직접 실행한다

이 성격이면 1차 진입점은 `admin API` 가 가장 분명하다.

- 누가 실행했는지 남기기 쉽다
- 대상 user를 명시적으로 받기 쉽다
- integration smoke를 붙이기 쉽다

반대로 DB 직접 수정이나 수동 Redis key 주입은 운영 편의용 우회 경로일 수는 있어도, 제품 계약의 1차 진입점으로 두기엔 의미가 흐려진다.

### 2. 즉시 차단은 Redis가 DB보다 맞다

`forced logout` baseline은 아래를 요구한다.

- old access token 즉시 차단
- old refresh token 즉시 차단

이건 요청 path에서 filter/auth layer가 빠르게 봐야 하는 정보다. 현재 auth stack도 refresh token과 access revoke를 Redis로 다루고 있으므로, 1차 immediate cutoff는 Redis가 가장 자연스럽다.

DB를 source of truth로 두면:

- filter path에서 read 경계가 더 커지고
- 즉시성/운영 응답성이 떨어지고
- allowlist/account state와 의미가 다시 섞일 가능성이 있다

### 3. forced logout은 allowlist revoke와 계층이 다르다

- `SECURITY_ADMIN_EMAILS`
  - future role issuance 제어
- `forced logout`
  - existing token/session 제어

따라서 이 기능의 storage/source도 분리하는 편이 낫다.

`forced logout` 을 DB persistent role state에 섞으면:

- account lock
- admin role revoke
- session revoke

가 다시 한 테이블/플래그로 합쳐질 수 있다.

## 제외하는 것

현재 phase에서 아래는 1차 진입점으로 두지 않는다.

### 1. DB 직접 수정

운영자가 SQL로 cutoff를 박는 방식은 break-glass 용도일 수는 있어도, 제품 기본 경로로 두지 않는다.

### 2. Redis 수동 key 주입

디버깅/긴급 대응용 보조 수단일 수는 있지만, 운영자가 매번 key schema를 직접 알아야 하는 형태는 기본 경로가 아니다.

### 3. account lock 재사용

account lock은 로그인/활성 상태를 더 넓게 바꾸는 개념이라 `forced logout` 의 최소 범위보다 크다.

## 1차 설계 스케치

### trigger

- `POST /api/admin/.../forced-logout`
- 대상 `userKey` 또는 `userId` 명시

### immediate state

- refresh 재발급 차단용 Redis key
- access revoke/cutoff 확인용 Redis key

### expected behavior

- old access token
  - 즉시 `401 / A006`
- old refresh token
  - 즉시 refresh 실패
- fresh login
  - account lock이 없다면 가능할 수 있음

## next step

다음 작은 task는 `forced logout admin API` 의 최소 request/response 계약을 문서로 고정하는 것이다.

즉:

- path
- target identifier
- idempotency
- 성공 시 기대되는 revoke 범위

를 먼저 박은 뒤, 그 다음에 Redis key shape와 integration baseline을 연다.
