# Admin Forced Logout Service Structure Policy

> Status note (2026-05-01)
>
> 이 문서는 현재 구현의 배경을 남긴 **design history** 입니다.
> 현재 제품/코드 계약은 [auth-session-revocation-current-state.md](./auth-session-revocation-current-state.md) 와
> [auth-admin-forced-logout-closeout.md](./auth-admin-forced-logout-closeout.md) 를 우선 기준으로 봅니다.


## 결정

`UserSessionRevocationService` 는 **새 클래스로 추가** 하고, 기존 `AccessTokenRevocationService` 와는 **composition 관계** 로 둔다.

즉 구조는 아래처럼 가져간다.

- `AccessTokenRevocationService`
  - exact-token blacklist 전용
- `UserSessionRevocationService`
  - user session revoke / cutoff 전용
  - 필요 시 `AccessTokenRevocationService` 를 내부 dependency로 사용

현재 phase에서는 두 책임을 한 클래스로 합치지 않는다.

## 이유

### 1. exact-token revoke와 user-session revoke는 책임이 다르다

현재 [AccessTokenRevocationService.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/user/service/AccessTokenRevocationService.java) 는:

- access token 문자열 1개
- 남은 만료 시간 TTL 계산
- `access-revoked:{token}` key write/read

만 담당한다.

반면 future `UserSessionRevocationService` 는:

- `refresh:{userKey}` delete
- `access-cutoff:{userKey}` write/read
- `iatm` 비교
- legacy token reject

를 담당한다.

즉 “token 하나”와 “user session 묶음”은 책임 단위가 다르다.

### 2. 상속보다 조합이 더 안전하다

이 둘을 상속으로 엮으면:

- forced logout이 exact-token revoke의 확장처럼 보이고
- exact-token revoke 구현 세부가 새 서비스로 새어 나가기 쉽다

하지만 실제로는:

- logout/withdraw presented token revoke
- admin forced logout session revoke

가 나란한 sibling 책임에 가깝다.

그래서 새 클래스 + composition이 더 자연스럽다.

### 3. 구현 변경 반경이 작다

새 클래스로 두면:

- 기존 logout/withdraw 경로는 `AccessTokenRevocationService` 그대로 유지
- forced logout만 새 서비스로 추가
- 이후 필요하면 filter에서 두 서비스를 함께 사용

할 수 있다.

기존 클래스를 확장/흡수하면 이미 안정화된 logout revoke 경계까지 다시 건드리게 된다.

## 권장 구조

### exact revoke

```java
class AccessTokenRevocationService {
    void revoke(String accessToken);
    boolean isRevoked(String accessToken);
}
```

### session revoke

```java
class UserSessionRevocationService {
    boolean isAccessAllowed(String accessToken);
    void revokeUserSessions(String userKey, long cutoffMillis);
}
```

필요하면 `UserSessionRevocationService` 내부에서:

- `AccessTokenRevocationService`
- `JwtUtil`
- `RedisTemplate`

를 조합한다.

## 선택하지 않는 구조

### 1. `AccessTokenRevocationService` 에 forced logout까지 흡수

이 경우 클래스 이름과 책임이 동시에 커진다.  
`revoke(token)` 와 `revokeUserSessions(userKey, cutoffMillis)` 가 한곳에 있으면 “token blacklist service” 와 “session revoke service” 의미가 섞인다.

### 2. `UserSessionRevocationService extends AccessTokenRevocationService`

상속 관계로 두면 exact-token revoke가 session revoke의 기반 클래스처럼 보이지만, 실제 제품 의미는 그렇지 않다.

### 3. filter가 두 Redis 규칙을 직접 모두 구현

서비스 분리 없이 filter가:

- exact revoke
- cutoff
- legacy token reject

를 직접 다루면 auth gate가 비대해진다.

## current phase에서 제외하는 것

이번 구조 정책은 아래까지 같이 열지 않는다.

- interface/implementation 분리 강제
- multi-module auth package 재배치
- abstract base revoke class

## next step

다음 작은 task는 이 구조 정책을 기준으로, `UserSessionRevocationService` 를 어느 package에 둘지와 생성자 dependency 최소 집합을 문서로 고정하는 것이다.
