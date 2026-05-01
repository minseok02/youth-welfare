# Withdraw Revocation Next Step

## 상태

이 문서의 원래 next step인 `withdraw old access-token baseline smoke` 는 2026-05-01에 먼저 수행됐고, 같은 날 바로 `withdraw` hardening까지 반영됐다.

현재 상태는 아래와 같다.

1. baseline smoke 확보 완료
2. `withdraw` 시 presented bearer access token revoke 반영 완료
3. `withdraw` 시 refresh key 삭제 반영 완료
4. stale refresh token은 `AuthService.refresh(...)` 에서 `WITHDRAWN_USER` 로 차단

따라서 이 문서는 “왜 baseline을 먼저 잡았는가”를 남기는 기록으로 유지하고, 다음 후속 작업은 `admin forced logout` baseline 쪽으로 넘어간다.

## 이유

### 1. 현재 gap을 먼저 증적화해야 한다

로그아웃도 실제 구현 전에 `old access token after logout smoke` 를 먼저 남겼기 때문에, 이후 revoke 구현이 무엇을 바꿨는지 분리해서 설명할 수 있었다.

`withdraw` 도 같은 방식이 맞다.

- 탈퇴 직후 same token으로 어떤 API가 통과하는지
- `WITHDRAWN_USER`, `UNAUTHORIZED`, `ACCESS_DENIED` 중 어떤 응답이 나오는지
- `GET`/`POST` 보호 경로가 똑같이 막히는지

이 baseline이 먼저 있어야 나중에 revoke 구현이 “정책 변경”인지 “버그 수정”인지 경계가 선명해진다.

### 2. `withdraw` 는 revoke 외에도 상태 변화가 많다

현재 `UserService.withdraw(...)` 는 revoke만 건드리는 경로가 아니다.

- user attribute/priorities 삭제
- chat session 정리
- withdrawn state 반영
- core sync 반영

여기에 user-level cutoff까지 곧바로 넣으면, 실패 원인이 상태 masking 문제인지, 인증 필터 문제인지, revoke 문제인지 섞일 수 있다.

### 3. smoke 대상 API를 먼저 고르는 편이 안전하다

`withdraw` 이후 baseline을 볼 때는 최소한 아래 둘 중 하나를 고정해야 한다.

- 일반 보호 API
- admin이 아닌 사용자도 재현 가능한 사용자 전용 보호 API

즉 “어떤 old token이 어디까지 살아 있는가”를 먼저 측정하고 구현을 여는 편이 실패 반경이 작다.

## 결과

baseline 이후 실제 반영된 계약은 이렇다.

- `DELETE /api/users/me` 에 사용한 bearer access token
  - 즉시 revoke
  - 이후 보호 API에서 `401 / A006`
- `withdraw` 이전 refresh token
  - refresh key 삭제
  - stale token 재사용 시 `410 / U003`

## 다음 후속 작업

이제 revoke hardening의 다음 우선순위는 `withdraw` 가 아니라 [auth-revocation-reopen-order.md](./auth-revocation-reopen-order.md) 에 적어둔 `admin forced logout` baseline/inventory 쪽이다.
