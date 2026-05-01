# Logout Revocation Scope Policy

## 결정

현재 phase에서는 `logout` 의 access-token 즉시 무효화 범위를 **logout 요청에 실제로 실린 bearer access token 1개**로 제한한다.

- `Authorization: Bearer <access-token>` 이 함께 온 `POST /api/auth/logout`
  - refresh token 회수
  - 해당 bearer access token 즉시 revoke
- refresh cookie/header만 있는 `cookie-only logout`
  - refresh token 회수만 보장
  - 이미 발급된 access token 전체 cutoff는 하지 않음

즉, `cookie-only logout` 은 계속 `refresh-only contract` 로 유지한다.

## 이유

### 1. 현재 구현 범위를 가장 작은 diff로 닫는다

이번 단계의 목표는 “logout에 사용한 현재 access token이 바로 다시 보호 API를 통과하지 않게 하는 것”이다. exact token blacklist는 이 요구를 가장 짧게 만족한다.

### 2. user-level cutoff는 별도 설계 이슈가 남아 있다

`logoutAt` / `revokedAfter` 같은 user-level cutoff로 넓히려면 아래를 추가로 정해야 한다.

- JWT `iat` 비교 정밀도
- 동시 다중 로그인/session 기대치
- 재로그인 직후 old/new token 경계
- Redis/DB cutoff source of truth
- admin/manual revoke 범위

이건 현재의 “presented token 즉시 차단”보다 범위가 크다.

### 3. 브라우저 cookie logout과 API bearer logout의 의미를 분리해야 한다

브라우저는 refresh cookie만으로도 logout을 호출할 수 있다. 이 경로까지 조용히 user-level cutoff로 확장하면, 현재 요청에 어떤 access token이 살아 있었는지 보지 못한 채 더 넓은 세션 의미를 바꾸게 된다.

## 현재 제품 계약

- `logout success`
  - refresh cookie clear
  - refresh 재발급 차단
- `logout + bearer access token present`
  - 같은 bearer token 재사용 차단
- `logout without bearer access token`
  - current access token 즉시 차단은 보장하지 않음

## reopen 조건

아래 중 하나가 생기면 user-level cutoff를 별도 task로 다시 연다.

- 다중 access token 동시 회수 요구
- mobile/web 동시 세션 정리 요구
- admin 강제 로그아웃/계정 잠금 요구
- 탈취 대응으로 “user 단위 revoke-after” 가 필요해짐

그 전까지는 `cookie-only logout = refresh-only`, `bearer-present logout = exact token revoke` 경계를 유지한다.
