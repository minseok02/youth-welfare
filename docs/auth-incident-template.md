# Auth / Session Revoke 기록 템플릿

관련 문서:

- [auth-session-revocation-current-state.md](./auth-session-revocation-current-state.md)
- [auth-operation-checklist.md](./auth-operation-checklist.md)

## 사용법

auth/session revoke 실행 결과나 이상 동작을 기록할 때 복사해서 씁니다.

권장 파일명 예시:

- `auth-check-<date>-logout.md`
- `auth-check-<date>-forced-logout.md`

---

## 1. 실행 정보

- date/time:
- environment:
- boundary:
  - `logout`
  - `withdraw`
  - `admin allowlist revoke`
  - `admin forced logout`
- command / endpoint:

## 2. 전제 상태

- userKey:
- admin 여부:
- access token 종류:
  - `current`
  - `legacy(no iatm)`
- refresh cookie/token 상태:

## 3. 결과 요약

- main response:
- old access reuse result:
- old refresh reuse result:
- relogin result:

## 4. 운영 증적

- server log line:
- redis key 확인:
- cutoffMillis 확인 여부:

## 5. 판정

- `success`
- `expected contract`
- `unexpected behavior`
- `needs follow-up`

## 6. 문제/경고

-
-
-

## 7. 해석

- exact-token revoke 문제인지:
- user-session cutoff 문제인지:
- allowlist/role issue 인지:
- 테스트 전제 혼동인지:

## 8. 다음 액션

1.
2.
3.
