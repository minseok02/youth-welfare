# Auth Revocation Reopen Order

## 결정

user-level revocation/cutoff를 다시 열어야 한다면 우선순위는 아래 순서로 본다.

1. `withdraw` user-level revoke
2. future `admin forced logout / account lock` revoke
3. generic `cookie-only logout` user-level cutoff

즉, 다음 hardening 후보는 “브라우저 logout 전체 세션 회수”가 아니라 **더 강한 보안 이벤트** 인 `withdraw` 와 `관리자 강제 로그아웃` 이다.

## 이유

## 1. `withdraw` 는 terminal state다

회원탈퇴는 단순 브라우저 세션 종료가 아니라 계정 자체를 비활성/폐기 상태로 바꾸는 이벤트다.

현재 `UserService.withdraw(...)` 는 아래를 수행한다.

- 비밀번호 검증
- 관심사/우선순위 정리
- 챗 세션 정리
- `user.withdraw()` 로 withdrawn state 반영

하지만 현재 phase 기준 access token revoke는 `logout` 의 presented bearer token에만 붙어 있다. 따라서 “탈퇴한 계정의 기존 access token을 전부 더 빨리 막아야 한다”는 요구가 생기면, generic logout보다 `withdraw` 를 먼저 여는 편이 맞다.

## 2. `admin forced logout` 은 운영 보안 이벤트다

관리자 강제 로그아웃/계정 잠금은 탈취 대응, 권한 회수, 운영자 offboarding 같은 보안 요구와 직접 연결된다. 이 경로는 “사용자가 브라우저에서 로그아웃 버튼을 눌렀다”는 상황보다 강한 이벤트이므로, user-level cutoff가 필요해져도 generic logout보다 먼저 reopen할 가치가 있다.

## 3. generic `cookie-only logout` 은 UX/세션 의미가 더 섞여 있다

refresh cookie만 가진 브라우저 logout을 전 세션 revoke로 넓히면 아래 의미가 한 번에 섞인다.

- 현재 브라우저 세션 종료
- 다른 디바이스 세션 종료
- mobile/web 동시 세션 종료
- 재로그인 직후 old/new token 경계

이건 보안 이벤트라기보다 세션 모델 재정의에 가깝다. 그래서 우선순위를 가장 뒤로 둔다.

## 현재 정책과의 연결

- 현재 유지:
  - `bearer-present logout = exact token revoke`
  - `cookie-only logout = refresh-only`
- future reopen 우선순위:
  - `withdraw`
  - `admin forced logout`
  - generic `cookie-only logout`

## reopen 조건 예시

### 1순위: `withdraw`

- 탈퇴 직후 old access token의 보호 API 접근을 즉시 전부 막아야 함
- 탈퇴 후 background action 차단을 stronger하게 요구함

### 2순위: `admin forced logout`

- 운영자가 특정 사용자/관리자 세션을 즉시 회수해야 함
- account lock / incident response / offboarding 절차가 필요해짐

### 3순위: generic `cookie-only logout`

- “로그아웃 버튼 = 모든 디바이스 세션 종료”를 제품 계약으로 올림
- multi-device sign-out 요구가 명시적으로 들어옴

## 구현 reopen 시 주의

이 순서를 다시 열 때도 `logoutAt/revokedAfter` 를 한 번에 공용 도입하지 말고, event scope를 먼저 분리한다.

- `withdraw` 전용 cutoff
- `admin forced logout` 전용 cutoff
- generic session model cutoff

이렇게 나눠야 실패 반경과 제품 의미를 각각 검증할 수 있다.
