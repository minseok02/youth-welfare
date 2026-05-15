# Auth / Session Revoke 기록 템플릿

문서군 진입점: [auth-docs-index.md](./auth-docs-index.md)

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
- wrapper / command / endpoint:
- app base URL:

## 2. 전제 상태

- userKey:
- admin 여부:
- access token 종류:
  - `current`
  - `legacy(no iatm)`
- refresh cookie/token 상태:
- 관련 URL / query:

## 3. 결과 요약

- main response:
- old access reuse result:
- old refresh reuse result:
- relogin result:
- errorCode 요약:

## 4. 운영 증적

- server log line:
- redis key 확인:
- cutoffMillis 확인 여부:
- artifact / response file 위치:

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
- smoke baseline과 다른 점:

## 8. 다음 액션

1.
2.
3.
