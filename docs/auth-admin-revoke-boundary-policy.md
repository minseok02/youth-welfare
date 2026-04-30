# Admin Revoke Boundary Policy

## 결정

현재 phase에서 운영 admin 권한 회수의 기본 경로는 **`SECURITY_ADMIN_EMAILS` 변경 + 앱 재기동** 이다.

즉, 지금 있는 revoke 수단은 아래 두 층으로 분리해서 본다.

1. **role grant/revoke**
   - `SECURITY_ADMIN_EMAILS` allowlist
   - 앱 재기동 후 새 로그인/refresh/access token부터 반영
2. **token/session revoke**
   - `logout` presented token revoke
   - future `admin forced logout` / `account lock` hardening 후보

현재는 `ROLE_ADMIN` 자체를 런타임에서 즉시 회수하는 별도 DB/table/API는 없다.

## 이유

### 1. 현재 admin role source of truth가 config다

관리자 권한은 DB role table이 아니라 `SECURITY_ADMIN_EMAILS` 와 사용자 row 이메일 조합으로 계산된다.

따라서 운영에서 “이 사람은 이제 admin이 아니다”를 현재 구조로 가장 정확하게 표현하는 방법은:

1. allowlist에서 제거
2. 앱 재기동

이다.

### 2. forced logout과 role revoke는 같은 문제가 아니다

`admin forced logout` 은 이미 발급된 token/session을 회수하는 문제이고, `allowlist revoke` 는 앞으로 새 token에 `ROLE_ADMIN` 을 싣지 않게 하는 문제다.

이 둘을 섞으면 아래가 함께 흔들린다.

- 현재 token 즉시 차단
- refresh 후 권한 재주입 금지
- 재로그인 후 권한 제거
- 운영 절차상 offboarding

그래서 현재는 **role revoke baseline** 과 **forced logout baseline** 을 분리한다.

### 3. stale config와 stale token을 각각 봐야 한다

admin 회수 이슈는 보통 두 가지로 나뉜다.

- allowlist를 바꿨는데 앱을 재기동하지 않아 stale config가 남음
- allowlist는 이미 바뀌었지만 예전 access/refresh token이 살아 있음

이 둘은 원인과 대응이 다르다.

## 현재 제품/운영 계약

- admin 권한 부여
  - `SECURITY_ADMIN_EMAILS` 포함
  - 사용자 row 존재
  - 앱 재기동 후 login/refresh
- admin 권한 회수
  - `SECURITY_ADMIN_EMAILS` 에서 제거
  - 앱 재기동
  - 필요 시 기존 token/session은 별도 revoke hardening 대상

## current gap

현재 구조에서 allowlist 제거 + 앱 재기동만으로 바로 해결되지 않는 부분은 이것이다.

- 이미 발급된 admin access token
- 이미 저장된 refresh token
- re-login 전까지 남아 있는 session semantics

이건 future `admin forced logout baseline` 에서 따로 측정한다.

## next step

다음 작은 task는 `admin forced logout` 구현이 아니라, 아래 baseline 중 무엇을 먼저 고정할지 정하는 것이다.

1. allowlist 제거 + 앱 재기동 후 old admin token이 어디까지 통과하는지
2. allowlist 제거 후 refresh가 새 `ROLE_ADMIN` 없이 재발급되는지

즉, 먼저 “config-based admin revoke의 현재 한계”를 측정하고, 그 다음에야 forced logout을 연다.
