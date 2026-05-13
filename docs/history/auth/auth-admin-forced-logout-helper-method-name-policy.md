# Admin Forced Logout Helper Method Name Policy

> Status note (2026-05-01)
>
> 이 문서는 현재 구현의 배경을 남긴 **design history** 입니다.
> 현재 제품/코드 계약은 [auth-session-revocation-current-state.md](../../auth/auth-session-revocation-current-state.md) 와
> [auth-admin-forced-logout-closeout.md](../../auth/auth-admin-forced-logout-closeout.md) 를 우선 기준으로 봅니다.


## 결정

`UserSessionRevocationService` 의 1차 메서드명은 아래처럼 **그대로 유지** 한다.

1. `boolean isAccessAllowed(String accessToken)`
2. `void revokeUserSessions(String userKey, long cutoffMillis)`

즉 이번 phase에서는 더 domain-specific 한 이름으로 다시 바꾸지 않는다.

## 이유

### 1. read 메서드는 filter 관점의 질문을 그대로 담는다

`JwtAuthenticationFilter` 가 알고 싶은 것은 결국:

- 이 access token으로 지금 SecurityContext를 세워도 되는가

이다.

그래서:

- `isAccessAllowed`

는 filter의 질문을 가장 직접적으로 담는다.

반대로 아래 이름들은 세부 구현을 너무 드러내거나 의미가 좁다.

- `isAccessRevoked`
- `isTokenPastCutoff`
- `isForcedLogoutRejected`

현재 helper는:

- exact revoke
- forced logout cutoff
- legacy token reject

를 모두 안에서 캡슐화하므로, 최종 결과 중심 이름이 더 맞다.

### 2. write 메서드는 user 단위 revoke intent를 정확히 설명한다

`revokeUserSessions(...)` 는:

- 대상이 token 1개가 아니라 userKey 기준 세션 묶음이라는 점
- refresh delete + cutoff 기록을 함께 수행한다는 점

을 가장 무리 없이 담는다.

아래 대안들은 현재 phase 기준으로 덜 적합하다.

- `forceLogoutUser`
  - API action 이름과 너무 가깝고 service 내부 책임보다 상위 use case를 더 드러냄
- `applyAccessCutoff`
  - refresh delete 의미가 빠짐
- `revokeUserAccess`
  - refresh/session 범위가 약해짐

### 3. 이름을 더 세분화하면 helper 내부 규칙이 다시 새어 나온다

예를 들어:

- `isTokenPastCutoff`
- `deleteRefreshAndWriteCutoff`

같은 이름은 구현 세부를 바깥에 다시 노출한다.

현재는:

- filter는 allow/deny
- admin API는 revoke intent write

만 알면 되므로, 메서드명도 그 수준을 넘지 않는 편이 낫다.

## 선택하지 않는 이름

### read

- `isRevoked(String accessToken)`
  - exact-token blacklist 의미가 너무 강함
- `isAccessPermitted(String accessToken)`
  - 가능은 하지만 current codebase에서 `allowed` 쪽이 더 자연스러움
- `shouldAuthenticate(String accessToken)`
  - filter 내부 구현 흐름과 너무 결합됨

### write

- `forceLogoutUser(String userKey, long cutoffMillis)`
  - use case 이름을 다시 중복함
- `revokeUserAccess(String userKey, long cutoffMillis)`
  - refresh/session 범위가 축소됨
- `applyUserCutoff(String userKey, long cutoffMillis)`
  - refresh delete 의미가 드러나지 않음

## 권장 최종 형태

```java
public interface UserSessionRevocationService {
    boolean isAccessAllowed(String accessToken);
    void revokeUserSessions(String userKey, long cutoffMillis);
}
```

## next step

다음 작은 task는 이 이름/인터페이스 정책을 바탕으로, 실제 구현 시 `UserSessionRevocationService` 를 새 클래스로 추가할지 아니면 기존 `AccessTokenRevocationService` 와 composition 관계로 둘지 구조를 고정하는 것이다.
