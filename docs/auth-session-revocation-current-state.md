# Auth Session Revocation Current State

관련 코드:

- [AuthService.java](../backend/src/main/java/com/example/welfare/user/service/AuthService.java)
- [UserService.java](../backend/src/main/java/com/example/welfare/user/service/UserService.java)
- [AccessTokenRevocationService.java](../backend/src/main/java/com/example/welfare/user/service/AccessTokenRevocationService.java)
- [UserSessionRevocationService.java](../backend/src/main/java/com/example/welfare/user/service/UserSessionRevocationService.java)
- [JwtAuthenticationFilter.java](../backend/src/main/java/com/example/welfare/global/config/JwtAuthenticationFilter.java)
- [JwtUtil.java](../backend/src/main/java/com/example/welfare/global/util/JwtUtil.java)
- [UserAdminController.java](../backend/src/main/java/com/example/welfare/user/controller/UserAdminController.java)

관련 문서:

- [auth-logout-revocation-scope-policy.md](./history/auth/auth-logout-revocation-scope-policy.md)
- [auth-admin-revoke-boundary-policy.md](./history/auth/auth-admin-revoke-boundary-policy.md)
- [auth-admin-refresh-revoke-policy.md](./history/auth/auth-admin-refresh-revoke-policy.md)
- [auth-admin-forced-logout-closeout.md](./auth-admin-forced-logout-closeout.md)

## 목적

현재 구현된 auth/session revoke 동작을 한 문서에서 바로 확인할 수 있게 정리합니다.

이 문서는 **현재 source of truth 요약** 입니다.  
세부 설계 배경은 개별 `auth-admin-forced-logout-*` 문서를 보되, 구현 상태 판단은 이 문서와 실제 코드를 우선합니다.

## 현재 구현 상태 요약

현재 로컬/코드 기준으로 정리하면 아래 네 경계가 살아 있습니다.

1. `logout`
2. `withdraw`
3. `admin allowlist revoke`
4. `admin forced logout`

## 1. logout

엔드포인트:

- `POST /api/auth/logout`

현재 계약:

- refresh token 삭제
- 요청에 `Authorization: Bearer ...` 가 같이 실리면 그 access token exact revoke
- bearer 없이 cookie-only logout 이면 refresh 회수만 보장

구현 위치:

- `AuthService.logout(...)`
- `AuthService.logoutByUserKey(...)`
- `AuthService.logoutByRefreshToken(...)`
- `AccessTokenRevocationService.revoke(...)`

에러/후속 동작:

- old refresh 재사용: `401 / A001` 또는 refresh invalidation 계열로 실패
- logout 요청에 실렸던 old access token 재사용: `401 / A006`

## 2. withdraw

엔드포인트:

- `DELETE /api/users/me`

현재 계약:

- refresh key 삭제
- 요청에 실린 bearer access token exact revoke
- user 상태 withdrawn 반영
- 채팅/속성/우선순위 정리

구현 위치:

- `UserService.withdraw(...)`

에러/후속 동작:

- 탈퇴에 사용한 old access token 재사용: `401 / A006`
- stale refresh token 재사용: `410 / U003`

## 3. admin allowlist revoke

여기서 말하는 revoke 는 **forced logout이 아니라**:

- `SECURITY_ADMIN_EMAILS` 에서 대상 이메일 제거
- 앱 재기동

입니다.

현재 계약:

- 이미 발급된 old admin access token: 즉시 회수되지 않음
- 기존 refresh token: 바로 invalidation 되지 않음
- 다만 그 refresh로 새 access token을 받으면 `ROLE_ADMIN` 이 빠짐

즉 현재 allowlist revoke 의 의미는:

- `future token issuance에서 admin role 제거`

이지

- `existing session/token 즉시 차단`

이 아닙니다.

관련 코드 경계:

- `AuthService.resolveRoles(...)`
- `AuthService.initAdminEmails()`

## 4. admin forced logout

엔드포인트:

- `POST /api/admin/users/forced-logout`

request body:

```json
{
  "userKey": "..."
}
```

현재 계약:

- 특정 `userKey` 의 refresh token 즉시 삭제
- 특정 `userKey` 의 access cutoff 기록
- old access token 즉시 차단
- old refresh token 즉시 차단
- relogin 후 fresh access token 은 정상 허용

구현 위치:

- write path: `UserAdminController.forceLogoutUserSessions(...)`
- revoke state write/read: `UserSessionRevocationService`
- auth gate: `JwtAuthenticationFilter`

## Redis key shape

### exact access revoke

- `access-revoked:{token}`

용도:

- logout/withdraw 에서 요청에 실린 bearer token 1개 즉시 차단

### refresh revoke

- `refresh:{userKey}`

용도:

- logout/withdraw/forced logout 시 refresh session 회수

### forced logout cutoff

- `access-cutoff:{userKey}`

값:

- epoch millis 문자열

용도:

- 해당 시점 이전에 발급된 access token 차단

## JWT 현재 계약

현재 access token 은 `iatm` claim 을 가집니다.

- access token: `iatm` 포함
- refresh token: `iatm` 없음
- notification token: `iatm` 없음

관련 helper:

- `JwtUtil.getIssuedAtMillis(...)`
- `JwtUtil.getIssuedAtMillisAllowExpired(...)`

forced logout 경계에서는:

- `iatm` 없는 legacy admin access token
  - `401 / A006`

로 처리합니다.

## 현재 A006 이 나오는 대표 경계

1. logout 후 presented old access token 재사용
2. withdraw 후 presented old access token 재사용
3. forced logout 이후 old access token 재사용
4. forced logout 보호 경계에서 `iatm` 없는 legacy admin access token 사용

## 운영 증적 현재 계약

forced logout response:

- `userKey`
- `accepted=true`

forced logout 로그:

```text
[Admin] forced logout 트리거 userKey=<userKey> cutoffMillis=<epochMillis>
```

현재는 로그에 `actor` 를 넣지 않습니다.

## 무엇이 design history 문서인가

아래 문서들은 구현 전후의 설계 결정을 쪼개서 남긴 기록입니다.

- `auth-admin-forced-logout-api-contract.md`
- `auth-admin-forced-logout-entrypoint-policy.md`
- `auth-admin-forced-logout-redis-shape.md`
- `auth-admin-forced-logout-issued-at-policy.md`
- `auth-admin-forced-logout-jwt-helper-policy.md`
- `auth-admin-forced-logout-helper-interface.md`
- `auth-admin-forced-logout-service-structure-policy.md`
- 그 외 `auth-admin-forced-logout-*`

이 문서들은 왜 현재 구현이 이렇게 되었는지 설명하는 **design history** 로 읽고,
현재 제품/코드 계약 확인은 이 문서와 실제 코드 기준으로 봅니다.

## 지금 기준의 간단한 결론

1. `logout` 은 refresh 회수 + bearer-present exact access revoke 입니다.
2. `withdraw` 는 refresh 회수 + presented access revoke + withdrawn state 반영까지 구현돼 있습니다.
3. `admin allowlist revoke` 는 즉시 forced logout 이 아니라 future role issuance 변경입니다.
4. `admin forced logout` 는 이미 구현돼 있고, `POST /api/admin/users/forced-logout` + Redis cutoff + `JwtAuthenticationFilter` gate + `iatm` 계약까지 현재 코드에 반영돼 있습니다.
