# Admin Forced Logout JWT Helper Policy

## 결정

future `admin forced logout` 구현을 위해 [JwtUtil.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/global/util/JwtUtil.java) 에 아래 helper/claim 계약을 추가하는 방향으로 고정한다.

1. access token 생성 시 `iatm` claim write
2. access token 읽기 시 `getIssuedAtMillis(...)` / `getIssuedAtMillisAllowExpired(...)` helper 추가
3. forced logout cutoff 비교에서는 **legacy access token fallback을 두지 않는다**

즉, forced logout은 old token compatibility보다 **new access token contract 명확화** 를 우선한다.

## write helper

### claim

- name
  - `iatm`
- meaning
  - token issued-at epoch millis

### scope

`iatm` 은 우선 **access token** 에만 필수로 넣는다.

- `generateAccessToken(...)`
  - 필수
- `generateRefreshToken(...)`
  - 현재 phase에서는 불필요
- `generateNotificationToken(...)`
  - 현재 phase에서는 불필요

이유는 forced logout cutoff 비교가 access token 쪽에만 직접 필요하기 때문이다.

## read helper

### 신규 helper

- `long getIssuedAtMillis(String token)`
- `long getIssuedAtMillisAllowExpired(String token)`

역할은 아래와 같다.

- validate된 token에서 `iatm` 을 읽는다
- expired token도 cutoff triage가 필요하면 allow-expired helper를 쓴다

### 반환 타입

반환 타입은 nullable보다 primitive `long` 기준으로 두는 편이 낫다.

`iatm` 이 없으면:

- `INVALID_TOKEN`
- 또는 forced logout protected path에서 explicit reject

로 보는 쪽을 기본 계약으로 둔다.

즉 helper가 “없으면 0 fallback” 같은 모호한 계약을 갖지 않게 한다.

## legacy token fallback을 두지 않는 이유

### 1. second precision fallback은 다시 경계를 흐린다

이미 [auth-admin-forced-logout-issued-at-policy.md](./auth-admin-forced-logout-issued-at-policy.md) 에서 정한 것처럼, 표준 `iat` 초 단위만으로는 same-second relogin 경계가 흔들린다.

그런데 `iatm` 이 없는 legacy token에서 다시:

- `iat * 1000`
- `issuedAt(Date)` millis

같은 fallback 을 허용하면, forced logout 경계가 다시 애매해진다.

### 2. forced logout은 future hardening 후보다

이 기능은 이미 발급된 legacy token을 최대한 오래 살리려는 기능이 아니라:

- offboarding
- incident response
- admin session hardening

경계다.

따라서 이 기능이 실제로 열리는 시점에는 “forced logout이 필요한 운영 이벤트 이후에 재로그인해 새 access token을 받는 것”이 더 중요하다.

### 3. refresh는 이미 userKey key-delete로 정리된다

forced logout 시 old refresh token은 `refresh:{userKey}` 삭제로 정리된다.

즉 가장 애매한 legacy fallback surface는 access token 뿐인데, 여기까지 느슨하게 두면 forced logout의 보안 의미가 약해진다.

## 구현 방향

### token write

`buildToken(...)` 에 공용으로 넣을지, access token 전용 helper에서 넣을지는 구현 task에서 정할 수 있다.  
다만 **제품 계약상 필수 대상은 access token** 이다.

### token read

forced logout filter/helper는:

1. userKey 추출
2. `iatm` 추출
3. `access-cutoff:{userKey}` 와 비교

순서로 본다.

## current phase에서 제외하는 것

이번 helper 정책은 아래까지 같이 열지 않는다.

- legacy access token에 대한 `iat` fallback 허용
- refresh token용 `iatm`
- notification token용 `iatm`
- 복수 claim alias

## next step

다음 작은 task는 이 helper 정책을 기준으로 **forced logout integration baseline에서 legacy token을 어떻게 다룰지**, 즉:

- 기능 on 이후 새 login token만 지원한다고 볼지
- rollout 직후 stale admin token은 재로그인 요구로 정리할지

를 문서로 고정하는 것이다.
