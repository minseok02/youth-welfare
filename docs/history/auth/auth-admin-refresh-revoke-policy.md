# Admin Refresh Revoke Policy

## 결정

현재 phase에서는 `SECURITY_ADMIN_EMAILS` 에서 admin 이메일을 제거해도, **기존 refresh token 자체를 즉시 invalidation 대상으로 보지 않는다**.

대신 현재 계약은 아래와 같다.

- old admin access token
  - 만료 전까지 기존 role claim 유지 가능
- old refresh token
  - 재발급 자체는 허용
  - 하지만 새 access token부터는 `ROLE_ADMIN` 없이 발급

즉, allowlist 제거의 의미는 **refresh token 즉시 차단** 이 아니라 **future token issuance에서 admin role 제거** 다.

## 이유

### 1. role revoke와 token revoke를 섞지 않는다

현재 admin 회수의 기본 source of truth는 `SECURITY_ADMIN_EMAILS` 다.  
이 값이 바뀌면 `AuthService.refresh(...)` 와 login 시점 role 계산도 바뀐다.

따라서 allowlist 제거만으로 지금 당장 달라져야 하는 최소 계약은:

- 새로 발급되는 token에 `ROLE_ADMIN` 을 싣지 않는 것

이다.

여기에 기존 refresh token 즉시 폐기까지 섞으면, role revoke와 forced logout/session revoke가 다시 한 경로로 합쳐진다.

### 2. 이미 current baseline이 그렇다

`AdminSecurityIntegrationTest` baseline 기준 현재 동작은 이렇다.

- old admin access token은 계속 admin API 통과
- old refresh token으로 재발급한 새 access token부터 `ROLE_ADMIN` 제거
- 새 token의 admin API 호출은 `403 / C003`

즉 지금 구조는 “refresh token은 살아 있지만 admin role은 새로 주입되지 않는다”는 방향으로 이미 수렴해 있다.

### 3. 운영 의미도 이쪽이 더 단순하다

운영에서 allowlist 제거는 “이 사람을 앞으로 admin으로 보지 않는다”는 뜻이다.  
그 의미를 가장 작게 반영하는 방법은 기존 refresh 자체를 끊는 것이 아니라, refresh 결과물에서 admin role을 빼는 것이다.

즉:

- config revoke: future role issuance 차단
- forced logout: existing token/session 차단

으로 분리하는 편이 더 명확하다.

## 현재 운영 계약

### allowlist 제거 후 기대값

- old access token: 기존 claim 유지 가능
- old refresh token: 사용 가능
- refreshed access token: `ROLE_ADMIN` 없음
- refreshed access token의 admin API: `403 / C003`

### 즉시 차단이 필요한 경우

아래 요구가 있으면 allowlist 제거만으로는 부족하고, 별도 `admin forced logout` 경로를 열어야 한다.

- 기존 refresh token도 바로 unusable 해야 함
- 기존 access token도 바로 unusable 해야 함
- incident response / offboarding / 탈취 대응

## reopen 조건

다음 중 하나가 생기면 `old admin refresh token 즉시 차단` 을 별도 hardening으로 다시 연다.

- 운영자가 allowlist 제거만으로 기존 refresh도 즉시 막히길 기대함
- admin offboarding 절차에서 refresh 재발급도 허용하면 안 됨
- `admin forced logout` 기능을 실제 구현하기로 확정

그 전까지는 현재 정책을 유지한다.
