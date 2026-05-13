# Admin Forced Logout Helper Name Policy

> Status note (2026-05-01)
>
> 이 문서는 현재 구현의 배경을 남긴 **design history** 입니다.
> 현재 제품/코드 계약은 [auth-session-revocation-current-state.md](../../auth/auth-session-revocation-current-state.md) 와
> [auth-admin-forced-logout-closeout.md](../../auth/auth-admin-forced-logout-closeout.md) 를 우선 기준으로 봅니다.


## 결정

future `admin forced logout` helper/service의 1차 이름은 **`UserSessionRevocationService`** 로 둔다.

이번 phase에서 선택하지 않는 이름은:

- `AdminForcedLogoutGuard`
- `AccessSessionCutoffService`

이다.

즉 현재 네이밍 분리는 아래처럼 가져간다.

- `AccessTokenRevocationService`
  - exact token blacklist
- `UserSessionRevocationService`
  - userKey 기준 session/token revoke + cutoff 판단

## 왜 `UserSessionRevocationService` 인가

### 1. admin API 진입점보다 실제 책임을 더 잘 설명한다

이 helper/service의 실제 책임은:

- 특정 `userKey` 의 refresh/access 세션 revoke intent 기록
- access token이 현재 auth gate에서 허용 가능한지 판단

이다.

즉 이 객체는 “admin controller 전용 guard” 라기보다 **user session revoke 정책 서비스** 에 가깝다.

`AdminForcedLogoutGuard` 로 부르면:

- admin API에서만 쓰는 것처럼 보이고
- read/write 둘 다 가진 service보다 단순 guard처럼 보인다

### 2. 기존 `AccessTokenRevocationService` 와 역할이 더 잘 갈린다

현재 이미 있는 [AccessTokenRevocationService.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/user/service/AccessTokenRevocationService.java) 는:

- presented bearer access token 1개 revoke
- exact token blacklist

만 담당한다.

새 helper는:

- userKey 기준 refresh delete
- access cutoff 기록
- legacy token reject
- cutoff 비교

를 담당하므로, 이름에서도 **token 1개** 와 **user session 단위** 를 분리하는 편이 낫다.

### 3. future 확장에도 더 맞다

나중에 같은 helper가:

- admin forced logout
- withdraw cutoff 재사용
- future account lock auth gate

같은 이벤트를 같이 볼 가능성이 있다.

이 경우 `AdminForcedLogoutGuard` 보다는 `UserSessionRevocationService` 가 scope를 더 자연스럽게 담는다.

## 왜 `AdminForcedLogoutGuard` 를 고르지 않는가

이 이름은 아래 오해를 만들기 쉽다.

- admin API 전용 컴포넌트처럼 보임
- filter read path보다 controller guard처럼 들림
- write/revoke intent 기록 책임이 약해 보임

현재 정책상 이 객체는:

- filter read path
- admin API write path

둘 다 받으므로 `Guard` 보다는 `Service` 가 더 맞다.

## 왜 `AccessSessionCutoffService` 를 고르지 않는가

이 이름은 기술적으로는 가깝지만, 현재 phase에서 핵심 의미인:

- revoke
- refresh delete
- session invalidation

보다 `cutoff` 구현 세부를 너무 전면에 둔다.

현재는 제품 의미를 기준으로 이름을 두는 편이 낫다.

## 권장 조합

현재 기준 권장 클래스 역할 분리는 아래다.

- `AccessTokenRevocationService`
  - logout/withdraw presented token exact revoke
- `UserSessionRevocationService`
  - forced logout session revoke + cutoff read/write

## next step

다음 작은 task는 이 이름 정책을 기준으로, `UserSessionRevocationService` 의 메서드명을 그대로 갈지 (`isAccessAllowed`, `revokeUserSessions`) 아니면 더 domain-specific 하게 바꿀지 정리하는 것이다.
