# Auth / Session Revoke 실행 체크리스트

관련 문서:

- [auth-session-revocation-current-state.md](./auth-session-revocation-current-state.md)
- [runtime-api-smoke-commands.md](./runtime-api-smoke-commands.md)
- [auth-admin-forced-logout-closeout.md](./auth-admin-forced-logout-closeout.md)

## 목적

이 문서는 auth/session revoke 경계를 실제로 확인할 때
무엇부터 보고 어떤 순서로 판정할지 정리한 짧은 runbook 입니다.

## 1. 먼저 구분할 것

지금 확인하려는 경계가 무엇인지 먼저 정합니다.

- `logout`
- `withdraw`
- `admin allowlist revoke`
- `admin forced logout`

이 네 가지는 이름이 비슷하지만 계약이 다릅니다.

## 2. logout 확인

엔드포인트:

- `POST /api/auth/logout`

체크:

- refresh cookie 삭제
- bearer access token을 실었다면 exact revoke

확인 포인트:

- 직후 refresh 재호출: `401`
- logout 요청에 실린 old access 재사용: `401 / A006`

주의:

- cookie-only logout 경로는 refresh 회수만 보장
- bearer 없는 경우 old access 즉시 차단 기대를 섞지 않음

## 3. withdraw 확인

엔드포인트:

- `DELETE /api/users/me`

체크:

- refresh key 삭제
- presented access revoke
- withdrawn 상태 반영

확인 포인트:

- old access 재사용: `401 / A006`
- stale refresh 재사용: `410 / U003`

## 4. admin allowlist revoke 확인

이건 forced logout 이 아닙니다.

현재 의미:

- `SECURITY_ADMIN_EMAILS` 에서 대상 제거
- 앱 재기동

확인 포인트:

- old admin access token: 즉시 살아 있을 수 있음
- old refresh token: 바로 invalidation 안 될 수 있음
- 새 refresh 이후 발급된 access token: `ROLE_ADMIN` 빠짐

즉 이 경계는:

- 즉시 세션 회수
가 아니라
- future role issuance 변경

입니다.

## 5. admin forced logout 확인

엔드포인트:

- `POST /api/admin/users/forced-logout`

체크:

- `refresh:{userKey}` 삭제
- `access-cutoff:{userKey}` 기록
- old access 즉시 차단
- old refresh 즉시 차단
- relogin 후 fresh access 허용

확인 포인트:

- API 응답 `success=true`
- old access: `401 / A006`
- old refresh: `401 / A003` 계열
- relogin 후 보호 API: `200`

## 6. legacy admin token 확인

forced logout 보호 경계에서
`iatm` 없는 legacy admin access token은:

- `401 / A006`

으로 본다.

즉 invalid format 이 아니라
이 보호 경계에서 더 이상 허용되지 않는 세션으로 해석합니다.

## 7. 운영 증적 확인

forced logout 현재 증적:

- response: `userKey`, `accepted`
- server log:

```text
[Admin] forced logout 트리거 userKey=<userKey> cutoffMillis=<epochMillis>
```

현재는 actor audit 까지는 안 봅니다.

## 8. 여기서 멈춰야 하는 경우

- allowlist revoke 와 forced logout을 같은 것으로 해석하는 경우
- cookie-only logout에 old access revoke 기대를 섞는 경우
- legacy token `A006` 을 `INVALID_TOKEN` 으로 오해하는 경우

## 9. 실행 후 남길 최소 기록

- 어떤 경계를 본 것인지
- endpoint / command
- old access 결과
- old refresh 결과
- relogin 결과
- 로그/Redis 증적
- 다음 액션

## 요약

1. logout, withdraw, allowlist revoke, forced logout은 서로 다른 계약입니다.
2. logout/withdraw는 presented access revoke 중심입니다.
3. allowlist revoke는 future role issuance 변경입니다.
4. forced logout은 user session revoke + cutoff 경계입니다.
