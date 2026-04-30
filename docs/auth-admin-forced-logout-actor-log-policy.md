# Admin Forced Logout Actor Log Policy

## 결정

현재 phase의 `admin forced logout` 로그 라인에는 **`actor` 를 추가하지 않는다**.

즉 현재 권장 로그 범위는 그대로:

```text
[Admin] forced logout 트리거 userKey=<userKey> cutoffMillis=<epochMillis>
```

로 유지한다.

`actor admin email/userKey`, `request id`, `source IP` 같은 추가 식별자는 future reopen 항목으로 남긴다.

## 왜 지금 `actor` 를 넣지 않는가

### 1. 현재 baseline의 핵심은 revoke correctness다

지금까지 구현/검증한 핵심은 아래다.

- old access token fail
- old refresh token fail
- relogin access pass
- legacy token `A006`

즉 현재 forced logout 1차 hardening의 본질은:

- **누가 눌렀는지** 보다
- **old/new token 경계가 맞게 끊기는지**

다.

현재 triage 최소 증적도:

- `userKey`
- `cutoffMillis`

면 충분하다.

### 2. actor를 넣는 순간 auth/audit 경계가 다시 커진다

`actor` 를 지금 추가하려면 곧바로 아래가 따라온다.

- 어떤 actor identifier를 쓸지
  - email
  - userKey
  - userId
- masking/redaction 기준
- 로그 보존 범위
- future audit storage와의 관계

이건 [auth-admin-forced-logout-audit-scope-policy.md](./auth-admin-forced-logout-audit-scope-policy.md) 에서 현재 제외한 “persistent audit” 쪽으로 바로 이어진다.

지금 단계에서 이걸 같이 열면 forced logout 1차 hardening 범위를 다시 넓히게 된다.

### 3. 현재 controller layer는 actor principal을 굳이 쓰지 않아도 된다

지금 [UserAdminController.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/user/controller/UserAdminController.java) 의 forced logout 경로는:

- `ROLE_ADMIN` 인가
- target `userKey`
- `cutoffMillis`

만 있으면 동작한다.

여기에 `@AuthenticationPrincipal` 을 끌어와 actor까지 로그에 남기기 시작하면:

- 로그 포맷
- admin identifier choice
- future test fixture

가 동시에 바뀐다.

현재는 운영자 진입점과 revoke correctness를 먼저 닫은 상태이므로, actor는 별도 reopen이 더 맞다.

## 현재 권장 로그 범위

### include

- target `userKey`
- `cutoffMillis`

### exclude

- actor email
- actor userKey
- request IP
- user-agent
- 별도 audit row id

## 왜 future reopen으로 남기는가

아래 요구가 생기면 `actor` 를 다시 연다.

1. incident review에서 “누가 강제 로그아웃을 눌렀는지”를 host log만으로도 바로 남겨야 함
2. 여러 admin이 같은 운영 경로를 공유해 actor 추적이 실제 운영 요구가 됨
3. forced logout action history API/UI가 필요해짐

그 전까지는:

- current log line
- Redis cutoff
- integration baseline

으로 충분하다고 본다.

## next step

다음 작은 task는 `actor` 를 바로 구현하기보다, current phase에서 남은 forced logout 운영 보강 항목이 더 있는지 보고 없으면 이 트랙을 닫고 다른 pending으로 넘어가는 것이다.
