# Withdraw Revocation Next Step

## 결정

`withdraw` 를 다음 access-token revoke 후보로 보더라도, **다음 액션은 즉시 구현이 아니라 baseline smoke/inventory 확보** 로 둔다.

순서는 아래와 같다.

1. `withdraw` 직전 발급된 old access token이 탈퇴 후에도 어떤 보호 API에 계속 통과하는지 smoke로 고정
2. 그 결과를 기준으로 `withdraw` 전용 revoke scope를 설계
3. 그 다음에만 실제 cutoff/revoke 구현을 연다

즉, 현재 단계의 next step은 `withdraw revoke 구현` 이 아니라 `withdraw old access-token baseline smoke` 다.

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

## 현재 권장 next task

다음 작은 task는 아래를 문서/테스트 기준으로 고정하는 것이다.

- `withdraw` 전 old access token 확보
- `DELETE /api/users/me` 후 same token으로 재호출할 보호 API 선정
- 기대 baseline 응답 기록

예: `GET /api/users/me/bookmarks` 또는 profile/priority 계열 보호 API

## 구현은 언제 여는가

아래가 확보된 뒤에만 실제 `withdraw` revoke 구현을 연다.

- baseline smoke 결과
- revoke 대상 API 범위
- `withdraw` 전용 revoke가 generic logout/session 의미를 건드리지 않는다는 확인
