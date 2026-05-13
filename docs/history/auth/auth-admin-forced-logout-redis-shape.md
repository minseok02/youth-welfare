# Admin Forced Logout Redis Shape

> Status note (2026-05-01)
>
> 이 문서는 현재 구현의 배경을 남긴 **design history** 입니다.
> 현재 제품/코드 계약은 [auth-session-revocation-current-state.md](../../auth/auth-session-revocation-current-state.md) 와
> [auth-admin-forced-logout-closeout.md](../../auth/auth-admin-forced-logout-closeout.md) 를 우선 기준으로 봅니다.


## 결정

future `admin forced logout` 의 Redis revoke state는 아래 **두 층** 으로 둔다.

1. `refresh:{userKey}` 삭제
2. `access-cutoff:{userKey}` 기록

즉 forced logout은 logout처럼 presented access token 1개만 `access-revoked:{token}` 으로 넣는 방식이 아니라, **특정 userKey 기준 access cutoff + refresh key 제거** 조합으로 본다.

## key shape

### 1. refresh revoke

- key
  - `refresh:{userKey}`
- action
  - delete

이건 현재 refresh rotation/logout/withdraw 경계와 같다.

### 2. access cutoff

- key
  - `access-cutoff:{userKey}`
- value
  - cutoff epoch millis 문자열

예:

```text
access-cutoff:usr_123 = 1777588800000
```

의미는:

- 이 시점 **이전** 에 발급된 access token은 revoke 대상으로 본다

## 왜 `access-revoked:{token}` 를 기본으로 두지 않는가

logout은 현재 요청에 실린 bearer token 1개만 즉시 막으면 되므로 exact token blacklist가 맞다.

하지만 `admin forced logout` 은 운영자가 **대상 user의 기존 세션 전체** 를 정리하려는 이벤트다. 이 경우:

- 운영자는 old access token 원문을 모른다
- 여러 device/session이 동시에 있을 수 있다
- presented token 1개 revoke만으로는 범위가 부족하다

따라서 forced logout의 access 차단은 `userKey cutoff` 가 더 자연스럽다.

## filter/read path 의미

future auth filter는 access token 검증 시 아래 순서로 본다.

1. 기존 exact token revoke
   - `access-revoked:{token}`
2. future forced logout cutoff
   - `access-cutoff:{userKey}`
   - token `iat` 또는 발급시각이 cutoff 이전이면 reject

즉:

- logout
  - exact token revoke
- forced logout
  - userKey cutoff

로 역할을 분리한다.

## TTL

### `refresh:{userKey}`

- 별도 TTL 없음
- 그냥 delete

### `access-cutoff:{userKey}`

- 기본 TTL
  - **access token max lifetime보다 길게**

현재 phase의 원칙은 “최소한 forced logout 이전에 발급된 access token이 자연 만료될 때까지는 cutoff가 살아 있어야 한다” 이다.

따라서 cutoff TTL은 최소한:

- 현재 access token 최대 수명

이상이어야 한다.

문서 기본값은:

- `access token max lifetime + small safety margin`

으로 둔다.

구체 시간 상수는 실제 구현 task에서 `JwtUtil` access 만료 설정과 함께 맞춘다.

## clear 조건

### 자동 clear

- TTL 만료 시 자연 삭제

### 수동 clear를 지금 넣지 않는 이유

`forced logout` 은 account lock이 아니다.  
하지만 그렇다고 해서 별도 “forced logout clear API” 를 지금 같이 열면:

- relogin 허용 시점
- old/new token 경계
- admin offboarding과 temporary incident response

가 다시 한꺼번에 섞인다.

현재 phase에서는:

- forced logout
  - revoke intent 기록
- 이후 fresh login
  - 새 token은 cutoff 이후 발급되므로 통과 가능

라는 구조만 먼저 유지한다.

즉 cutoff key는 명시적으로 지우지 않아도, **새 token의 발급시각이 cutoff 이후면 자연스럽게 통과** 해야 한다.

## fresh login과의 관계

중요한 점은 `access-cutoff:{userKey}` 가 있다고 해서 그 user의 모든 future access token이 영구 차단되면 안 된다는 것이다.

의미는:

- cutoff 이전 token
  - reject
- cutoff 이후 새로 발급된 token
  - allow

이다.

따라서 구현 때는 token 발급시각과 cutoff 비교가 핵심이고, 단순 key 존재 여부만으로 막으면 안 된다.

## current phase에서 제외하는 것

아래는 이번 Redis shape에 넣지 않는다.

- DB persistent revoke table
- multi-cutoff history
- per-device session id
- explicit “clear forced logout” key

## next step

다음 작은 task는 이 Redis shape를 기준으로 **JWT claim/발급시각 비교 방식** 을 문서로 고정하는 것이다.

즉:

- `iat` 로 충분한지
- millis precision이 필요한지
- existing `JwtUtil` 계약에서 무엇을 같이 바꿔야 하는지

를 먼저 정리하면 된다.
