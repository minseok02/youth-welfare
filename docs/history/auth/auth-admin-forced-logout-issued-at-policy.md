# Admin Forced Logout Issued-At Policy

> Status note (2026-05-01)
>
> 이 문서는 현재 구현의 배경을 남긴 **design history** 입니다.
> 현재 제품/코드 계약은 [auth-session-revocation-current-state.md](../../auth-session-revocation-current-state.md) 와
> [auth-admin-forced-logout-closeout.md](../../auth-admin-forced-logout-closeout.md) 를 우선 기준으로 봅니다.


## 결정

future `admin forced logout` 의 access cutoff 비교는 **표준 JWT `iat` 만으로는 충분하지 않고**, access token에 **별도 millis precision issued-at claim** 을 추가하는 방향으로 고정한다.

즉 1차 정책은 아래와 같다.

- 표준 `iat`
  - 계속 유지
- forced logout cutoff 비교
  - custom millis claim 기준

권장 claim 이름은 문서 단계에서 `iatm` 으로 둔다.

## 이유

### 1. forced logout은 same-second 경계를 자주 밟는다

이 기능의 기대 흐름은 이렇다.

1. admin user가 로그인해 access token을 받는다
2. 운영자가 즉시 `forced logout` 을 건다
3. 같은 user가 바로 다시 로그인해 새 access token을 받는다

이때 old token과 new token이 **같은 초(second)** 안에 발급되면, 표준 `iat` 만으로는 두 토큰을 안전하게 가르기 어렵다.

forced logout cutoff는 “cutoff 이전 token reject / 이후 token allow” 경계가 핵심이므로, 정밀도가 초 단위면 too coarse 하다.

### 2. 현재 `JwtUtil` 은 `issuedAt(now)` 만 기록한다

현재 [JwtUtil.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/global/util/JwtUtil.java) 는:

- `issuedAt(now)`
- `expiration(...)`
- `uid`
- `roles`

만 기록한다.

즉 millis precision을 직접 꺼내 쓸 custom claim이 없다.

### 3. forced logout은 logout exact-token revoke보다 더 넓은 비교다

현재 logout revoke는 `access-revoked:{token}` exact blacklist라서 token string 하나만 보면 된다.

반면 forced logout은:

- `access-cutoff:{userKey}`
- token 발급시각

을 비교해야 한다.

그래서 “대충 같은 초인지”가 아니라 **엄밀한 before/after ordering** 이 필요하다.

## 왜 `iat` 단독 비교를 채택하지 않는가

아래 이유로 현재 phase에서는 `iat` only를 기본 계약으로 두지 않는다.

### 1. same-second relogin ambiguity

old token과 new token이 같은 second bucket이면:

- cutoff 직전 old token
- cutoff 직후 new token

이 같은 `iat` 값으로 보일 수 있다.

그 상태에서 비교식을:

- `< cutoff`
- `<= cutoff`

중 무엇으로 잡아도 한쪽에서 false positive/false negative가 생길 수 있다.

### 2. forced logout은 운영 incident 대응이므로 보수적으로 가야 한다

이 기능은 generic UX 기능이 아니라:

- offboarding
- incident response
- suspected compromise

경로다.

따라서 정밀도 부족으로 old/new token 경계가 흔들리는 설계는 피하는 편이 낫다.

## 권장 claim shape

access token에 아래 custom claim을 추가한다.

- claim
  - `iatm`
- type
  - epoch millis number

예:

```json
{
  "sub": "usr_...",
  "uid": 123,
  "roles": ["ROLE_USER", "ROLE_ADMIN"],
  "iat": 1777588800,
  "iatm": 1777588800123,
  "exp": 1777589700
}
```

## 비교 규칙

future filter 비교는 아래처럼 둔다.

- cutoff key
  - `access-cutoff:{userKey}`
- token issued-at
  - `iatm`
- rule
  - `token.iatm < cutoff` 이면 reject
  - `token.iatm >= cutoff` 이면 allow

정확한 `strict/non-strict` 연산자는 구현 직전 테스트와 함께 다시 확인할 수 있지만, 핵심은 **millis precision custom claim 기준 비교** 다.

## refresh token과의 관계

현재 forced logout 설계에서 refresh는:

- `refresh:{userKey}` delete

로 즉시 정리한다.

따라서 forced logout 경계에서 millis precision claim이 특히 중요한 쪽은 **access token** 이다.

refresh token은 key 존재 여부/일치 여부가 더 본질적이다.

## current phase에서 제외하는 것

이번 정책은 아래까지 같이 열지 않는다.

- DB persisted token version
- monotonic sequence per login
- per-device session id
- refresh token custom millis cutoff

## next step

다음 작은 task는 이 정책을 기준으로 [JwtUtil.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/global/util/JwtUtil.java) 에 어떤 helper를 추가해야 하는지, 즉:

- `iatm` claim write
- `iatm` claim read
- legacy token fallback 처리 여부

를 문서로 고정하는 것이다.
