# Admin Forced Logout Baseline Policy

> Status note (2026-05-01)
>
> 이 문서는 현재 구현의 배경을 남긴 **design history** 입니다.
> 현재 제품/코드 계약은 [auth-session-revocation-current-state.md](../../auth-session-revocation-current-state.md) 와
> [auth-admin-forced-logout-closeout.md](../../auth-admin-forced-logout-closeout.md) 를 우선 기준으로 봅니다.


## 결정

future `admin forced logout` 는 `SECURITY_ADMIN_EMAILS` 변경 기반 role revoke와 별도 기능으로 보고, 다음 구현 전에는 아래 **baseline/success criteria** 를 먼저 truth로 둔다.

1. 운영자가 특정 admin 사용자에 대해 **명시적 강제 로그아웃 액션** 을 실행한다
2. 그 시점 이전에 발급된 **old admin access token** 은 admin 보호 API에서 즉시 `401 / A006` 으로 막힌다
3. 같은 세션의 **old refresh token** 도 즉시 재발급에 실패한다
4. 이것만으로 사용자를 영구 비활성화하지는 않는다

즉 `admin forced logout` 의 제품 의미는 **existing token/session revoke** 이고, `allowlist revoke` 나 `account lock` 과 같은 의미로 합치지 않는다.

## 이유

### 1. current allowlist revoke는 future role issuance만 다룬다

현재 baseline은 이렇다.

- old admin access token
  - 계속 admin API 통과 가능
- old refresh token
  - 재발급 가능
  - 다만 새 access token부터 `ROLE_ADMIN` 제거

이건 “앞으로 admin role을 더 이상 싣지 않는다”는 계약이지, 기존 세션을 바로 회수하는 계약이 아니다.

### 2. forced logout은 access/refresh 둘 다 즉시 끊겨야 의미가 선다

운영 강제 로그아웃이 필요한 이유는 보통 아래다.

- admin offboarding
- token 탈취 의심
- incident response

이 경우 old access token만 막고 refresh를 남기면, 같은 사용자가 즉시 새 token을 다시 발급받을 수 있다. 반대로 refresh만 막고 old access를 남기면 만료 전까지 admin API 호출이 계속 가능하다.

따라서 baseline은 **old access immediate fail + old refresh immediate fail** 두 축이 같이 있어야 한다.

### 3. forced logout은 account lock과 다르다

`admin forced logout` 은 기존 세션을 정리하는 이벤트지, 사용자를 영구 차단하는 이벤트가 아니다.

따라서 이 baseline은 아래를 자동 포함하지 않는다.

- 로그인 자체 영구 금지
- 사용자 비활성화
- `ROLE_ADMIN` 영구 제거

이런 요구는 `allowlist revoke`, `withdraw`, `account lock` 같은 별도 경로로 본다.

## current baseline과의 대비

### current `allowlist revoke`

- trigger
  - `SECURITY_ADMIN_EMAILS` 변경 + 앱 재기동
- old access token
  - 계속 통과 가능
- old refresh token
  - 사용 가능
- new access token
  - `ROLE_ADMIN` 없음

### future `admin forced logout`

- trigger
  - 운영자 명시 액션
- old access token
  - 즉시 `401 / A006`
- old refresh token
  - 즉시 refresh 실패
- fresh login
  - 별도 account lock이 없다면 가능할 수 있음

## future smoke에서 증명할 것

future baseline/integration smoke는 최소한 아래를 분리해서 증명해야 한다.

1. `forced logout` 전 발급된 old admin access token이 admin API에서 즉시 막힌다
2. `forced logout` 전 발급된 old refresh token이 재발급에 실패한다
3. 이것이 `SECURITY_ADMIN_EMAILS` 변경/재기동 없이도 동작한다
4. 이것만으로 사용자의 일반 활성 상태나 영구 admin role source를 바꾸지 않는다

## next step

다음 작은 task는 구현이 아니라, **운영자 명시 액션의 진입점** 을 어디로 둘지 정하는 것이다.

예:

- admin API
- DB/Redis cutoff key
- 운영 콘솔/백오피스 command

즉 먼저 “무엇을 증명해야 하는가”를 고정했고, 그 다음에야 “어디서 어떻게 트리거할 것인가”를 연다.
