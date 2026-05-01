# Admin Forced Logout Implementation Location

> Status note (2026-05-01)
>
> 이 문서는 현재 구현의 배경을 남긴 **design history** 입니다.
> 현재 제품/코드 계약은 [auth-session-revocation-current-state.md](./auth-session-revocation-current-state.md) 와
> [auth-admin-forced-logout-closeout.md](./auth-admin-forced-logout-closeout.md) 를 우선 기준으로 봅니다.


## 결정

future `admin forced logout` 의 `A006` 차단은 **controller/service guard가 아니라 `JwtAuthenticationFilter` 앞단** 에서 처리하고, 실제 비교 로직은 filter가 직접 들고 있지 않고 **전용 helper/service** 로 분리한다.

즉 1차 구현 경계는 아래다.

1. `JwtAuthenticationFilter`
   - bearer token 추출
   - JWT validate
   - forced logout helper 호출
   - 차단 시 SecurityContext 미설정
2. dedicated helper/service
   - `userKey`
   - `iatm`
   - Redis cutoff/revocation state
   - 비교 후 allow/deny 판단

다시 말해:

- **판단 시점** 은 filter
- **판단 로직** 은 helper/service

로 분리한다.

## 왜 filter에서 처리하는가

### 1. forced logout은 auth-layer concern이다

이 기능의 의미는:

- 이 token을 현재 보호 경계에서 더 이상 인증 세션으로 인정하지 않음

이다.

이건 business rule보다 authentication rule에 가깝다.  
따라서 controller/service로 내려보내기 전에 걸러야 한다.

### 2. controller/service guard로 내리면 누락 surface가 커진다

만약 forced logout 비교를 각 controller/service에서 하면:

- `/api/admin/**`
- `/api/users/**`
- `/api/recommendations/**`

마다 같은 guard를 반복 적용하거나, 특정 경로에서 빠질 위험이 생긴다.

반면 filter에서 처리하면 bearer token을 쓰는 모든 보호 경로가 같은 규칙을 공유할 수 있다.

### 3. 현재 revoke 계열도 이미 filter 앞단 의미로 수렴한다

현재 `logout` presented-token revoke는 [JwtAuthenticationFilter.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/global/config/JwtAuthenticationFilter.java) 에서:

- `access-revoked:{token}` 확인
- 차단 시 SecurityContext clear

로 동작한다.

forced logout도 같은 계층에서 처리해야:

- logout exact revoke
- forced logout cutoff

이 한 auth gate에서 일관되게 보인다.

## 왜 helper/service를 따로 두는가

### 1. filter에 Redis/JWT 비교 세부 로직을 다 넣지 않는다

forced logout은 단순 boolean check가 아니다.

- `userKey` 추출
- `iatm` 추출
- exact revoke
- cutoff key 조회
- cutoff 비교
- legacy token 판단

이 섞인다.

이걸 filter 본문에 직접 넣으면 `JwtAuthenticationFilter` 가 비대해진다.

### 2. 이후 테스트 분리가 쉬워진다

전용 helper/service가 있으면:

- pure unit test
- integration test
- filter wiring test

를 분리하기 쉽다.

### 3. future event별 cutoff 확장에도 유리하다

나중에:

- withdraw
- admin forced logout
- account lock

같은 이벤트별 차단 규칙이 늘어날 수 있다.  
helper/service를 두면 filter는 “이 토큰을 현재 auth gate에서 허용할지”만 묻고, 세부 event rule은 helper 쪽에서 확장할 수 있다.

## 에러 흐름

구현 시 에러 의미는 아래처럼 둔다.

- filter/helper가 forced logout 차단 판단
  - SecurityContext 미설정
- 이후 보호 API 접근
  - `401 / A006`

즉 filter가 직접 response를 write하기보다, 현재 revoke 경계와 같이 **인증 실패 상태를 만들고 인가 단계에서 `A006` 으로 수렴** 하는 쪽을 기본으로 둔다.

## 제외하는 것

현재 phase에서 아래는 기본 구현 위치로 두지 않는다.

### 1. controller annotation / interceptor

경로별 누락 가능성이 크다.

### 2. service method 시작부 guard

business rule과 auth rule이 섞인다.

### 3. Redis 수동 확인을 admin API 안에서 직접 처리

`POST /api/admin/users/forced-logout` 는 revoke intent를 기록하는 API이지, 요청마다 old token을 판정하는 위치가 아니다.

## next step

다음 작은 task는 이 구현 위치 정책을 기준으로, future helper/service의 최소 인터페이스를 문서로 고정하는 것이다.

예:

- `boolean isRevoked(String token)`
- `boolean isCutoff(String token)`
- `void revokeUserSessions(String userKey, long cutoffMillis)`

중 어떤 형태가 적절한지 먼저 정리하면 된다.
