# Admin Forced Logout Service Structure Policy

> Status note (2026-05-01)
>
> 이 문서는 현재 구현의 배경을 남긴 **design history** 입니다.
> 현재 제품/코드 계약은 [auth-session-revocation-current-state.md](../../auth/auth-session-revocation-current-state.md) 와
> [auth-admin-forced-logout-closeout.md](../../auth/auth-session-revocation-current-state.md#closeout과-후속-범위) 를 우선 기준으로 봅니다.


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

현재 [AccessTokenRevocationService.java](../../../backend/src/main/java/com/example/welfare/user/service/AccessTokenRevocationService.java) 는:

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

## 통합한 서비스 결정

다음은 2026-05 설계 당시의 이름·인터페이스·적용 순서 근거입니다. 현재 구현은 `UserSessionRevocationService`와 인증 현재 상태를 확인하며 아래 순서를 새 작업 지시로 재실행하지 않습니다.

### 서비스 이름

`UserSessionRevocationService`는 사용자별 refresh 세션 무효화와 access cutoff 판정을 담당합니다. `AccessTokenRevocationService`는 제시된 token 하나의 exact blacklist를 담당하므로 두 책임을 구분합니다. `AdminForcedLogoutGuard`는 admin 전용/read-only guard처럼 보이고, `AccessSessionCutoffService`는 refresh 삭제·세션 무효화보다 cutoff 구현만 강조하므로 당시 선택하지 않았습니다. withdraw 등 다른 session 사건에서도 같은 책임을 재사용할 수 있다는 판단을 보존합니다.

### 메서드 이름

- `isAccessAllowed(String accessToken)`: filter가 필요한 최종 allow/deny를 반환합니다. exact revoke·cutoff·legacy token 판정을 내부에 감추므로 `isTokenPastCutoff` 같은 구현 중심 이름으로 줄이지 않습니다.
- `revokeUserSessions(String userKey, long cutoffMillis)`: 사용자 단위 refresh 삭제와 cutoff 기록의 intent를 표현합니다. `forceLogoutUser`는 상위 use case와 겹치고 `applyAccessCutoff`는 refresh 삭제 의미가 빠진다는 판단을 보존합니다.

### 구현 순서

당시 적용 순서는 `JwtUtil의 iatm write/read helper -> session service -> filter wiring -> admin write API -> 단위/통합 smoke`였습니다. claim 계약 없이 임시 parsing/fallback을 service에 넣거나, revoke write 경로만 연결하고 인증 read gate를 비워 두는 위험을 피하기 위한 순서입니다. filter 연결은 모든 보호 경로에 영향을 주므로 claim/service 검증 뒤 적용한다는 근거를 남깁니다.
