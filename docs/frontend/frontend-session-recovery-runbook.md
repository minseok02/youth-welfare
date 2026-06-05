# Frontend Session Recovery Runbook

문서군 진입점: [frontend-qa-docs-index.md](./frontend-qa-docs-index.md)

## 목적

이 문서는 인증이 끊겼을 때 사용자가 다시 복구되는 흐름을 한 묶음으로 봅니다.

현재 범위는 아래입니다.

1. 로그인된 `/chat` 세션 만료
2. 보호 API `401 -> refresh 401`
3. `/reset-password` query/hash token 진입
4. invalid reset token 처리

즉 “세션이 끊겼을 때 로그인으로 복귀하는가” 와 “비밀번호 재설정 경로가 로그인과 섞이지 않는가” 를 함께 보는 runbook 입니다.

## 현재 기준선

아래 계약이 유지돼야 합니다.

- 세션 만료 시 `/login` 으로 이동
- `reason=expired` 안내가 노출
- 재로그인 후 원래 보호 경로(`/chat`)로 복귀
- reset-password는 query token이든 hash token이든 정상 처리
- invalid reset token은 로그인으로 튕기지 않고 reset-password 화면에 머물러야 함

## Playwright 기준선

관련 smoke:

- `로그인된 chat 세션이 만료되면 로그인으로 이동하고 재로그인 후 chat으로 복귀한다`
- `chat 보호 API가 401 후 refresh도 실패하면 로그인으로 이동하고 재로그인 후 chat으로 복귀한다`
- `reset-password query token 진입은 새 비밀번호 설정 후 새 비밀번호로 로그인할 수 있다`
- `reset-password hash token 딥링크 진입은 새 비밀번호 설정 후 새 비밀번호로 로그인할 수 있다`
- `reset-password invalid token 진입은 만료 안내를 보여주고 로그인으로 이동하지 않는다`

실행:

```bash
cd frontend
PLAYWRIGHT_GREP='로그인된 chat 세션이 만료되면 로그인으로 이동하고 재로그인 후 chat으로 복귀한다|chat 보호 API가 401 후 refresh도 실패하면 로그인으로 이동하고 재로그인 후 chat으로 복귀한다|reset-password query token 진입은 새 비밀번호 설정 후 새 비밀번호로 로그인할 수 있다|reset-password hash token 딥링크 진입은 새 비밀번호 설정 후 새 비밀번호로 로그인할 수 있다|reset-password invalid token 진입은 만료 안내를 보여주고 로그인으로 이동하지 않는다' npm run test:e2e
```

## 해석 기준

- 세션 만료 후 `/login` 복귀가 깨지면 auth recovery 회귀입니다.
- refresh 실패 후 무한 loop가 생기면 high-risk 회귀입니다.
- reset-password invalid token이 로그인으로 튕기면 reset deep-link 회귀입니다.
- reset-password query/hash가 다르게 동작하면 이메일 링크 진입 회귀로 봅니다.
