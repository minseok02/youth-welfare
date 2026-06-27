# Admin Forced Logout Helper Interface

> Status note (2026-05-01)
>
> 이 문서는 현재 구현의 배경을 남긴 **design history** 입니다.
> 현재 제품/코드 계약은 [auth-session-revocation-current-state.md](../../auth/auth-session-revocation-current-state.md) 와
> [auth-admin-forced-logout-closeout.md](../../auth/auth-admin-forced-logout-closeout.md) 를 우선 기준으로 봅니다.


## 결정

future `admin forced logout` helper/service의 최소 인터페이스는 **읽기(read) 1개 + 쓰기(write) 1개** 로 시작한다.

권장 형태는 아래다.

1. `boolean isAccessAllowed(String accessToken)`
2. `void revokeUserSessions(String userKey, long cutoffMillis)`

즉 filter는 “이 access token을 현재 auth gate에서 허용할 수 있는가”만 묻고, admin forced logout API는 “이 userKey 기준 revoke intent를 기록하라”만 요청한다.

## 왜 `isAccessAllowed(...)` 인가

### 1. filter가 알고 싶은 건 최종 allow/deny다

`JwtAuthenticationFilter` 입장에서 필요한 건:

- exact token revoke인지
- forced logout cutoff에 걸리는지
- legacy token인지

를 각각 알고 싶다기보다, **결국 이 token으로 SecurityContext를 세워도 되는지** 다.

그래서 1차 read 인터페이스는:

- `isRevoked(token)`
- `isCutoff(token)`

처럼 세부 규칙을 여러 개 노출하는 것보다, 최종 allow/deny 하나로 두는 편이 filter를 단순하게 유지한다.

### 2. 세부 규칙은 helper 내부에서 캡슐화한다

helper/service 내부에서는 아래를 순서대로 볼 수 있다.

1. `access-revoked:{token}`
2. token parse / validate / subject
3. `iatm`
4. `access-cutoff:{userKey}`
5. cutoff 비교

하지만 이걸 바깥에 그대로 노출하면 filter가 다시 세부 구현에 묶인다.

## 왜 `revokeUserSessions(...)` 인가

### 1. forced logout write는 user 단위 intent 기록이다

forced logout API의 의미는:

- 특정 `userKey` 의 기존 access/refresh 세션을 끊는다

이다.

따라서 write 인터페이스는 token 1개 revoke가 아니라:

- 대상 user 식별자
- cutoff 시각

을 받는 게 맞다.

### 2. refresh delete + cutoff 기록을 한 메서드로 묶는다

이 write 메서드는 내부에서:

- `refresh:{userKey}` delete
- `access-cutoff:{userKey}` write

를 같이 처리한다.

즉 API/controller는 Redis key shape를 모르고, helper/service가 write bundle을 책임진다.

## 현재 `AccessTokenRevocationService` 와의 관계

현재 [AccessTokenRevocationService.java](../../../backend/src/main/java/com/example/welfare/user/service/AccessTokenRevocationService.java) 는:

- `revoke(accessToken)`
- `isRevoked(accessToken)`

만 가진 exact-token blacklist service다.

forced logout은 범위와 의미가 다르므로, 1차로는 이 클래스를 억지로 확장하기보다 별도 helper/service를 둔다는 방향을 기본으로 둔다.

즉:

- logout
  - exact token revoke service
- forced logout
  - user session revoke helper/service

로 역할을 분리한다.

## 선택하지 않는 인터페이스

### 1. `boolean isRevoked(String token)` 단일 확장

이 이름은 exact token blacklist 의미가 너무 강하다.  
forced logout cutoff, legacy token reject, future account lock까지 담기 시작하면 이름이 오히려 좁다.

### 2. `boolean isCutoff(String token)` + `boolean isRevoked(String token)` 분리 노출

filter가 두세 개 메서드를 순서대로 호출하면 helper 내부 규칙이 다시 외부로 새어나간다.

### 3. `void revokeAccessToken(String token)` 재사용

forced logout write는 token 원문을 모르는 상황이 기본이라 맞지 않는다.

## future implementation sketch

### filter read path

```java
if (!forcedLogoutGuard.isAccessAllowed(token)) {
    SecurityContextHolder.clearContext();
} else {
    // proceed
}
```

### admin API write path

```java
forcedLogoutGuard.revokeUserSessions(userKey, cutoffMillis);
```

## current phase에서 제외하는 것

이번 인터페이스 정책은 아래까지 같이 열지 않는다.

- revoke reason enum
- actor/admin audit payload
- batch revoke(list of userKeys)
- per-device session revoke

## next step

다음 작은 task는 이 인터페이스 정책을 기준으로, helper/service 이름을 무엇으로 둘지 고정하는 것이다.

예:

- `AdminForcedLogoutGuard`
- `UserSessionRevocationService`
- `AccessSessionCutoffService`

중 어떤 이름이 현재 역할을 가장 정확히 담는지 정리하면 된다.
