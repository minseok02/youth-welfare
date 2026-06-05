# Frontend Account Lifecycle Runbook

문서군 진입점: [frontend-qa-docs-index.md](./frontend-qa-docs-index.md)

## 목적

이 문서는 계정 생성 이후 다시 로그인하고 계정을 유지하는 흐름을 한 묶음으로 봅니다.

현재 범위는 아래입니다.

1. 회원가입 완료 후 로그인 안내
2. reset-password query/hash token 진입
3. invalid reset token 처리
4. 마이페이지 비밀번호 변경 후 재로그인

즉 “계정 생성 -> 비밀번호 재설정 -> 비밀번호 변경 -> 재로그인 복귀” 를 하나의 account lifecycle 로 보는 runbook 입니다.

## 현재 기준선

아래 계약이 유지돼야 합니다.

- 회원가입 완료 후 로그인 페이지에서 안내가 보여야 함
- reset-password query/hash token 이 모두 정상 동작해야 함
- invalid reset token 은 로그인으로 튕기지 않아야 함
- 마이페이지 비밀번호 변경 후 `/login` 으로 이동하고, 재로그인 뒤 원래 account tab 으로 복귀해야 함

## Playwright 기준선

관련 smoke:

- `마이페이지 비밀번호 변경 후 로그인으로 이동하고 재로그인하면 account tab으로 복귀한다`
- `reset-password query token 진입은 새 비밀번호 설정 후 새 비밀번호로 로그인할 수 있다`
- `reset-password hash token 딥링크 진입은 새 비밀번호 설정 후 새 비밀번호로 로그인할 수 있다`
- `reset-password invalid token 진입은 만료 안내를 보여주고 로그인으로 이동하지 않는다`

실행:

```bash
cd frontend
PLAYWRIGHT_GREP='마이페이지 비밀번호 변경 후 로그인으로 이동하고 재로그인하면 account tab으로 복귀한다|reset-password query token 진입은 새 비밀번호 설정 후 새 비밀번호로 로그인할 수 있다|reset-password hash token 딥링크 진입은 새 비밀번호 설정 후 새 비밀번호로 로그인할 수 있다|reset-password invalid token 진입은 만료 안내를 보여주고 로그인으로 이동하지 않는다' npm run test:e2e
```

## 해석 기준

- 비밀번호 변경 후 account tab 복귀가 깨지면 account recovery 회귀입니다.
- reset-password query/hash가 다르게 동작하면 이메일 링크 진입 회귀입니다.
- invalid token이 로그인으로 튕기면 reset error handling 회귀입니다.
