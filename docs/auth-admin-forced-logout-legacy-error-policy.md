# Admin Forced Logout Legacy Token Error Policy

> Status note (2026-05-01)
>
> 이 문서는 현재 구현의 배경을 남긴 **design history** 입니다.
> 현재 제품/코드 계약은 [auth-session-revocation-current-state.md](./auth-session-revocation-current-state.md) 와
> [auth-admin-forced-logout-closeout.md](./auth-admin-forced-logout-closeout.md) 를 우선 기준으로 봅니다.


## 결정

future `admin forced logout` 보호 경계에서 `iatm` 없는 legacy admin access token은 **`401 / A006`** 으로 통일한다.

즉 현재 phase의 계약은:

- legacy admin access token
  - forced logout protected path 진입 시
  - `401 Unauthorized`
  - `errorCode=A006`

이다.

`A001 INVALID_TOKEN` 이나 forced-logout 전용 신규 에러 코드는 이번 단계에서 쓰지 않는다.

## 이유

### 1. 제품 의미는 “형식 오류”보다 “더 이상 인증된 세션으로 보지 않음”에 가깝다

legacy admin access token은 서명 자체가 깨졌거나 임의 변조된 토큰이 아니라:

- forced logout 보호 경계에서 필요한 새 계약(`iatm`)을 만족하지 않는 토큰

이다.

이 경우 사용자/운영자가 받아들여야 할 제품 의미는:

- “이 토큰은 더 이상 이 보호 경계에서 인증된 세션으로 인정되지 않는다”

에 가깝다.

그래서 `INVALID_TOKEN` 보다는 `UNAUTHORIZED` 가 더 맞다.

### 2. 현재 revoke 계열 보호 API 차단도 이미 `A006` 으로 수렴한다

현재 baseline은 이렇다.

- logout 후 revoke된 old access token
  - `401 / A006`
- withdraw에 사용된 old access token
  - `401 / A006`

forced logout도 같은 “보호 API 접근 차단” 계열이므로, 여기만 `A001` 이나 별도 코드로 갈라지면 운영/테스트/문서가 더 복잡해진다.

### 3. 새 에러 코드를 지금 열 이유가 약하다

별도 forced-logout-specific 에러 코드를 만들면:

- exception mapping
- 문서/클라이언트 분기
- 테스트 기준

이 다 같이 늘어난다.

하지만 현재 필요한 제품 의미는 세밀한 분기보다:

- 재로그인 필요
- 이 토큰으로는 더 이상 보호 API 접근 불가

를 일관되게 주는 것이다.

## current auth code와의 정렬

현재 인증/인가 경계에서:

- malformed/parse failure
  - `A001 INVALID_TOKEN`
- expired token
  - `A002 EXPIRED_TOKEN`
- 보호 경계에서 인증되지 않은 접근
  - `A006 UNAUTHORIZED`

가 이미 나뉘어 있다.

legacy admin access token on forced-logout path는:

- parse는 가능
- expiry와도 별개일 수 있음
- 하지만 보호 경계 인증 조건은 불충족

이므로 `A006` 으로 다루는 편이 현재 에러 taxonomy와 더 잘 맞는다.

## 운영 계약

운영자/개발자 해석은 아래처럼 둔다.

- `A001`
  - 토큰 자체가 잘못됨
- `A002`
  - 만료됨
- `A006`
  - 현재 보호 경계에서 더 이상 인증 세션으로 인정되지 않음
  - 예: revoke, forced logout cutoff, legacy token not accepted

즉 forced logout rollout 이후 legacy admin access token의 핵심 메시지는 “토큰 형식이 이상하다”가 아니라 “재로그인 필요”다.

## next step

다음 작은 task는 이 정책을 기준으로, future implementation/test에서:

- `JwtAuthenticationFilter` 에서 바로 `A006` 으로 보낼지
- 별도 forced-logout guard helper가 `CustomException(ErrorCode.UNAUTHORIZED)` 를 던질지

구현 위치를 정리하는 것이다.
